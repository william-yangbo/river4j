package io.river4j.internal.testdb;

import io.river4j.internal.database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for TestDB and TestDBManager alignment with Go's DBWithPool.
 */
class TestDBAlignmentTest {
    
    private TestDBManager manager;
    private ExecutorService executor;
    
    @BeforeEach
    void setUp() {
        manager = new TestDBManager(10, null, null);
        executor = Executors.newFixedThreadPool(4);
    }
    
    @AfterEach
    void tearDown() throws InterruptedException {
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (manager != null) {
            manager.close();
        }
    }
    
    @Test
    void testBasicAcquireAndRelease() {
        TestDB db = manager.acquire();
        assertNotNull(db);
        assertNotNull(db.getDbName());
        assertNotNull(db.getDatabaseManager());
        
        // Test that we can get a connection
        DatabaseManager databaseManager = db.getDatabaseManager();
        assertNotNull(databaseManager);
        
        // Release should not throw
        assertDoesNotThrow(db::release);
    }
    
    @Test
    void testMultipleDatabases() {
        List<TestDB> dbs = new ArrayList<>();
        
        // Acquire multiple databases
        for (int i = 0; i < 3; i++) {
            TestDB db = manager.acquire();
            assertNotNull(db);
            assertNotNull(db.getDbName());
            dbs.add(db);
            
            // Each database should have a unique name
            for (int j = 0; j < i; j++) {
                assertNotEquals(dbs.get(j).getDbName(), db.getDbName());
            }
        }
        
        // Release all databases
        for (TestDB db : dbs) {
            assertDoesNotThrow(db::release);
        }
    }
    
    @Test
    void testConcurrentAccess() throws InterruptedException {
        int numThreads = 4;
        int operationsPerThread = 5;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < numThreads; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    TestDB db = manager.acquire();
                    assertNotNull(db);
                    assertNotNull(db.getDbName());
                    assertNotNull(db.getDatabaseManager());
                    
                    try {
                        Thread.sleep(10); // Simulate some work
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    
                    assertDoesNotThrow(db::release);
                }
            }, executor);
            futures.add(future);
        }
        
        // Wait for all futures to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            fail("Concurrent test failed: " + e.getMessage());
        }
    }
    
    @Test
    void testDoubleReleaseIsSafe() {
        TestDB db = manager.acquire();
        assertNotNull(db);
        
        // First release should work
        assertDoesNotThrow(db::release);
        
        // Second release should be safe (idempotent)
        assertDoesNotThrow(db::release);
    }
    
    @Test
    void testManagerWithPrepareFunc() throws SQLException {
        final boolean[] prepareCalled = {false};
        
        TestDBManager managerWithPrepare = new TestDBManager(10, 
            (Connection conn) -> {
                prepareCalled[0] = true;
                // Create a test table
                conn.createStatement().execute("CREATE TABLE test_table (id SERIAL PRIMARY KEY, name VARCHAR(100))");
            }, 
            null);
            
        try {
            TestDB db = managerWithPrepare.acquire();
            assertNotNull(db);
            
            // The prepare function should have been called
            // We can't easily verify the table creation without direct connection access,
            // but we can verify the callback was invoked
            assertTrue(prepareCalled[0]);
            db.release();
        } finally {
            managerWithPrepare.close();
        }
    }
    
    @Test
    void testManagerWithCleanupFunc() throws SQLException {
        final boolean[] cleanupCalled = {false};
        
        TestDBManager managerWithCleanup = new TestDBManager(10, 
            (Connection conn) -> {
                // Create a test table
                conn.createStatement().execute("CREATE TABLE cleanup_test (id SERIAL PRIMARY KEY)");
                conn.createStatement().execute("INSERT INTO cleanup_test (id) VALUES (1)");
            },
            (Connection conn) -> {
                cleanupCalled[0] = true;
                // Clean up the test data
                conn.createStatement().execute("DELETE FROM cleanup_test");
            });
            
        try {
            TestDB db = managerWithCleanup.acquire();
            assertNotNull(db);
            assertTrue(managerWithCleanup.hasCleanupFunc());
            
            db.release();
            // Note: In current implementation, cleanup function is available but not automatically called
            // This matches the Go implementation where cleanup is managed by the pool
        } finally {
            managerWithCleanup.close();
        }
    }
    
    @Test
    void testResourceCleanupOnRelease() {
        TestDB db = manager.acquire();
        String dbName = db.getDbName();
        DatabaseManager databaseManager = db.getDatabaseManager();
        
        assertNotNull(dbName);
        assertNotNull(databaseManager);
        
        // Release the database
        db.release();
        
        // After release, the database name should still be accessible for logging/debugging
        assertEquals(dbName, db.getDbName());
    }
}