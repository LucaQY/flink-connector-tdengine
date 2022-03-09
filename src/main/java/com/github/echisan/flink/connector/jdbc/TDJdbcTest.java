package com.github.echisan.flink.connector.jdbc;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;

public class TDJdbcTest {

    public static void main(String[] args) {
        EnvironmentSettings settings = EnvironmentSettings
                .newInstance()
                .inBatchMode()
                .useBlinkPlanner()
                .build();

        TableEnvironment tableEnv = TableEnvironment.create(settings);
//'connector' = 'jdbc',
//   'url' = 'jdbc:mysql://localhost:3306/mydatabase',
//        jdbc:TAOS-RS://taosdemo.com:6041/test?user=root&password=taosdata

//   'table-name' = 'users'
        tableEnv.executeSql("create table mytest(\n" +
                "ts bigint,id int, name string) with(\n" +
                "'connector' = 'jdbc',\n" +
                "'url' = 'jdbc:TAOS-RS://localhost:6041/mytest?user=root&password=taosdata&timestampFormat=TIMESTAMP',\n" +
                "'table-name' = 'mytest',\n" +
                "'driver' = 'com.taosdata.jdbc.rs.RestfulDriver'" +
                ")");

        tableEnv.executeSql(
                String.format("insert into mytest select %s,2,'ass'", System.currentTimeMillis())
        ).print();

        tableEnv.executeSql("select * from mytest")
                .print();


    }
}
