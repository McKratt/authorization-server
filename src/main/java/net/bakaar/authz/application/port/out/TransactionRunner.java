package net.bakaar.authz.application.port.out;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The transaction boundary of the application services: commits when the work returns, rolls back when it throws.
 * Nested calls join the outer transaction.
 */
public interface TransactionRunner {

    <T extends @Nullable Object> T inTransaction(Supplier<T> work);

    default void run(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
