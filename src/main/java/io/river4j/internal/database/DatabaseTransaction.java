package io.river4j.internal.database;

import io.river4j.internal.database.dao.JobDao;
import io.river4j.internal.database.dao.LeaderDao;
import io.river4j.internal.database.dao.MigrationDao;
import org.jdbi.v3.core.Handle;

/**
 * Database transaction context.
 * Provides access to DAOs within a transaction scope.
 */
public class DatabaseTransaction {
    
    private final Handle handle;
    
    public DatabaseTransaction(Handle handle) {
        this.handle = handle;
    }
    
    /**
     * Get job DAO within this transaction.
     */
    public JobDao jobs() {
        return handle.attach(JobDao.class);
    }
    
    /**
     * Get leader DAO within this transaction.
     */
    public LeaderDao leaders() {
        return handle.attach(LeaderDao.class);
    }
    
    /**
     * Get migration DAO within this transaction.
     */
    public MigrationDao migrations() {
        return handle.attach(MigrationDao.class);
    }
    
    /**
     * Get migration DAO (alias for migrations).
     */
    public MigrationDao getMigrationDao() {
        return migrations();
    }
    
    /**
     * Get direct handle for custom operations.
     */
    public Handle handle() {
        return handle;
    }
}