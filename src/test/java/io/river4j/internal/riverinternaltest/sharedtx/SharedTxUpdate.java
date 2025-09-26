package io.river4j.internal.riverinternaltest.sharedtx;

import org.jdbi.v3.core.statement.Update;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SharedTxUpdate wraps an Update such that it unlocks SharedTx when
 * the update is executed or closed.
 */
public class SharedTxUpdate {
    private final SharedTx parent;
    private final Update inner;
    private final AtomicBoolean unlocked;

    public SharedTxUpdate(SharedTx parent, Update update) {
        this.parent = parent;
        this.inner = update;
        this.unlocked = new AtomicBoolean(false);
    }

    private void unlockParent() {
        if (unlocked.compareAndSet(false, true)) {
            parent.unlock();
        }
    }

    public int execute() {
        try {
            return inner.execute();
        } finally {
            unlockParent();
        }
    }

    public SharedTxUpdate bind(String name, Object value) {
        inner.bind(name, value);
        return this;
    }

    public SharedTxUpdate bind(int position, Object value) {
        inner.bind(position, value);
        return this;
    }

    public SharedTxUpdate bindList(String key, Iterable<?> values) {
        inner.bindList(key, values);
        return this;
    }

    public SharedTxUpdate define(String key, Object value) {
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
     * Get the underlying update for direct access.
     */
    public Update getInner() {
        return inner;
    }
}