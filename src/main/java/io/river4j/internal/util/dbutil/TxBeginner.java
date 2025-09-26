package io.river4j.internal.util.dbutil;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * TxBeginner is an interface to a type that can begin a transaction, like a 
 * connection pool, connection, or transaction (the latter would begin a 
 * subtransaction).
 * This is the Java equivalent of Go's dbutil.TxBeginner interface.
 */
public interface TxBeginner {
    
    /**
     * Begin a new transaction.
     * 
     * @return a new Connection in transaction mode (autoCommit=false)
     * @throws SQLException if transaction cannot be started
     */
    Connection beginTx() throws SQLException;
}