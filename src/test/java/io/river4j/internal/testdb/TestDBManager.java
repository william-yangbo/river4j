package io.river4j.internal.testdb;

import io.river4j.internal.database.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manager manages a pool of test databases up to a max size. Each DB keeps a
 * DataSource which is available when one is acquired from the Manager.
 * Databases can optionally be prepared with a PrepareFunc before being added
 * into the pool, and cleaned up with a CleanupFunc before being returned to the
 * pool for reuse.
 * 
 * This setup makes it trivial to run fully isolated tests in parallel.
 */
public class TestDBManager {
    
    private static final Logger logger = LoggerFactory.getLogger(TestDBManager.class);
    
    // Shared PostgreSQL container for all test databases
    private static final AtomicReference<PostgreSQLContainer<?>> sharedContainer = new AtomicReference<>();
    
    private final AtomicInteger nextDBNum = new AtomicInteger(0);
    private final Consumer<Connection> prepareFunc;
    private final Consumer<Connection> cleanupFunc;
    
    @FunctionalInterface
    public interface PrepareFunc {
        void prepare(Connection connection) throws SQLException;
    }
    
    @FunctionalInterface
    public interface CleanupFunc {
        void cleanup(Connection connection) throws SQLException;
    }
    
    /**
     * Create a new TestDBManager with the specified configuration.
     */
    public TestDBManager(int maxPoolSize, PrepareFunc prepareFunc, CleanupFunc cleanupFunc) {
        // maxPoolSize is currently not used in Java implementation
        this.prepareFunc = conn -> {
            if (prepareFunc != null) {
                try {
                    prepareFunc.prepare(conn);
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to prepare test database", e);
                }
            }
        };
        this.cleanupFunc = conn -> {
            if (cleanupFunc != null) {
                try {
                    cleanupFunc.cleanup(conn);
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to cleanup test database", e);
                }
            }
        };
    }
    
    /**
     * Acquire a test database. The returned TestDB must be released after use.
     */
    public TestDB acquire() {
        DatabaseConfigWithName configWithName = createDatabaseConfigWithName();
        return new TestDB(this, configWithName.config, configWithName.dbName);
    }
    
    /**
     * Check if cleanup function is provided.
     */
    public boolean hasCleanupFunc() {
        return cleanupFunc != null;
    }
    
    /**
     * Return a TestDB to the pool (placeholder for future pool implementation).
     * Currently this is a no-op, but mirrors Go's resource pool behavior.
     */
    public void returnToPool(TestDB testDB) {
        // TODO: Implement actual resource pooling similar to Go's puddle
        // For now, this is a placeholder to match the API
        logger.debug("TestDB returned to pool: {}", testDB.getDbName());
    }
    
    private static class DatabaseConfigWithName {
        final DatabaseConfig config;
        final String dbName;
        
        DatabaseConfigWithName(DatabaseConfig config, String dbName) {
            this.config = config;
            this.dbName = dbName;
        }
    }
    
    /**
     * Create a database configuration for a new test database.
     */
    private synchronized DatabaseConfigWithName createDatabaseConfigWithName() {
        PostgreSQLContainer<?> container = getOrCreateSharedContainer();
        
        // Wait for container to be fully started
        if (!container.isRunning()) {
            throw new RuntimeException("Container is not running");
        }
        
        int dbNum = nextDBNum.getAndIncrement();
        long timestamp = System.currentTimeMillis();
        String dbName = "river_testdb_" + dbNum + "_" + timestamp;
        
        logger.info("Using test database: {}", dbName);
        
        // Create the database
        try (Connection adminConn = container.createConnection("")) {
            adminConn.createStatement().execute("CREATE DATABASE " + dbName);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create test database " + dbName, e);
        }
        
        // Return config and name for the new database
        DatabaseConfig config = new DatabaseConfig(
            container.getJdbcUrl().replace("/river_testdb", "/" + dbName),
            container.getUsername(),
            container.getPassword()
        );
        
        return new DatabaseConfigWithName(config, dbName);
    }
    
    /**
     * Gets or creates a shared PostgreSQL container for all tests.
     */
    @SuppressWarnings("resource") // Container is closed in shutdown hook
    private static PostgreSQLContainer<?> getOrCreateSharedContainer() {
        PostgreSQLContainer<?> existingContainer = sharedContainer.get();
        if (existingContainer == null) {
            synchronized (TestDBManager.class) {
                existingContainer = sharedContainer.get();
                if (existingContainer == null) {
                    PostgreSQLContainer<?> newContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                            .withDatabaseName("river_testdb")
                            .withUsername("river")
                            .withPassword("river");
                    newContainer.start();
                    sharedContainer.set(newContainer);
                    
                    // Add shutdown hook to stop container
                    final PostgreSQLContainer<?> containerToStop = newContainer;
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        if (containerToStop.isRunning()) {
                            containerToStop.stop();
                        }
                    }));
                    existingContainer = newContainer;
                }
            }
        }
        return existingContainer;
    }
    
    /**
     * Close the manager and all resources.
     * Note: In test environment, container is kept running and closed by shutdown hook.
     */
    public void close() {
        // Don't stop the container here - let it run for other tests
        // Container will be stopped by the shutdown hook
    }
    
    // Package-private methods for TestDB
    void prepareDatabase(Connection connection) {
        if (prepareFunc != null) {
            prepareFunc.accept(connection);
        }
    }
    
    void cleanupDatabase(Connection connection) {
        if (cleanupFunc != null) {
            cleanupFunc.accept(connection);
        }
    }
}