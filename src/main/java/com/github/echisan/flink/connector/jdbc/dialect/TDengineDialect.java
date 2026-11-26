package com.github.echisan.flink.connector.jdbc.dialect;

import com.github.echisan.flink.connector.jdbc.internal.converter.TDengineRowConverter;
import org.apache.flink.connector.jdbc.dialect.JdbcDialect;
import org.apache.flink.connector.jdbc.internal.converter.JdbcRowConverter;
import org.apache.flink.table.types.logical.RowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class TDengineDialect implements JdbcDialect {
    private static final Logger LOG = LoggerFactory.getLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    @Override
    public String dialectName() {
        return "TDengine";
    }

    @Override
    public boolean canHandle(String url) {
        return url.startsWith("jdbc:TAOS:")
                || url.startsWith("jdbc:TAOS-RS:");
    }

    @Override
    public JdbcRowConverter getRowConverter(RowType rowType) {
        return new TDengineRowConverter(rowType);
    }

    @Override
    public String getLimitClause(long limit) {
        return "limit " + limit;
    }


    public String getInsertIntoStatement(String tableName, String[] fieldNames) {
        LOG.info("tableName:{}, fieldNames:{}", tableName, fieldNames);
        return JdbcDialect.super.getInsertIntoStatement(tableName, fieldNames);
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "'" + identifier + "'";
    }

    @Override
    public String getSelectFromStatement(String tableName, String[] selectFields, String[] conditionFields) {
        String selectExpressions =
                String.join(", ", selectFields);
        String fieldExpressions =
                Arrays.stream(conditionFields)
                        .map(f -> format("%s = :%s", quoteIdentifier(f), f))
                        .collect(Collectors.joining(" AND "));
        return "SELECT "
                + selectExpressions
                + " FROM "
                + quoteIdentifier(tableName)
                + (conditionFields.length > 0 ? " WHERE " + fieldExpressions : "");
    }
}

