package com.github.echisan.flink.connector.jdbc.internal.converter;

import org.apache.flink.connector.jdbc.internal.converter.AbstractJdbcRowConverter;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.RowType;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class IoTDBRowConverter extends AbstractJdbcRowConverter {
    private static final long serialVersionUID = 1L;

    public IoTDBRowConverter(RowType rowType) {
        super(rowType);
    }

    @Override
    public String converterName() {
        return "IoTDB";
    }

    @Override
    public RowData toInternal(ResultSet resultSet) throws SQLException {
        GenericRowData row = new GenericRowData(rowType.getFieldCount());
        for (int pos = 0; pos < rowType.getFieldCount(); pos++) {
            LogicalTypeRoot typeRoot = fieldTypes[pos].getTypeRoot();
            Object field;
            if (isTimestamp(typeRoot)) {
                field = resultSet.getTimestamp(pos + 1);
                if (resultSet.wasNull()) {
                    field = null;
                }
            } else if (typeRoot == LogicalTypeRoot.CHAR || typeRoot == LogicalTypeRoot.VARCHAR) {
                field = resultSet.getString(pos + 1);
            } else if (typeRoot == LogicalTypeRoot.BINARY
                    || typeRoot == LogicalTypeRoot.VARBINARY) {
                field = resultSet.getBytes(pos + 1);
            } else {
                field = resultSet.getObject(pos + 1);
            }
            row.setField(pos, toInternalConverters[pos].deserialize(field));
        }
        return row;
    }

    @Override
    protected JdbcSerializationConverter createExternalConverter(LogicalType type) {
        switch (type.getTypeRoot()) {
            case TINYINT:
                return (row, index, statement) -> statement.setInt(index, row.getByte(index));
            case SMALLINT:
                return (row, index, statement) -> statement.setInt(index, row.getShort(index));
            case DECIMAL:
                throw new UnsupportedOperationException(
                        "IoTDB does not support DECIMAL columns; use an IoTDB numeric type");
            case TIME_WITHOUT_TIME_ZONE:
                throw new UnsupportedOperationException(
                        "IoTDB has no TIME column type; use TIMESTAMP for time values");
            default:
                return super.createExternalConverter(type);
        }
    }

    private static boolean isTimestamp(LogicalTypeRoot typeRoot) {
        return typeRoot == LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE
                || typeRoot == LogicalTypeRoot.TIMESTAMP_WITH_TIME_ZONE;
    }
}
