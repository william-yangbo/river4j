package io.river4j.internal.riverinternaltest.sharedtx;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.result.ResultIterable;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SharedTx can be used to wrap a test transaction in cases where multiple
 * callers may want to access it concurrently during the course of a single test
 * case. Normally this is not allowed and an access will error with connection
 * issues if another caller is already using it.
 * 
 * This is a test-only construct because in non-test environments a database
 * manager uses a full connection pool which can support concurrent access
 * without trouble. Many test cases use single test transactions, and that's
 * where code written to use a connection pool can become problematic.
 * 
 * SharedTx uses a semaphore for synchronization and does *not* guarantee FIFO
 * ordering for callers.
 * 
 * Avoid using SharedTx if possible because while it works, problems
 * encountered by use of concurrent accesses will be more difficult to debug
 * than otherwise, so it's better to not go there at all if it can be avoided.
 */
public class SharedTx {
    private final Handle inner;
    private final Semaphore semaphore;
    private final AtomicBoolean closed;

    public SharedTx(Handle handle) {
        this.inner = handle;
        this.semaphore = new Semaphore(1, true); // Fair semaphore
        this.closed = new AtomicBoolean(false);
    }

    /**
     * Begin a new sub-transaction.
     */
    public SharedSubTx begin() throws SQLException {
        lock();
        // no unlock until transaction commit/rollback

        try {
            inner.begin();
            return new SharedSubTx(this, inner);
        } catch (Exception e) {
            unlock();
            throw e;
        }
    }

    /**
     * Execute a statement. For SELECT statements, returns 0.
     * For INSERT/UPDATE/DELETE, returns the number of affected rows.
     */
    public int execute(String sql, Object... args) {
        lock();
        try {
            // Handle SELECT statements differently since JDBI's execute() doesn't support them
            String trimmedSql = sql.trim().toUpperCase();
            if (trimmedSql.startsWith("SELECT")) {
                // Just execute the query to verify it works, don't return a result
                inner.select(sql, args).mapToMap().list();
                return 0; // SELECT doesn't affect rows
            } else {
                return inner.execute(sql, args);
            }
        } finally {
            unlock();
        }
    }

    /**
     * Create an update statement.
     */
    public SharedTxUpdate createUpdate(String sql) {
        lock();
        // unlock when update is executed or closed
        return new SharedTxUpdate(this, inner.createUpdate(sql));
    }

    /**
     * Create a query statement.
     */
    public SharedTxQuery createQuery(String sql) {
        lock();
        // unlock when query is executed or closed
        return new SharedTxQuery(this, inner.createQuery(sql));
    }

    /**
     * Execute a query and return results.
     */
    public ResultIterable<Map<String, Object>> select(String sql, Object... args) {
        lock();
        try {
            return inner.select(sql, args).mapToMap();
        } finally {
            unlock();
        }
    }

    /**
     * Get the underlying handle (for direct access when needed).
     */
    public Handle getHandle() {
        return inner;
    }

    /**
     * Get the semaphore for testing purposes.
     */
    public Semaphore getSemaphore() {
        return semaphore;
    }

    void lock() {
        if (closed.get()) {
            throw new IllegalStateException("SharedTx is closed");
        }
        
        try {
            if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("sharedtx: Timed out trying to acquire lock on SharedTx");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for SharedTx lock", e);
        }
    }

    void unlock() {
        semaphore.release();
    }

    /**
     * Close the shared transaction.
     */
    public void close() {
        closed.set(true);
        if (inner != null) {
            inner.close();
        }
    }
}