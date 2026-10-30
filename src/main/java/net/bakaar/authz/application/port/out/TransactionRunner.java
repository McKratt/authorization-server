package net.bakaar.authz.application.port.out;

import java.util.function.Supplier;

/** Runs several store calls atomically; nested calls join the outer transaction. */
public interface Transactions {

    <T> T inTransaction(Supplier<T> work);

    default void run(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
