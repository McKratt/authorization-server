package net.bakaar.authz.adapter.out.persistence;

import com.zaxxer.hikari.HikariDataSource;
import io.helidon.config.Config;
import net.bakaar.authz.application.port.out.ApplicationStore;
import net.bakaar.authz.application.port.out.GrantStore;
import net.bakaar.authz.application.port.out.TransactionRunner;
import net.bakaar.authz.application.port.out.UserRolesFinder;
import net.bakaar.authz.application.port.out.UserStore;

/**
 * Creates the connection pool, applies the Flyway migrations unless {@code db.migrate=false}, and hands out the
 * persistence adapters typed as outgoing ports. Closing it closes the pool.
 */
public final class AuthorizationPersistenceAdapterConfiguration implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final JdbcTransactionRunner transactionRunner;
    private final UserPersistenceAdapter userStore;
    private final ApplicationPersistenceAdapter applicationStore;
    private final GrantPersistenceAdapter grantStore;
    private final UserRolesPersistenceAdapter userRolesFinder;

    /**
     * @param db the {@code db} config node: {@code url}, {@code username}, {@code password}, {@code pool.max-size},
     *           {@code migrate}
     */
    public AuthorizationPersistenceAdapterConfiguration(Config db) {
        dataSource = Database.dataSource(db);
        try {
            if (db.get("migrate").asBoolean().orElse(true)) {
                Database.migrate(dataSource);
            }
        } catch (RuntimeException e) {
            dataSource.close();
            throw e;
        }
        transactionRunner = new JdbcTransactionRunner(dataSource);
        userStore = new UserPersistenceAdapter(transactionRunner);
        applicationStore = new ApplicationPersistenceAdapter(transactionRunner);
        grantStore = new GrantPersistenceAdapter(transactionRunner);
        userRolesFinder = new UserRolesPersistenceAdapter(dataSource);
    }

    public TransactionRunner transactionRunner() {
        return transactionRunner;
    }

    public UserStore userStore() {
        return userStore;
    }

    public ApplicationStore applicationStore() {
        return applicationStore;
    }

    public GrantStore grantStore() {
        return grantStore;
    }

    public UserRolesFinder userRolesFinder() {
        return userRolesFinder;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
