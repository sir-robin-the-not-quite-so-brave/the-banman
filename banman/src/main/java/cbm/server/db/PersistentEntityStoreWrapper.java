package cbm.server.db;

import jetbrains.exodus.backup.BackupStrategy;
import jetbrains.exodus.bindings.ComparableBinding;
import jetbrains.exodus.core.execution.MultiThreadDelegatingJobProcessor;
import jetbrains.exodus.entitystore.BlobVault;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityId;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.PersistentEntityStoreConfig;
import jetbrains.exodus.entitystore.StoreTransaction;
import jetbrains.exodus.entitystore.StoreTransactionalComputable;
import jetbrains.exodus.entitystore.StoreTransactionalExecutable;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.management.Statistics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PersistentEntityStore wrapper, that delegates all its methods to its base instance.
 * Useful for custom method overrides.
 */
class PersistentEntityStoreWrapper implements PersistentEntityStore {

    protected final PersistentEntityStore base;

    public PersistentEntityStoreWrapper(PersistentEntityStore base) {
        this.base = base;
    }

    @NotNull
    @Override
    public Environment getEnvironment() {
        return base.getEnvironment();
    }

    @Override
    public void clear() {
        base.clear();
    }

    @Override
    public void executeInTransaction(@NotNull StoreTransactionalExecutable executable) {
        base.executeInTransaction(executable);
    }

    @Override
    public void executeInExclusiveTransaction(@NotNull StoreTransactionalExecutable executable) {
        base.executeInExclusiveTransaction(executable);
    }

    @Override
    public void executeInReadonlyTransaction(@NotNull StoreTransactionalExecutable executable) {
        base.executeInReadonlyTransaction(executable);
    }

    @Override
    public <T> T computeInTransaction(@NotNull StoreTransactionalComputable<T> computable) {
        return base.computeInTransaction(computable);
    }

    @Override
    public <T> T computeInExclusiveTransaction(@NotNull StoreTransactionalComputable<T> computable) {
        return base.computeInExclusiveTransaction(computable);
    }

    @Override
    public <T> T computeInReadonlyTransaction(@NotNull StoreTransactionalComputable<T> computable) {
        return base.computeInReadonlyTransaction(computable);
    }

    @NotNull
    @Override
    public BlobVault getBlobVault() {
        return base.getBlobVault();
    }

    @Override
    public void registerCustomPropertyType(@NotNull StoreTransaction txn, @NotNull Class<? extends Comparable> clazz,
                                           @NotNull ComparableBinding binding) {

        base.registerCustomPropertyType(txn, clazz, binding);
    }

    @Override
    public Entity getEntity(@NotNull EntityId id) {
        return base.getEntity(id);
    }

    @Override
    public int getEntityTypeId(@NotNull String entityType) {
        return base.getEntityTypeId(entityType);
    }

    @NotNull
    @Override
    public String getEntityType(int entityTypeId) {
        return base.getEntityType(entityTypeId);
    }

    @Override
    public void renameEntityType(@NotNull String oldEntityTypeName, @NotNull String newEntityTypeName) {
        base.renameEntityType(oldEntityTypeName, newEntityTypeName);
    }

    @Override
    public long getUsableSpace() {
        return base.getUsableSpace();
    }

    @NotNull
    @Override
    public PersistentEntityStoreConfig getConfig() {
        return base.getConfig();
    }

    @NotNull
    @Override
    public MultiThreadDelegatingJobProcessor getAsyncProcessor() {
        return base.getAsyncProcessor();
    }

    @SuppressWarnings("rawtypes")
    @NotNull
    @Override
    public Statistics getStatistics() {
        return base.getStatistics();
    }

    @NotNull
    @Override
    public BackupStrategy getBackupStrategy() {
        return base.getBackupStrategy();
    }

    @NotNull
    @Override
    public String getName() {
        return base.getName();
    }

    @NotNull
    @Override
    public String getLocation() {
        return base.getLocation();
    }

    @NotNull
    @Override
    public StoreTransaction beginTransaction() {
        return base.beginTransaction();
    }

    @NotNull
    @Override
    public StoreTransaction beginExclusiveTransaction() {
        return base.beginExclusiveTransaction();
    }

    @NotNull
    @Override
    public StoreTransaction beginReadonlyTransaction() {
        return base.beginReadonlyTransaction();
    }

    @Nullable
    @Override
    public StoreTransaction getCurrentTransaction() {
        return base.getCurrentTransaction();
    }

    @Override
    public void close() {
        base.close();
    }
}
