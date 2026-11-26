# Flink JDBC connector

## IoTDB Table dialect

This connector supports Apache IoTDB Table connections using the official JDBC driver.

## IoTDB connection

Use the JDBC Table API connector and explicitly select IoTDB's relational Table dialect in the URL. This implementation does not claim IoTDB Tree-model compatibility.

```sql
CREATE TABLE readings (
  time TIMESTAMP(3),
  device STRING,
  temperature DOUBLE,
  note STRING
) WITH (
  'connector' = 'jdbc',
  'url' = 'jdbc:iotdb://127.0.0.1:6667/?sql_dialect=table',
  'table-name' = 'telemetry.readings',
  'driver' = 'org.apache.iotdb.jdbc.IoTDBDriver',
  'username' = '${SMOKE_USERNAME}',
  'password' = '${SMOKE_PASSWORD}'
)
```

Supply the username and password placeholders from your deployment before submitting the SQL. The IoTDB database and target table must already exist. A compatible table can be created with:

```sql
CREATE DATABASE telemetry;
CREATE TABLE telemetry.readings (
  time TIMESTAMP TIME,
  device STRING TAG,
  temperature DOUBLE FIELD,
  note STRING FIELD
);
```

Qualified `database.table` names are supported. Identifiers are quoted using double quotes, query limits use `LIMIT`, and Flink's standard JDBC batch insert path is used. The driver must be able to resolve `sql_dialect=table` from the JDBC URL.

The converter supports the common IoTDB scalar types exposed by Flink's JDBC connector: booleans, integer and floating-point values, strings, binary values, dates, and timestamps. Flink `TINYINT` and `SMALLINT` are sent through JDBC `setInt`, because IoTDB JDBC does not implement `setByte` or `setShort`. `DECIMAL` and `TIME` are rejected explicitly: IoTDB has no corresponding Table-model column types. Complex Flink values remain unsupported by the upstream converter.

## Build and tests

```powershell
mvn test
mvn package
```

`IoTDBFlinkSmokeTest` exercises both write and read through Flink's actual `connector='jdbc'` entry point. It is opt-in and refuses non-loopback URLs by default. Set `IOTDB_FLINK_SMOKE_ALLOW_NON_LOOPBACK=true` only when intentionally targeting a non-loopback endpoint. To run it, provide credentials through environment variables:

```powershell
$env:IOTDB_FLINK_SMOKE_URL = 'jdbc:iotdb://127.0.0.1:6667/?sql_dialect=table'
$credential = Get-Credential
$env:IOTDB_FLINK_SMOKE_USERNAME = $credential.UserName
$env:IOTDB_FLINK_SMOKE_PASSWORD = $credential.GetNetworkCredential().Password
mvn -Dtest=IoTDBFlinkSmokeTest test
```

On JDK 9 and later, Maven enables the `java.lang` module access required by Flink 1.13's closure cleaner for the test JVM.

The smoke test creates a uniquely named database, writes and reads one row through Flink, and drops only that database in cleanup. With no URL set it is skipped; an invalid endpoint or missing credentials on a configured test fails validation.
