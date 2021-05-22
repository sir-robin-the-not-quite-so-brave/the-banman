package cbm.server.db;

import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.StoreTransaction;
import jetbrains.exodus.entitystore.StoreTransactionalComputable;
import jetbrains.exodus.entitystore.StoreTransactionalExecutable;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

class CustomTypesPersistentEntityStore extends PersistentEntityStoreWrapper {

    private final BiConsumer<PersistentEntityStore, StoreTransaction> registrar;

    public CustomTypesPersistentEntityStore(PersistentEntityStore base,
                                            BiConsumer<PersistentEntityStore, StoreTransaction> registrar) {

        super(base);
        this.registrar = registrar;
    }

    @Override
    public void executeInTransaction(@NotNull StoreTransactionalExecutable executable) {
        super.executeInTransaction(txn -> {
            registrar.accept(base, txn);
            executable.execute(txn);
        });
    }

    @Override
    public void executeInExclusiveTransaction(@NotNull StoreTransactionalExecutable executable) {
        super.executeInExclusiveTransaction(txn -> {
            registrar.accept(base, txn);
            executable.execute(txn);
        });
    }

    @Override
    public void executeInReadonlyTransaction(@NotNull StoreTransactionalExecutable executable) {
        super.executeInReadonlyTransaction(txn -> {
            registrar.accept(base, txn);
            executable.execute(txn);
        });
    }

    @Override
    public <T> T computeInTransaction(@NotNull StoreTransactionalComputable<T> computable) {
        return super.computeInTransaction(txn -> {
            registrar.accept(base, txn);
            return computable.compute(txn);
        });
    }

    @Override
    public <T> T computeInExclusiveTransaction(@NotNull StoreTransactionalComputable<T> computable) {
        return super.computeInExclusiveTransaction(txn -> {
            registrar.accept(base, txn);
            return computable.compute(txn);
        });
    }

    @Override
    public <T> T computeInReadonlyTransaction(@NotNull StoreTransactionalComputable<T> computable) {
        return super.computeInReadonlyTransaction(txn -> {
            registrar.accept(base, txn);
            return computable.compute(txn);
        });
    }

    @Override
    public @NotNull StoreTransaction beginTransaction() {
        final StoreTransaction txn = super.beginTransaction();
        registrar.accept(base, txn);
        return txn;
    }

    @Override
    public @NotNull StoreTransaction beginExclusiveTransaction() {
        final StoreTransaction txn = super.beginExclusiveTransaction();
        registrar.accept(base, txn);
        return txn;
    }

    @Override
    public @NotNull StoreTransaction beginReadonlyTransaction() {
        final StoreTransaction txn = super.beginReadonlyTransaction();
        registrar.accept(base, txn);
        return txn;
    }
}
