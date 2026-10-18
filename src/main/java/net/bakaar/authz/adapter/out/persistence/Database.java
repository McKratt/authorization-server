package net.bakaar.authz.adapter.out.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.helidon.config.Config;
import org.flywaydb.core.Flyway;

/**
 * Creates the connection pool and applies Flyway migrations from {@code db/migration}.
 */
public final class Database {

    private Database() {
    }

    /**
     * Builds the pool without opening a connection, so the server can start while the database is unreachable
     * (required by the AOT training run in the Docker build).
     */
    public static HikariDataSource dataSource(Config db) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(db.get("url").asString().get());
        hikari.setUsername(db.get("username").asString().get());
        hikari.setPassword(db.get("password").asString().get());
        hikari.setMaximumPoolSize(db.get("pool.max-size").asInt().orElse(10));
        hikari.setInitializationFailTimeout(-1);
        hikari.setPoolName("authz");
        return new HikariDataSource(hikari);
    }

    public static void migrate(HikariDataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate();
    }
}
