package net.bakaar.authz.adapter.out.persistence;

import com.zaxxer.hikari.HikariDataSource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import java.util.Map;
import net.bakaar.authz.support.TestDatabase;

/** One migrated database with fixtures and the stores on top, shared by a test class. */
final class StoreFixture implements AutoCloseable {

    final TestDatabase database = new TestDatabase();
    final HikariDataSource dataSource;
    final JdbcTransactions transactions;
    final JdbcUserStore users;
    final JdbcApplicationStore applications;
    final JdbcGrantStore grants;

    StoreFixture() {
        Config db = Config.just(ConfigSources.create(Map.of(
                "url", database.jdbcUrl(),
                "username", database.username(),
                "password", database.password())));
        dataSource = Database.dataSource(db);
        Database.migrate(dataSource);
        database.insertFixtures();
        transactions = new JdbcTransactions(dataSource);
        users = new JdbcUserStore(transactions);
        applications = new JdbcApplicationStore(transactions);
        grants = new JdbcGrantStore(transactions);
    }

    @Override
    public void close() {
        dataSource.close();
        database.close();
    }
}
