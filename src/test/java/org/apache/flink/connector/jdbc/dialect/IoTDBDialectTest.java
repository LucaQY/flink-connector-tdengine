package org.apache.flink.connector.jdbc.dialect;

import com.github.echisan.flink.connector.jdbc.dialect.IoTDBDialect;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IoTDBDialectTest {
    private final IoTDBDialect dialect = new IoTDBDialect();

    @Test
    public void onlyClaimsIoTDBTableDialectUrls() {
        assertTrue(dialect.canHandle("jdbc:iotdb://127.0.0.1:6667/?sql_dialect=table"));
        assertTrue(
                JdbcDialects.get("jdbc:iotdb://127.0.0.1:6667/?sql_dialect=table").get()
                        instanceof IoTDBDialect);
        assertTrue(dialect.canHandle("JDBC:IOTDB://localhost:6667/?user=root&sql_dialect=TABLE"));
        assertFalse(dialect.canHandle("jdbc:iotdb://127.0.0.1:6667/"));
        assertFalse(dialect.canHandle("jdbc:iotdb://127.0.0.1:6667/?sql_dialect=tree"));
        assertFalse(dialect.canHandle("jdbc:TAOS-RS://127.0.0.1:6041/test"));
    }

    @Test
    public void buildsTableModelStatementsWithQuotedQualifiedNames() {
        assertEquals("\"metrics.readings\"", dialect.quoteIdentifier("metrics.readings"));
        assertEquals("\"select\"", dialect.quoteIdentifier("select"));
        assertEquals("\"sensor.v1\"", dialect.quoteIdentifier("sensor.v1"));
        assertEquals(
                "INSERT INTO \"metrics\".\"readings\" (\"time\", \"device\", \"value\")"
                        + " VALUES (:time, :device, :value)",
                dialect.getInsertIntoStatement(
                        "metrics.readings", new String[] {"time", "device", "value"}));
        assertEquals(
                "SELECT \"time\", \"value\" FROM \"metrics\".\"readings\""
                        + " WHERE \"device\" = :device",
                dialect.getSelectFromStatement(
                        "metrics.readings",
                        new String[] {"time", "value"},
                        new String[] {"device"}));
        assertEquals(
                "UPDATE \"metrics\".\"readings\" SET \"value\" = :value"
                        + " WHERE \"time\" = :time",
                dialect.getUpdateStatement(
                        "metrics.readings", new String[] {"value"}, new String[] {"time"}));
        assertEquals(
                "DELETE FROM \"metrics\".\"readings\" WHERE \"time\" = :time",
                dialect.getDeleteStatement("metrics.readings", new String[] {"time"}));
        assertEquals("LIMIT 12", dialect.getLimitClause(12));
    }
}
