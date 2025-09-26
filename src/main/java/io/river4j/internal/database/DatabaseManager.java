package io.river4j.internal.database;

import io.river4j.internal.database.config.DatabaseConfig;
import io.river4j.internal.database.dao.JobDao;
import io.river4j.internal.database.dao.LeaderDao;
import io.river4j.internal.database.dao.MigrationDao;
import org.jdbi.v3.core.Jdbi;

/**
 * Main database manager for River4J.
 * Provides access to all DAOs and database operations.
 * This is the Java equivalent of dbsqlc.Queries in Go.
 */
public class DatabaseManager {
    
    private final DatabaseConfig config;
    private final Jdbi jdbi;
    
    public DatabaseManager(String jdbcUrl, String username, String password) {
        this.config = new DatabaseConfig(jdbcUrl, username, password);
        this.jdbi = config.getJdbi();
    }
    
    public DatabaseManager(DatabaseConfig config) {
        this.config = config;
        this.jdbi = config.getJdbi();
    }
    
    /**
     * Get job data access object.
     */
    public JobDao jobs() {
        return jdbi.onDemand(JobDao.class);
    }
    
    /**
     * Get leader data access object.
     */
    public LeaderDao leaders() {
        return jdbi.onDemand(LeaderDao.class);
    }
    
    /**
     * Get migration data access object.
     */
    public MigrationDao migrations() {
        return jdbi.onDemand(MigrationDao.class);
    }
    
    /**
     * Get direct JDBI instance for custom queries.
     */
    public Jdbi jdbi() {
        return jdbi;
    }
    
    /**
     * Execute code within a transaction.
     */
    public <T> T inTransaction(TransactionCallback<T> callback) {
        return jdbi.inTransaction(handle -> {
            DatabaseTransaction transaction = new DatabaseTransaction(handle);
            try {
                return callback.execute(transaction);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Execute code within a transaction without return value.
     */
    public void inTransaction(TransactionVoidCallback callback) {
        jdbi.useTransaction(handle -> {
            DatabaseTransaction transaction = new DatabaseTransaction(handle);
            try {
                callback.execute(transaction);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Close database resources.
     */
    public void close() {
        config.close();
    }
    
    /**
     * Callback interface for transactions with return value.
     */
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(DatabaseTransaction transaction) throws Exception;
    }
    
    /**
     * Callback interface for transactions without return value.
     */
    @FunctionalInterface
    public interface TransactionVoidCallback {
        void execute(DatabaseTransaction transaction) throws Exception;
    }
}