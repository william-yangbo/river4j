package io.river4j.internal.riverinternaltest.sharedtx;

import org.jdbi.v3.core.result.ResultIterable;
import org.jdbi.v3.core.statement.Query;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SharedTxQuery wraps a Query such that it unlocks SharedTx when
 * the query is executed or closed.
 */
public class SharedTxQuery {
    private final SharedTx parent;
    private final Query inner;
    private final AtomicBoolean unlocked;

    public SharedTxQuery(SharedTx parent, Query query) {
        this.parent = parent;
        this.inner = query;
        this.unlocked = new AtomicBoolean(false);
    }

    private void unlockParent() {
        if (unlocked.compareAndSet(false, true)) {
            parent.unlock();
        }
    }

    public <R> ResultIterable<R> map(org.jdbi.v3.core.mapper.RowMapper<R> mapper) {
        try {
            return inner.map(mapper);
        } finally {
            unlockParent();
        }
    }

    public ResultIterable<Map<String, Object>> mapToMap() {
        try {
            return inner.mapToMap();
        } finally {
            unlockParent();
        }
    }

    public <T> ResultIterable<T> mapTo(Class<T> type) {
        try {
            return inner.mapTo(type);
        } finally {
            unlockParent();
        }
    }

    public SharedTxQuery bind(String name, Object value) {
        inner.bind(name, value);
        return this;
    }

    public SharedTxQuery bind(int position, Object value) {
        inner.bind(position, value);
        return this;
    }

    public SharedTxQuery bindList(String key, Iterable<?> values) {
        inner.bindList(key, values);
        return this;
    }

    public SharedTxQuery define(String key, Object value) {
        inner.define(key, value);
        return this;
    }

    public void close() {
        try {
            inner.close();
        } finally {
            unlockParent();
        }
    }

    /**
     * Get the underlying query for direct access.
     */
    public Query getInner() {
        return inner;
    }
}