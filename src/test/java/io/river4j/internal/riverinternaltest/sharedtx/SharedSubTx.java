package io.river4j.internal.riverinternaltest.sharedtx;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.result.ResultIterable;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.Update;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SharedSubTx wraps a Handle such that it unlocks SharedTx when it commits or
 * rolls back.
 */
public class SharedSubTx {
    private final SharedTx parent;
    private final Handle inner;
    private final AtomicBoolean committed;

    public SharedSubTx(SharedTx parent, Handle handle) {
        this.parent = parent;
        this.inner = handle;
        this.committed = new AtomicBoolean(false);
    }

    /**
     * Execute an update statement.
     */
    public int execute(String sql, Object... args) {
        // Handle SELECT statements differently since JDBI's execute() doesn't support them
        String trimmedSql = sql.trim().toUpperCase();
        if (trimmedSql.startsWith("SELECT")) {
            // Just execute the query to verify it works, don't return a result
            inner.select(sql, args).mapToMap().list();
            return 0; // SELECT doesn't affect rows
        } else {
            return inner.execute(sql, args);
        }
    }

    /**
     * Create an update statement.
     */
    public Update createUpdate(String sql) {
        return inner.createUpdate(sql);
    }

    /**
     * Create a query statement.
     */
    public Query createQuery(String sql) {
        return inner.createQuery(sql);
    }

    /**
     * Execute a query and return results.
     */
    public ResultIterable<Map<String, Object>> select(String sql, Object... args) {
        return inner.select(sql, args).mapToMap();
    }

    /**
     * Commit the transaction.
     */
    public void commit() throws SQLException {
        if (committed.compareAndSet(false, true)) {
            try {
                inner.commit();
            } finally {
                parent.unlock();
            }
        }
    }

    /**
     * Rollback the transaction.
     */
    public void rollback() throws SQLException {
        if (committed.compareAndSet(false, true)) {
            try {
                inner.rollback();
            } finally {
                parent.unlock();
            }
        } else {
            // Additional rollback after commit/rollback - should throw exception
            throw new SQLException("Transaction is already closed");
        }
    }

    /**
     * Get the underlying handle.
     */
    public Handle getHandle() {
        return inner;
    }
}