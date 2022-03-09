package com.github.echisan.flink.connector.jdbc.internal.converter;

import org.apache.flink.connector.jdbc.internal.converter.AbstractJdbcRowConverter;
import org.apache.flink.table.types.logical.RowType;

public class TDengineRowConverter extends AbstractJdbcRowConverter {
    private static final long serialVersionUID = 1L;

    public TDengineRowConverter(RowType rowType) {
        super(rowType);
    }

    public String converterName() {
        return "TDengine";
    }
}
