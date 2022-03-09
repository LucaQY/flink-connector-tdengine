//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.apache.flink.connector.jdbc.dialect;

import com.github.echisan.flink.connector.jdbc.dialect.TDengineDialect;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public final class JdbcDialects {
    private static final List<JdbcDialect> DIALECTS = Arrays.asList(
            new DerbyDialect(),
            new MySQLDialect(),
            new PostgresDialect(),
            new TDengineDialect()
    );

    public JdbcDialects() {
    }

    public static Optional<JdbcDialect> get(String url) {
        Iterator var1 = DIALECTS.iterator();

        JdbcDialect dialect;
        do {
            if (!var1.hasNext()) {
                return Optional.empty();
            }

            dialect = (JdbcDialect) var1.next();
        } while (!dialect.canHandle(url));

        return Optional.of(dialect);
    }
}
