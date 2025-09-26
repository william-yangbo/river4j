package io.river4j.internal.testdb;

import io.river4j.internal.database.DatabaseManager;
import io.river4j.internal.database.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TestDB is a wrapper for a test database resource, equivalent to Go's DBWithPool.
 * The database is made available via a preconfigured DatabaseManager.
 * This implementation mirrors Go's puddle resource management behavior.
 */
public class TestDB implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(TestDB.class);
    
    private final TestDBManager manager;
    private DatabaseConfig config;
    private volatile DatabaseManager databaseManager;
    private final String dbName;
    private final AtomicBoolean released = new AtomicBoolean(false);
    
    TestDB(TestDBManager manager, DatabaseConfig config, String dbName) {
        this.manager = manager;
        this.config = config;
        this.dbName = dbName;
        this.databaseManager = new DatabaseManager(config);
        
        // Run preparation if needed
        try (Connection conn = config.getDataSource().getConnection()) {
            manager.prepareDatabase(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prepare test database", e);
        }
    }
    
    /**
     * Get the DatabaseManager for this test database.
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    /**
     * Get the DatabaseConfig for this test database.
     */
    public DatabaseConfig getConfig() {
        return config;
    }
    
    /**
     * Get the database name for this test database.
     * Equivalent to Go's dbName field.
     */
    public String getDbName() {
        return dbName;
    }
    
    /**
     * Release the test database back to the manager.
     * This should be called when the test is finished with the database.
     * Mirrors Go's DBWithPool.Release() behavior.
     */
    public void release() {
        if (released.compareAndSet(false, true)) {
            performRelease();
        }
    }
    
    private void performRelease() {
        try {
            if (databaseManager != null) {
                // Close the database manager first
                databaseManager.close();
            }
            
            // Drop the test database at container level if needed
            // Use the manager's shared container access method
            if (manager != null && dbName != null) {
                try {
                    // Access the shared container through reflection or add a method to TestDBManager
                    // For now, we'll rely on the container's natural cleanup
                    logger.debug("Released test database: {}", dbName);
                } catch (Exception e) {
                    // Log but don't fail on cleanup errors
                    logger.debug("Failed to cleanup test database {}: {}", dbName, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error during release cleanup", e);
        } finally {
            if (manager != null) {
                manager.returnToPool(this);
            }
        }
    }
    
    @Override
    public void close() {
        release();
    }
}