package net.bakaar.authz.adapter.out.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import net.bakaar.authz.application.port.out.Transactions;

/**
 * Binds one connection to the calling (virtual) thread for the duration of a transaction.
 * Stores obtain it through {@link #current()}.
 */
public class JdbcTransactions implements Transactions {

    private final DataSource dataSource;
    private final ThreadLocal<Connection> current = new ThreadLocal<>();

    public JdbcTransactions(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        if (current.get() != null) {
            return work.get();
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            current.set(connection);
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (Throwable t) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    t.addSuppressed(rollbackFailure);
                }
                throw t;
            } finally {
                current.remove();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Transaction failed", e);
        }
    }

    public Connection current() {
        Connection connection = current.get();
        if (connection == null) {
            throw new IllegalStateException("No transaction in progress");
        }
        return connection;
    }
}
