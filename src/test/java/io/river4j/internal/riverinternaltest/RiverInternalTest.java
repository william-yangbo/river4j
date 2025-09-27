package io.river4j.internal.riverinternaltest;

import io.river4j.internal.baseservice.ServiceArchetype;
import io.river4j.internal.database.DatabaseManager;
import io.river4j.internal.database.config.DatabaseConfig;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Shared testing utilities for tests throughout the rest of the project.
 * Java equivalent of Go's riverinternaltest package.
 */
public class RiverInternalTest {
    
    private static final Logger logger = Logger.getLogger(RiverInternalTest.class.getName());
    
    // Shared PostgreSQL container for tests
    private static final AtomicReference<PostgreSQLContainer<?>> sharedContainer = new AtomicReference<>();
    
    /**
     * Returns a base service archetype suitable for use in tests.
     * Creates a new instance so that it's not possible to accidentally taint a shared object.
     */
    public static ServiceArchetype baseServiceArchetype() {
        return ServiceArchetype.builder()
                .logger(logger)
                .timeProvider(Instant::now)
                .disableSleep(true)
                .build();
    }
    
    /**
     * Creates a test database manager using TestContainers PostgreSQL.
     */
    public static DatabaseManager testDatabaseManager() {
        PostgreSQLContainer<?> container = getOrCreateSharedContainer();
        DatabaseConfig config = new DatabaseConfig(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword()
        );
        return new DatabaseManager(config);
    }
    
    /**
     * Creates a dedicated test database for isolated testing.
     * Like Go's riverinternaltest.TestDB(), this returns a database with only base migrations applied.
     * The test will set custom migrations using setMigrations().
     */
    public static DatabaseManager testDB() {
        DatabaseManager dbManager = testDatabaseManager();
        
        // Apply ONLY base migrations (versions 1,2) like Go's riverinternaltest.TestDB() does
        // This ensures the database starts at version 2, ready for test migrations (3,4)
        try {
            io.river4j.internal.dbmigrate.Migrator migrator = 
                new io.river4j.internal.dbmigrate.Migrator(dbManager, "io/river4j/internal/dbmigrate");
            // This applies only the base migrations (1,2), not test migrations
            migrator.migrateUp(new io.river4j.internal.dbmigrate.MigrateOptions());
        } catch (Exception e) {
            logger.warning("Failed to apply base migrations in testDB(): " + e.getMessage());
            // Continue - might be ok for some tests
        }
        
        return dbManager;
    }
    
    /**
     * Creates a test database manager WITHOUT running migrations.
     * This is used for migration tests that need a clean database.
     */
    public static DatabaseManager testDatabaseManagerWithoutMigrations() {
        PostgreSQLContainer<?> container = getOrCreateSharedContainer();
        DatabaseConfig config = new DatabaseConfig(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword()
        );
        return new DatabaseManager(config);
    }
    
    /**
     * Gets or creates a shared PostgreSQL container for all tests.
     */
    @SuppressWarnings("resource") // Container is closed in shutdown hook
    private static PostgreSQLContainer<?> getOrCreateSharedContainer() {
        PostgreSQLContainer<?> existingContainer = sharedContainer.get();
        if (existingContainer == null) {
            synchronized (sharedContainer) {
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
     * Wait for a value from a BlockingQueue with timeout.
     * Java equivalent of Go's WaitOrTimeout.
     */
    public static <T> T waitOrTimeout(BlockingQueue<T> queue) {
        return waitOrTimeout(queue, getWaitTimeout());
    }
    
    /**
     * Wait for a value from a BlockingQueue with custom timeout.
     */
    public static <T> T waitOrTimeout(BlockingQueue<T> queue, Duration timeout) {
        try {
            T value = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (value == null) {
                throw new AssertionError("WaitOrTimeout timed out after waiting " + timeout);
            }
            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Wait was interrupted", e);
        }
    }
    
    /**
     * Wait for a CompletableFuture to complete with timeout.
     * Java equivalent of Go's WaitOrTimeout for futures.
     */
    public static <T> T waitOrTimeout(java.util.concurrent.CompletableFuture<T> future) {
        return waitOrTimeout(future, getWaitTimeout());
    }
    
    /**
     * Wait for a CompletableFuture to complete with custom timeout.
     */
    public static <T> T waitOrTimeout(java.util.concurrent.CompletableFuture<T> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("WaitOrTimeout timed out after waiting " + timeout);
        } catch (Exception e) {
            throw new RuntimeException("Wait failed", e);
        }
    }
    
    /**
     * Wait for N values from a BlockingQueue with timeout.
     * Java equivalent of Go's WaitOrTimeoutN.
     */
    public static <T> java.util.List<T> waitOrTimeoutN(BlockingQueue<T> queue, int numValues) {
        return waitOrTimeoutN(queue, numValues, getWaitTimeout());
    }
    
    /**
     * Wait for N values from a BlockingQueue with custom timeout.
     */
    public static <T> java.util.List<T> waitOrTimeoutN(BlockingQueue<T> queue, int numValues, Duration timeout) {
        java.util.List<T> values = new java.util.ArrayList<>(numValues);
        long timeoutMillis = timeout.toMillis();
        long deadline = System.currentTimeMillis() + timeoutMillis;
        
        while (values.size() < numValues) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new AssertionError(String.format(
                    "WaitOrTimeoutN timed out after waiting %s (received %d value(s), wanted %d)", 
                    timeout, values.size(), numValues));
            }
            
            try {
                T value = queue.poll(remaining, TimeUnit.MILLISECONDS);
                if (value != null) {
                    values.add(value);
                } else {
                    throw new AssertionError(String.format(
                        "WaitOrTimeoutN timed out after waiting %s (received %d value(s), wanted %d)", 
                        timeout, values.size(), numValues));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Wait was interrupted", e);
            }
        }
        
        return values;
    }
    
    /**
     * Get wait timeout duration, with extra leeway in CI environments.
     * Java equivalent of rivercommon.WaitTimeout().
     */
    public static Duration getWaitTimeout() {
        // Check for CI environment
        if ("true".equals(System.getenv("GITHUB_ACTIONS")) || 
            "true".equals(System.getenv("CI"))) {
            return Duration.ofSeconds(10);
        }
        return Duration.ofSeconds(3);
    }
    
    /**
     * Create a service archetype with stubbed time for testing.
     */
    public static ServiceArchetype createStubbedArchetype(Instant time) {
        return ServiceArchetype.builder()
                .logger(logger)
                .timeProvider(() -> time)
                .disableSleep(true)
                .build();
    }
    
    /**
     * Truncates River tables for test cleanup.
     * Java equivalent of TruncateRiverTables.
     */
    public static void truncateRiverTables(DatabaseManager databaseManager) {
        String[] tables = {"river_job", "river_leader", "river_migration"};
        
        databaseManager.inTransaction(tx -> {
            for (String table : tables) {
                try {
                    tx.handle().execute("TRUNCATE TABLE " + table + " CASCADE");
                } catch (Exception e) {
                    // Ignore errors for non-existent tables during cleanup
                    logger.fine("Could not truncate table " + table + ": " + e.getMessage());
                }
            }
            return null;
        });
    }
}