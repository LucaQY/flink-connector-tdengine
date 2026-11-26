package com.github.echisan.flink.connector.jdbc;

import com.github.echisan.flink.connector.jdbc.dialect.IoTDBDialect;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.apache.iotdb.jdbc.IoTDBDriver;
import org.junit.Assume;
import org.junit.Test;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Opt-in read/write check through Flink's JDBC Table connector. */
public class IoTDBFlinkSmokeTest {
    private static final String JDBC_URL = "IOTDB_FLINK_SMOKE_URL";
    private static final String USERNAME = "IOTDB_FLINK_SMOKE_USERNAME";
    private static final String PASSWORD = "IOTDB_FLINK_SMOKE_PASSWORD";
    private static final String ALLOW_NON_LOOPBACK = "IOTDB_FLINK_SMOKE_ALLOW_NON_LOOPBACK";

    @Test
    public void requiresExplicitOptInForNonLoopbackSmokeEndpoints() {
        String remoteUrl = "jdbc:iotdb://192.0.2.10:6667/?sql_dialect=table";

        assertFalse(isAllowedEndpoint(remoteUrl, false));
        assertTrue(isAllowedEndpoint(remoteUrl, true));
        assertTrue(isAllowedEndpoint("jdbc:iotdb://localhost:6667/?sql_dialect=table", false));
        assertTrue(isAllowedEndpoint("jdbc:iotdb://127.0.0.1:6667/?sql_dialect=table", false));
        assertTrue(isAllowedEndpoint("jdbc:iotdb://[::1]:6667/?sql_dialect=table", false));
        assertFalse(isAllowedEndpoint("jdbc:iotdb://127.example.org/?sql_dialect=table", false));
        assertFalse(isAllowedEndpoint("jdbc:other://localhost/?sql_dialect=table", true));
        assertFalse(isAllowedEndpoint("jdbc:iotdb:///?sql_dialect=table", true));
    }

    @Test
    public void writesAndReadsThroughFlinkJdbcConnector() throws Exception {
        String url = System.getenv(JDBC_URL);
        String username = System.getenv(USERNAME);
        String password = System.getenv(PASSWORD);
        Assume.assumeTrue("Set IOTDB_FLINK_SMOKE_URL to opt in", url != null && !url.isEmpty());
        boolean allowNonLoopback = Boolean.parseBoolean(System.getenv(ALLOW_NON_LOOPBACK));
        assertTrue(
                "Use an IoTDB Table URL; non-loopback endpoints also require explicit opt-in",
                isAllowedEndpoint(url, allowNonLoopback));
        assertTrue(
                "Set both IoTDB smoke credentials when the URL is configured",
                username != null && !username.isEmpty() && password != null);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String database = "flink_smoke_" + suffix;
        String table = "readings_" + suffix;
        String qualifiedTable = database + "." + table;
        boolean databaseCreated = false;
        IoTDBDriver driver = new IoTDBDriver();
        DriverManager.registerDriver(driver);
        try {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + database);
                databaseCreated = true;
                statement.execute(
                        "CREATE TABLE "
                                + qualifiedTable
                                + " (time TIMESTAMP TIME, device STRING TAG, reading DOUBLE FIELD,"
                                + " note STRING FIELD)");
            }

            EnvironmentSettings settings =
                    EnvironmentSettings.newInstance().inBatchMode().useBlinkPlanner().build();
            TableEnvironment tableEnvironment = TableEnvironment.create(settings);
            createFlinkTable(tableEnvironment, "iotdb_sink", qualifiedTable, url, username, password);
            createFlinkTable(
                    tableEnvironment, "iotdb_source", qualifiedTable, url, username, password);

            LocalDateTime expectedTime = LocalDateTime.now().withNano(0);
            String expectedNote = "Flink's JDBC smoke";
            String timestampLiteral = expectedTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String insertSql =
                    "INSERT INTO iotdb_sink VALUES (TIMESTAMP '"
                            + timestampLiteral
                            + "', 'sensor-smoke', 12.5, 'Flink''s JDBC smoke')";
            tableEnvironment.executeSql(insertSql).await();

            TableResult query =
                    tableEnvironment.executeSql(
                            "SELECT `time`, device, reading, note FROM iotdb_source");
            try (CloseableIterator<Row> rows = query.collect()) {
                assertTrue(rows.hasNext());
                Row row = rows.next();
                assertEquals(expectedTime, row.getField(0));
                assertEquals("sensor-smoke", row.getField(1));
                assertEquals(12.5D, (Double) row.getField(2), 0.0001D);
                assertEquals(expectedNote, row.getField(3));
                assertFalse(rows.hasNext());
            }
        } finally {
            try {
                if (databaseCreated) {
                    try (Connection connection = DriverManager.getConnection(url, username, password);
                            Statement statement = connection.createStatement()) {
                        statement.execute("DROP DATABASE " + database);
                    }
                }
            } finally {
                DriverManager.deregisterDriver(driver);
            }
        }
    }

    private static void createFlinkTable(
            TableEnvironment environment,
            String flinkTable,
            String iotdbTable,
            String url,
            String username,
            String password) {
        environment.executeSql(
                "CREATE TABLE "
                        + flinkTable
                        + " (`time` TIMESTAMP(3), device STRING, reading DOUBLE, note STRING) WITH ("
                        + "'connector' = 'jdbc',"
                        + "'url' = "
                        + literal(url)
                        + ", 'table-name' = "
                        + literal(iotdbTable)
                        + ", 'driver' = 'org.apache.iotdb.jdbc.IoTDBDriver',"
                        + "'username' = "
                        + literal(username)
                        + ", 'password' = "
                        + literal(password)
                        + ")");
    }

    private static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static boolean isAllowedEndpoint(String jdbcUrl, boolean allowNonLoopback) {
        try {
            if (!new IoTDBDialect().canHandle(jdbcUrl)) {
                return false;
            }
            URI endpoint = URI.create(jdbcUrl.substring(5));
            String host = endpoint.getHost();
            if (host == null || endpoint.getUserInfo() != null) {
                return false;
            }
            boolean loopback =
                    "localhost".equalsIgnoreCase(host)
                            || "[::1]".equals(host)
                            || host.matches("127(?:\\.(?:0|[1-9][0-9]?|1[0-9]{2}|2[0-4][0-9]|25[0-5])){3}");
            return loopback || allowNonLoopback;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
