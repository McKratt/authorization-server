package net.bakaar.authz.adapter.out.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import net.bakaar.authz.application.port.out.TransactionRunner;
import org.jspecify.annotations.Nullable;

/**
 * Binds one connection to the calling (virtual) thread for the duration of a transaction.
 * Stores obtain it through {@link #current()}.
 */
@RequiredArgsConstructor
class JdbcTransactionRunner implements TransactionRunner {

    private final DataSource dataSource;
    private final ThreadLocal<Connection> current = new ThreadLocal<>();

    @Override
    public <T extends @Nullable Object> T inTransaction(Supplier<T> work) {
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
