package io.river4j.internal.riverinternaltest;

import io.river4j.internal.database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for RiverInternalTest utilities.
 * Java equivalent of riverinternaltest_test.go.
 */
class RiverInternalTestTest {
    
    /**
     * Test equivalent to Go's TestTestTx.
     * Tests transaction isolation and automatic cleanup.
     */
    @Test
    @Execution(ExecutionMode.CONCURRENT)
    void testTestTx() {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        
        // Test transaction rollback behavior - mimic Go's TestTx function
        // Create a transaction that will be rolled back
        Exception rollbackException = assertThrows(RuntimeException.class, () -> {
            dbManager.inTransaction((DatabaseManager.TransactionCallback<Void>) tx -> {
                // Create a regular table (not temporary)
                tx.handle().execute("CREATE TABLE test_tx_table (id bigint)");
                
                // Insert and verify data within the transaction
                tx.handle().execute("INSERT INTO test_tx_table (id) VALUES (1)");
                var result = tx.handle().select("SELECT id FROM test_tx_table").mapToMap().one();
                assertEquals(1L, result.get("id"));
                
                // Force rollback by throwing an exception
                throw new RuntimeException("Intentional rollback for test");
            });
        });
        
        // Verify the exception message contains our expected text (handling potential wrapping)
        String expectedMessage = "Intentional rollback for test";
        assertTrue(rollbackException.getMessage().contains(expectedMessage),
            "Exception message should contain '" + expectedMessage + "' but was: " + rollbackException.getMessage());
        
        // Test that table was rolled back and doesn't exist
        dbManager.inTransaction((DatabaseManager.TransactionCallback<Void>) tx -> {
            // This should not find the table from previous (rolled back) transaction
            assertThrows(Exception.class, () -> {
                tx.handle().select("SELECT * FROM test_tx_table").mapToMap().list();
            }, "Table should not exist after transaction rollback");
            
            return null;
        });
    }
    
    /**
     * Test equivalent to Go's TestTestTx_ConcurrentAccess.
     * Tests concurrent access to test transactions.
     */
    @Test
    void testTestTx_ConcurrentAccess() throws Exception {
        final int maxConnections = 4; // Simulate connection pool limit
        CountDownLatch latch = new CountDownLatch(maxConnections);
        ExecutorService executor = Executors.newFixedThreadPool(maxConnections);
        AtomicInteger successCount = new AtomicInteger(0);
        
        try {
            for (int i = 0; i < maxConnections; i++) {
                final int workerNum = i;
                executor.submit(() -> {
                    try {
                        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
                        
                        // Simulate some database work
                        dbManager.inTransaction(tx -> {
                            tx.handle().execute("SELECT 1");
                            return null;
                        });
                        
                        System.out.println("Opened transaction: " + workerNum);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            assertEquals(maxConnections, successCount.get());
        } finally {
            executor.shutdown();
        }
    }
    
    /**
     * Test equivalent to Go's TestWaitOrTimeout.
     * Tests waiting for a single value with timeout.
     */
    @Test
    @Execution(ExecutionMode.CONCURRENT)
    void testWaitOrTimeout() {
        // Create a queue and inject a few extra numbers to make sure we pick only one
        BlockingQueue<Integer> numQueue = new ArrayBlockingQueue<>(5);
        for (int i = 0; i < 5; i++) {
            numQueue.offer(i);
        }
        
        Integer num = RiverInternalTest.waitOrTimeout(numQueue);
        assertEquals(0, num);
    }
    
    /**
     * Test equivalent to Go's TestWaitOrTimeoutN.
     * Tests waiting for N values with timeout.
     */
    @Test
    @Execution(ExecutionMode.CONCURRENT)
    void testWaitOrTimeoutN() {
        // Create a queue and inject a few extra numbers to make sure we pick the right number
        BlockingQueue<Integer> numQueue = new ArrayBlockingQueue<>(5);
        for (int i = 0; i < 5; i++) {
            numQueue.offer(i);
        }
        
        List<Integer> nums = RiverInternalTest.waitOrTimeoutN(numQueue, 3);
        assertEquals(List.of(0, 1, 2), nums);
    }
    
    /**
     * Test timeout behavior of waitOrTimeout.
     */
    @Test
    void testWaitOrTimeout_Timeout() {
        BlockingQueue<Integer> emptyQueue = new ArrayBlockingQueue<>(1);
        
        AssertionError error = assertThrows(AssertionError.class, () -> {
            RiverInternalTest.waitOrTimeout(emptyQueue, java.time.Duration.ofMillis(100));
        });
        
        assertTrue(error.getMessage().contains("WaitOrTimeout timed out"));
    }
    
    /**
     * Test timeout behavior of waitOrTimeoutN.
     */
    @Test
    void testWaitOrTimeoutN_Timeout() {
        BlockingQueue<Integer> partialQueue = new ArrayBlockingQueue<>(2);
        partialQueue.offer(1);
        // Only 1 item, but asking for 3
        
        AssertionError error = assertThrows(AssertionError.class, () -> {
            RiverInternalTest.waitOrTimeoutN(partialQueue, 3, java.time.Duration.ofMillis(100));
        });
        
        assertTrue(error.getMessage().contains("WaitOrTimeoutN timed out"));
        assertTrue(error.getMessage().contains("received 1 value(s), wanted 3"));
    }
    
    /**
     * Test baseServiceArchetype creation.
     * Ensures archetype is properly configured for tests.
     */
    @Test
    void testBaseServiceArchetype() {
        var archetype = RiverInternalTest.baseServiceArchetype();
        
        assertNotNull(archetype);
        assertNotNull(archetype.logger());
        assertNotNull(archetype.timeProvider());
        assertTrue(archetype.disableSleep());
    }
    
    /**
     * Test database manager creation.
     */
    @Test
    void testTestDatabaseManager() {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        assertNotNull(dbManager);
        
        // Test basic database connectivity
        dbManager.inTransaction(tx -> {
            var result = tx.handle().select("SELECT 1 as test").mapToMap().one();
            assertEquals(1, result.get("test"));
            return null;
        });
    }
    
    /**
     * Test table truncation utility.
     */
    @Test
    void testTruncateRiverTables() {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        
        // This should not throw even if tables don't exist
        assertDoesNotThrow(() -> {
            RiverInternalTest.truncateRiverTables(dbManager);
        });
    }
}