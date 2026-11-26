package com.github.echisan.flink.connector.jdbc.dialect;

import com.github.echisan.flink.connector.jdbc.internal.converter.IoTDBRowConverter;
import org.apache.flink.connector.jdbc.dialect.JdbcDialect;
import org.apache.flink.connector.jdbc.internal.converter.JdbcRowConverter;
import org.apache.flink.table.types.logical.RowType;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public final class IoTDBDialect implements JdbcDialect {
    private static final String JDBC_PREFIX = "jdbc:iotdb:";

    @Override
    public String dialectName() {
        return "IoTDB Table";
    }

    @Override
    public boolean canHandle(String url) {
        if (url == null || !url.regionMatches(true, 0, JDBC_PREFIX, 0, JDBC_PREFIX.length())) {
            return false;
        }

        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return false;
        }
        for (String parameter : url.substring(queryStart + 1).split("&")) {
            int equals = parameter.indexOf('=');
            if (equals > 0
                    && "sql_dialect".equalsIgnoreCase(parameter.substring(0, equals))
                    && "table".equalsIgnoreCase(parameter.substring(equals + 1))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public JdbcRowConverter getRowConverter(RowType rowType) {
        return new IoTDBRowConverter(rowType);
    }

    @Override
    public String getLimitClause(long limit) {
        return "LIMIT " + limit;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public Optional<String> getUpsertStatement(
            String tableName, String[] fieldNames, String[] uniqueKeyFields) {
        return Optional.empty();
    }

    @Override
    public String getInsertIntoStatement(String tableName, String[] fieldNames) {
        String columns =
                Arrays.stream(fieldNames)
                        .map(this::quoteIdentifier)
                        .collect(Collectors.joining(", "));
        String placeholders =
                Arrays.stream(fieldNames).map(field -> ":" + field).collect(Collectors.joining(", "));
        return "INSERT INTO "
                + quoteTableName(tableName)
                + " ("
                + columns
                + ") VALUES ("
                + placeholders
                + ")";
    }

    @Override
    public String getRowExistsStatement(String tableName, String[] conditionFields) {
        return "SELECT 1 FROM "
                + quoteTableName(tableName)
                + " WHERE "
                + conditionClause(conditionFields);
    }

    @Override
    public String getUpdateStatement(
            String tableName, String[] fieldNames, String[] conditionFields) {
        String assignments =
                Arrays.stream(fieldNames)
                        .map(field -> quoteIdentifier(field) + " = :" + field)
                        .collect(Collectors.joining(", "));
        return "UPDATE "
                + quoteTableName(tableName)
                + " SET "
                + assignments
                + " WHERE "
                + conditionClause(conditionFields);
    }

    @Override
    public String getDeleteStatement(String tableName, String[] conditionFields) {
        return "DELETE FROM "
                + quoteTableName(tableName)
                + " WHERE "
                + conditionClause(conditionFields);
    }

    @Override
    public String getSelectFromStatement(
            String tableName, String[] selectFields, String[] conditionFields) {
        String selectedColumns =
                Arrays.stream(selectFields)
                        .map(this::quoteIdentifier)
                        .collect(Collectors.joining(", "));
        String conditions =
                Arrays.stream(conditionFields)
                        .map(field -> quoteIdentifier(field) + " = :" + field)
                        .collect(Collectors.joining(" AND "));
        return "SELECT "
                + selectedColumns
                + " FROM "
                + quoteTableName(tableName)
                + (conditions.isEmpty() ? "" : " WHERE " + conditions);
    }

    private String quoteTableName(String tableName) {
        return Arrays.stream(tableName.split("\\.", -1))
                .map(segment -> {
                    if (segment.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Table name segments must not be empty: " + tableName);
                    }
                    return quoteIdentifier(segment);
                })
                .collect(Collectors.joining("."));
    }

    private String conditionClause(String[] conditionFields) {
        if (conditionFields.length == 0) {
            throw new IllegalArgumentException("At least one condition field is required");
        }
        return Arrays.stream(conditionFields)
                .map(field -> quoteIdentifier(field) + " = :" + field)
                .collect(Collectors.joining(" AND "));
    }
}
