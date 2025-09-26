package io.river4j.internal.riverinternaltest.sharedtx;

import io.river4j.internal.database.DatabaseManager;
import io.river4j.internal.riverinternaltest.RiverInternalTest;
import org.jdbi.v3.core.Handle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for SharedTx functionality.
 * Java equivalent of shared_tx_test.go.
 */
@Execution(ExecutionMode.CONCURRENT)
class SharedTxTest {

    // private SharedTx setup() {
    //     DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
    //     Handle[] handleHolder = new Handle[1];
        
    //     dbManager.inTransaction(tx -> {
    //         handleHolder[0] = tx.handle();
    //         return null;
    //     });
        
    //     return new SharedTx(handleHolder[0]);
    // }

    /**
     * Test equivalent to Go's TestSharedTx - SharedTxFunctions.
     * Tests basic SharedTx operations.
     */
    @Test
    @Execution(ExecutionMode.CONCURRENT)
    void testSharedTxFunctions() {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        
        dbManager.inTransaction(tx -> {
            SharedTx sharedTx = new SharedTx(tx.handle());
            
            // Test execute
            int result = sharedTx.execute("SELECT 1");
            assertTrue(result >= 0);
            
            // Test select
            var rows = sharedTx.select("SELECT 1 as num");
            Map<String, Object> row = rows.one();
            assertEquals(1, ((Number) row.get("num")).intValue());
            
            // Test createQuery
            SharedTxQuery query = sharedTx.createQuery("SELECT 1 as num");
            var queryResults = query.mapToMap();
            Map<String, Object> queryRow = queryResults.one();
            assertEquals(1, ((Number) queryRow.get("num")).intValue());
            
            assertEquals(1, sharedTx.getSemaphore().availablePermits());
            
            return null;
        });
    }

    /**
     * Test equivalent to Go's TestSharedTx - SharedSubTxFunctions.
     * Tests SharedSubTx operations.
     */
    @Test
    @Execution(ExecutionMode.CONCURRENT) 
    void testSharedSubTxFunctions() throws SQLException {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        
        dbManager.inTransaction(tx -> {
            SharedTx sharedTx = new SharedTx(tx.handle());
            
            try {
                SharedSubTx sharedSubTx = sharedTx.begin();
                
                // Test execute within subtransaction
                int result = sharedSubTx.execute("SELECT 1");
                assertTrue(result >= 0);
                
                // Test select within subtransaction
                var rows = sharedSubTx.select("SELECT 1 as num");
                Map<String, Object> row = rows.one();
                assertEquals(1, ((Number) row.get("num")).intValue());
                
                // Test createQuery within subtransaction
                var query = sharedSubTx.createQuery("SELECT 1 as num");
                var queryResults = query.mapToMap();
                Map<String, Object> queryRow = queryResults.one();
                assertEquals(1, ((Number) queryRow.get("num")).intValue());
                
                sharedSubTx.commit();
                
                assertEquals(1, sharedTx.getSemaphore().availablePermits());
                
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            
            return null;
        });
    }

    /**
     * Test equivalent to Go's TestSharedTx - TransactionCommitAndRollback.
     * Tests transaction commit and rollback behavior.
     */
    @Test
    @Execution(ExecutionMode.CONCURRENT)
    void testTransactionCommitAndRollback() throws SQLException {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        
        dbManager.inTransaction(tx -> {
            SharedTx sharedTx = new SharedTx(tx.handle());
            
            try {
                SharedSubTx sharedSubTx = sharedTx.begin();
                
                var rows = sharedSubTx.select("SELECT 1 as num");
                Map<String, Object> row = rows.one();
                assertEquals(1, ((Number) row.get("num")).intValue());
                
                sharedSubTx.commit();
                
                // An additional rollback should throw exception
                assertThrows(SQLException.class, () -> {
                    sharedSubTx.rollback();
                }, "Additional rollback should throw SQLException");
                
                assertEquals(1, sharedTx.getSemaphore().availablePermits());
                
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            
            return null;
        });
    }

    /**
     * Test equivalent to Go's TestSharedTx - ConcurrentUse.
     * Tests concurrent access to SharedTx.
     */
    @Test
    @Execution(ExecutionMode.CONCURRENT)
    void testConcurrentUse() throws InterruptedException {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        
        dbManager.inTransaction(tx -> {
            SharedTx sharedTx = new SharedTx(tx.handle());
            
            final int numIterations = 50;
            CountDownLatch latch = new CountDownLatch(numIterations * 2);
            ExecutorService executor = Executors.newCachedThreadPool();
            AtomicInteger errorCount = new AtomicInteger(0);
            
            try {
                // Test concurrent subtransactions
                for (int i = 0; i < numIterations; i++) {
                    executor.submit(() -> {
                        try {
                            SharedSubTx sharedSubTx = sharedTx.begin();
                            
                            var rows = sharedSubTx.select("SELECT 1 as num");
                            Map<String, Object> row = rows.one();
                            assertEquals(1, ((Number) row.get("num")).intValue());
                            
                            sharedSubTx.commit();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            e.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                
                // Test concurrent direct queries
                for (int i = 0; i < numIterations; i++) {
                    executor.submit(() -> {
                        try {
                            var rows = sharedTx.select("SELECT 1 as num");
                            Map<String, Object> row = rows.one();
                            assertEquals(1, ((Number) row.get("num")).intValue());
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            e.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                
                assertTrue(latch.await(30, TimeUnit.SECONDS), "All operations should complete within timeout");
                assertEquals(0, errorCount.get(), "No errors should occur during concurrent access");
                assertEquals(1, sharedTx.getSemaphore().availablePermits());
                
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            
            return null;
        });
    }
}