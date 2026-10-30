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
    final JdbcTransactionRunner transactions;
    final UserPersistenceAdapter users;
    final ApplicationPersistenceAdapter applications;
    final GrantPersistenceAdapter grants;

    StoreFixture() {
        Config db = Config.just(ConfigSources.create(Map.of(
                "url", database.jdbcUrl(),
                "username", database.username(),
                "password", database.password())));
        dataSource = Database.dataSource(db);
        Database.migrate(dataSource);
        database.insertFixtures();
        transactions = new JdbcTransactionRunner(dataSource);
        users = new UserPersistenceAdapter(transactions);
        applications = new ApplicationPersistenceAdapter(transactions);
        grants = new GrantPersistenceAdapter(transactions);
    }

    @Override
    public void close() {
        dataSource.close();
        database.close();
    }
}
