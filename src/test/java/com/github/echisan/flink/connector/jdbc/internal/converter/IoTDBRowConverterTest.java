package com.github.echisan.flink.connector.jdbc.internal.converter;

import org.apache.flink.connector.jdbc.internal.converter.AbstractJdbcRowConverter;
import org.apache.flink.connector.jdbc.statement.FieldNamedPreparedStatement;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.types.logical.BigIntType;
import org.apache.flink.table.types.logical.IntType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.logical.SmallIntType;
import org.apache.flink.table.types.logical.TimestampType;
import org.apache.flink.table.types.logical.VarCharType;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class IoTDBRowConverterTest {
    @Test
    public void readsTimestampThroughTheIoTDBTimestampGetter() throws Exception {
        RowType rowType =
                RowType.of(
                        new org.apache.flink.table.types.logical.LogicalType[] {
                            new TimestampType(false, 3), new IntType(), new VarCharType(32)
                        },
                        new String[] {"time", "count", "device"});
        IoTDBRowConverter converter = new IoTDBRowConverter(rowType);
        Timestamp timestamp = Timestamp.valueOf("2026-11-19 10:11:12.123");
        ResultSet resultSet = resultSet(timestamp, 7, "sensor-a");

        RowData row = converter.toInternal(resultSet);

        assertEquals(TimestampData.fromTimestamp(timestamp), row.getTimestamp(0, 3));
        assertEquals(7, row.getInt(1));
        assertEquals("sensor-a", row.getString(2).toString());
    }

    @Test
    public void writesSmallIntegerTypesUsingIoTDBSupportedIntSetter() throws Exception {
        RowType rowType =
                RowType.of(
                        new org.apache.flink.table.types.logical.LogicalType[] {
                            new org.apache.flink.table.types.logical.TinyIntType(),
                            new SmallIntType()
                        },
                        new String[] {"small_value", "medium_value"});
        IoTDBRowConverter converter = new IoTDBRowConverter(rowType);
        List<String> calls = new ArrayList<>();
        FieldNamedPreparedStatement statement = recordingStatement(calls);

        converter.toExternal(GenericRowData.of((byte) 7, (short) 300), statement);

        assertEquals(Arrays.asList("0:7", "1:300"), calls);
    }

    @Test
    public void preservesNullTimestampValues() throws Exception {
        RowType rowType =
                RowType.of(
                        new org.apache.flink.table.types.logical.LogicalType[] {
                            new TimestampType(true, 3)
                        },
                        new String[] {"time"});
        IoTDBRowConverter converter = new IoTDBRowConverter(rowType);

        GenericRowData row = (GenericRowData) converter.toInternal(resultSet((Object) null));
        assertNull(row.getField(0));
    }

    @Test
    public void preservesExistingJdbcNumericTimestampFormat() throws Exception {
        RowType rowType =
                RowType.of(
                        new org.apache.flink.table.types.logical.LogicalType[] {
                            new BigIntType(), new IntType()
                        },
                        new String[] {"ts", "count"});
        ExistingJdbcConverter converter = new ExistingJdbcConverter(rowType);

        RowData row = converter.toInternal(existingJdbcResultSet("TIMESTAMP", 123456L, null));

        assertEquals(123456L, row.getLong(0));
        assertEquals(7, row.getInt(1));
    }

    @Test
    public void preservesExistingJdbcUtcTimestampFormat() throws Exception {
        RowType rowType =
                RowType.of(
                        new org.apache.flink.table.types.logical.LogicalType[] {
                            new TimestampType(false, 3), new IntType()
                        },
                        new String[] {"ts", "count"});
        ExistingJdbcConverter converter = new ExistingJdbcConverter(rowType);
        Timestamp timestamp = Timestamp.valueOf("2026-11-19 10:11:12.123");

        RowData row = converter.toInternal(existingJdbcResultSet("UTC", 0L, timestamp));

        assertEquals(TimestampData.fromTimestamp(timestamp), row.getTimestamp(0, 3));
        assertEquals(7, row.getInt(1));
    }

    private static ResultSet resultSet(Object... values) {
        final boolean[] wasNull = {false};
        InvocationHandler handler =
                (proxy, method, args) -> {
                    int index = args == null || args.length == 0 ? -1 : (Integer) args[0] - 1;
                    Object value = index >= 0 && index < values.length ? values[index] : null;
                    if ("getTimestamp".equals(method.getName())) {
                        wasNull[0] = value == null;
                        return (Timestamp) value;
                    }
                    if ("getString".equals(method.getName())) {
                        wasNull[0] = value == null;
                        return (String) value;
                    }
                    if ("getBytes".equals(method.getName())) {
                        wasNull[0] = value == null;
                        return (byte[]) value;
                    }
                    if ("getObject".equals(method.getName())) {
                        wasNull[0] = value == null;
                        return value;
                    }
                    if ("wasNull".equals(method.getName())) {
                        return wasNull[0];
                    }
                    return defaultValue(method.getReturnType());
                };
        return (ResultSet)
                Proxy.newProxyInstance(
                        ResultSet.class.getClassLoader(), new Class<?>[] {ResultSet.class}, handler);
    }

    private static ResultSet existingJdbcResultSet(
            String timestampFormat, long timestampMillis, Timestamp timestamp) {
        Connection connection =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) ->
                                        "getClientInfo".equals(method.getName())
                                                ? timestampFormat
                                                : defaultValue(method.getReturnType()));
        Statement statement =
                (Statement)
                        Proxy.newProxyInstance(
                                Statement.class.getClassLoader(),
                                new Class<?>[] {Statement.class},
                                (proxy, method, args) ->
                                        "getConnection".equals(method.getName())
                                                ? connection
                                                : defaultValue(method.getReturnType()));
        return (ResultSet)
                Proxy.newProxyInstance(
                        ResultSet.class.getClassLoader(),
                        new Class<?>[] {ResultSet.class},
                        (proxy, method, args) -> {
                            if ("getStatement".equals(method.getName())) {
                                return statement;
                            }
                            if ("getLong".equals(method.getName())) {
                                return timestampMillis;
                            }
                            if ("getTimestamp".equals(method.getName())) {
                                return timestamp;
                            }
                            if ("getObject".equals(method.getName())) {
                                return 7;
                            }
                            return defaultValue(method.getReturnType());
                        });
    }

    private static FieldNamedPreparedStatement recordingStatement(List<String> calls) {
        InvocationHandler handler =
                (proxy, method, args) -> {
                    if ("setInt".equals(method.getName())) {
                        calls.add(args[0] + ":" + args[1]);
                    }
                    return defaultValue(method.getReturnType());
                };
        return (FieldNamedPreparedStatement)
                Proxy.newProxyInstance(
                        FieldNamedPreparedStatement.class.getClassLoader(),
                        new Class<?>[] {FieldNamedPreparedStatement.class},
                        handler);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }

    private static final class ExistingJdbcConverter extends AbstractJdbcRowConverter {
        private ExistingJdbcConverter(RowType rowType) {
            super(rowType);
        }

        @Override
        public String converterName() {
            return "JDBC";
        }
    }
}
