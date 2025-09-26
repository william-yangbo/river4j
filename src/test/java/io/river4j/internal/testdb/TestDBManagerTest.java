package io.river4j.internal.testdb;

import org.junit.jupiter.api.Test;
// Removed parallel execution for now

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for TestDBManager functionality.
 * Java equivalent of manager_test.go.
 */
class TestDBManagerTest {

    /**
     * Test equivalent to Go's TestManager_AcquireMultiple.
     * Tests acquiring multiple test databases and ensuring proper database name prefixes.
     */
    @Test
    void testAcquireMultiple() {
        TestDBManager manager = new TestDBManager(10, null, null);
        
        try {
            // Acquire first database
            TestDB testDB0 = manager.acquire();
            assertNotNull(testDB0);
            
            checkDBNameForTestDB(testDB0, "river_testdb_");
            
            // Acquire second database
            TestDB testDB1 = manager.acquire();
            assertNotNull(testDB1);
            
            checkDBNameForTestDB(testDB1, "river_testdb_");
            
            // Release first database
            testDB0.release();
            
            // Acquire another database - should be able to reuse resources
            TestDB testDB0Again = manager.acquire();
            assertNotNull(testDB0Again);
            
            checkDBNameForTestDB(testDB0Again, "river_testdb_");
            
            // Clean up
            testDB0Again.release();
            testDB1.release();
            
        } finally {
            manager.close();
        }
    }

    /**
     * Test equivalent to Go's TestManager_ReleaseTwice.
     * Tests that releasing a database twice is safe (no-op).
     */
    @Test
    void testReleaseTwice() {
        TestDBManager manager = new TestDBManager(10, null, null);
        
        try {
            TestDB testDB0 = manager.acquire();
            assertNotNull(testDB0);
            
            // Verify we can query the database
            selectOne(testDB0);
            
            // Release the database
            testDB0.release();
            
            // Release again - should be a no-op and not throw an exception
            assertDoesNotThrow(() -> testDB0.release());
            
            // Acquire a new database to ensure the manager is still functional
            TestDB testDB1 = manager.acquire();
            assertNotNull(testDB1);
            
            selectOne(testDB1);
            testDB1.release();
            
        } finally {
            manager.close();
        }
    }

    /**
     * Test database preparation and cleanup functions.
     */
    @Test
    void testPrepareAndCleanup() {
        AtomicInteger prepareCount = new AtomicInteger(0);
        AtomicInteger cleanupCount = new AtomicInteger(0);
        
        TestDBManager.PrepareFunc prepareFunc = conn -> {
            prepareCount.incrementAndGet();
            // Create a test table
            try (var stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS test_prepare_table (id INTEGER)");
                stmt.execute("INSERT INTO test_prepare_table (id) VALUES (999)");
            }
        };
        
        TestDBManager.CleanupFunc cleanupFunc = conn -> {
            cleanupCount.incrementAndGet();
            // Clean up the test table
            try (var stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS test_prepare_table");
            }
        };
        
        TestDBManager manager = new TestDBManager(5, prepareFunc, cleanupFunc);
        
        try {
            TestDB testDB = manager.acquire();
            
            // Verify prepare function was called
            assertTrue(prepareCount.get() > 0, "Prepare function should have been called");
            
            // Verify the prepared data exists
            boolean tableExists = false;
            try (Connection conn = testDB.getConfig().getDataSource().getConnection()) {
                try (var stmt = conn.createStatement()) {
                    try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_prepare_table WHERE id = 999")) {
                        if (rs.next()) {
                            tableExists = rs.getInt(1) > 0;
                        }
                    }
                }
            } catch (SQLException e) {
                fail("Failed to verify prepared data: " + e.getMessage());
            }
            assertTrue(tableExists, "Prepared data should exist");
            
            // Release the database (should trigger cleanup)
            testDB.release();
            
            // Verify cleanup function was called
            assertTrue(cleanupCount.get() > 0, "Cleanup function should have been called");
            
        } finally {
            manager.close();
        }
    }
    
    /**
     * Test concurrent database acquisition.
     */
    @Test
    void testConcurrentAcquisition() throws InterruptedException {
        TestDBManager manager = new TestDBManager(5, null, null);
        
        try {
            int numThreads = 10;
            Thread[] threads = new Thread[numThreads];
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        TestDB testDB = manager.acquire();
                        
                        // Verify we can use the database
                        selectOne(testDB);
                        
                        // Sleep briefly to simulate work
                        Thread.sleep(50);
                        
                        testDB.release();
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        e.printStackTrace();
                    }
                });
                threads[i].start();
            }
            
            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }
            
            assertEquals(numThreads, successCount.get(), "All threads should succeed");
            assertEquals(0, errorCount.get(), "No errors should occur");
            
        } finally {
            manager.close();
        }
    }

    /**
     * Helper method to check database name has the expected prefix.
     */
    private void checkDBNameForTestDB(TestDB testDB, String expectedPrefix) {
        try (Connection conn = testDB.getConfig().getDataSource().getConnection()) {
            try (var stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
                    assertTrue(rs.next(), "Should get a result");
                    String currentDBName = rs.getString(1);
                    assertTrue(currentDBName.startsWith(expectedPrefix), 
                        "Database name '" + currentDBName + "' should start with '" + expectedPrefix + "'");
                }
            }
        } catch (SQLException e) {
            fail("Failed to check database name: " + e.getMessage());
        }
    }

    /**
     * Helper method to perform a simple SELECT 1 query.
     */
    private void selectOne(TestDB testDB) {
        try (Connection conn = testDB.getConfig().getDataSource().getConnection()) {
            try (var stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    assertTrue(rs.next(), "Should get a result");
                    assertEquals(1, rs.getInt(1), "Should get value 1");
                }
            }
        } catch (SQLException e) {
            fail("Failed to execute SELECT 1: " + e.getMessage());
        }
    }
}