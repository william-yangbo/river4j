package io.river4j.internal.rivertest;

import io.river4j.internal.baseservice.ServiceArchetype;
import io.river4j.internal.database.DatabaseManager;
import io.river4j.internal.database.model.RiverJob;
import io.river4j.internal.riverinternaltest.RiverInternalTest;
import io.river4j.internal.riverinternaltest.slogtest.SlogTest;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * RiverTest contains shared testing utilities for public API tests.
 * This is the Java equivalent of the Go rivertest package.
 */
public class RiverTest {
    
    private static final Logger logger = SlogTest.newLogger(RiverTest.class.getName());
    
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
        return RiverInternalTest.testDatabaseManager();
    }
    
    /**
     * Creates a dedicated test database for isolated testing.
     */
    public static DatabaseManager testDB() {
        return RiverInternalTest.testDB();
    }
    
    /**
     * Wait for a value from a CompletableFuture with a timeout.
     */
    public static <T> T waitOrTimeout(CompletableFuture<T> future, java.time.Duration timeout) {
        return RiverInternalTest.waitOrTimeout(future, timeout);
    }
    
    /**
     * Wait for a value from a CompletableFuture with a default timeout of 5 seconds.
     */
    public static <T> T waitOrTimeout(CompletableFuture<T> future) {
        return RiverInternalTest.waitOrTimeout(future);
    }
    
    /**
     * Create a service archetype with stubbed time for testing.
     */
    public static ServiceArchetype createStubbedArchetype(Instant time) {
        return RiverInternalTest.createStubbedArchetype(time);
    }
    
    /**
     * Returns a logger suitable for use in tests.
     */
    public static Logger logger() {
        return logger;
    }
    
    /**
     * Returns a logger with WARN level for tests with noisy output.
     */
    public static Logger loggerWarn() {
        return SlogTest.newLogger("RiverTestWarn", java.util.logging.Level.WARNING);
    }
    
    /**
     * Verifies that a job of the given kind was inserted for work.
     * This is a convenience method that delegates to JobAssertions.
     * 
     * @param databaseManager the database manager to query
     * @param expectedKind the expected job kind
     * @param opts optional requirements for the job
     * @return the found job
     * @throws AssertionError if job requirements are not met
     */
    public static RiverJob requireInserted(DatabaseManager databaseManager, String expectedKind, JobAssertions.RequireInsertedOpts opts) {
        DBTX dbtx = new DatabaseManagerDBTX(databaseManager);
        return JobAssertions.requireInserted(dbtx, expectedKind, opts);
    }
    
    /**
     * Verifies that a job of the given kind was inserted for work (without additional options).
     */
    public static RiverJob requireInserted(DatabaseManager databaseManager, String expectedKind) {
        DBTX dbtx = new DatabaseManagerDBTX(databaseManager);
        return JobAssertions.requireInserted(dbtx, expectedKind, null);
    }
    
    /**
     * Verifies that jobs of the given kinds were inserted for work in the correct order.
     * This is a convenience method that delegates to JobAssertions.
     */
    public static List<RiverJob> requireManyInserted(DatabaseManager databaseManager, List<JobAssertions.ExpectedJob> expectedJobs) {
        DBTX dbtx = new DatabaseManagerDBTX(databaseManager);
        return JobAssertions.requireManyInserted(dbtx, expectedJobs);
    }
    
    // === Channel Utilities ===
    
    /**
     * Continuously drain and discard items from a queue.
     * Returns a stop function that should be called to stop draining.
     * 
     * @param drainQueue the queue to drain from
     * @return a Runnable to stop draining
     */
    public static <T> Runnable discardContinuously(BlockingQueue<T> drainQueue) {
        return ChannelUtils.discardContinuously(drainQueue);
    }
    
    /**
     * Continuously drain and accumulate items from a queue.
     * Returns a supplier that returns accumulated items and stops draining when called.
     * 
     * @param drainQueue the queue to drain from
     * @return a supplier that returns accumulated items and stops draining
     */
    public static <T> Supplier<List<T>> drainContinuously(BlockingQueue<T> drainQueue) {
        return ChannelUtils.drainContinuously(drainQueue);
    }
    
    // === Transaction Utilities ===
    
    /**
     * Creates a DBTX wrapper around a database connection.
     * 
     * @param connection the database connection
     * @return a DBTX instance
     */
    public static DBTX wrapConnection(Connection connection) {
        return TestTx.wrap(connection);
    }
    
    /**
     * Executes code within a transaction that is automatically rolled back.
     * 
     * @param connection the database connection
     * @param callback function to execute within the transaction
     * @return the result of the callback function
     * @throws SQLException if database operation fails
     */
    public static <T> T withTestTx(Connection connection, Function<DBTX, T> callback) throws SQLException {
        return TestTx.withTx(connection, callback);
    }
}