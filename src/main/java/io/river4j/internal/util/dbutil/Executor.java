package io.river4j.internal.util.dbutil;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Executor is an interface for a type that can begin a transaction and also
 * perform all the operations needed to be used in conjunction with database queries.
 * This is the Java equivalent of Go's dbutil.Executor interface.
 */
public interface Executor extends TxBeginner, DBTX {
    
    /**
     * Get the underlying connection for direct access when needed.
     * This provides compatibility with existing DBTX interface.
     */
    @Override
    Connection getConnection() throws SQLException;
}