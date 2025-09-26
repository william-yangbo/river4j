package io.river4j.internal.util.dbutil;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * DbUtil provides database transaction utilities.
 * This is the Java equivalent of Go's dbutil package functions.
 */
public class DbUtil {
    
    /**
     * Functional interface for operations that don't return a value.
     */
    @FunctionalInterface
    public interface TxOperation {
        void execute(Connection connection) throws SQLException;
    }
    
    /**
     * Functional interface for operations that return a value.
     */
    @FunctionalInterface
    public interface TxFunction<T> {
        T execute(Connection connection) throws SQLException;
    }
    
    /**
     * WithTx starts and commits a transaction around the given function.
     * This is the Java equivalent of Go's dbutil.WithTx function.
     * 
     * @param txBeginner the transaction beginner (connection pool, etc.)
     * @param operation the operation to execute within the transaction
     * @throws SQLException if any database operation fails
     */
    public static void withTx(TxBeginner txBeginner, TxOperation operation) throws SQLException {
        withTxV(txBeginner, connection -> {
            operation.execute(connection);
            return null;
        });
    }
    
    /**
     * WithTxV starts and commits a transaction around the given function, allowing
     * the return of a generic value.
     * This is the Java equivalent of Go's dbutil.WithTxV function.
     * 
     * @param txBeginner the transaction beginner (connection pool, etc.)
     * @param function the function to execute within the transaction
     * @return the result of the function
     * @throws SQLException if any database operation fails
     */
    public static <T> T withTxV(TxBeginner txBeginner, TxFunction<T> function) throws SQLException {
        Connection tx = txBeginner.beginTx();
        try {
            T result = function.execute(tx);
            tx.commit();
            return result;
        } catch (Exception e) {
            try {
                tx.rollback();
            } catch (SQLException rollbackException) {
                e.addSuppressed(rollbackException);
            }
            if (e instanceof SQLException) {
                throw (SQLException) e;
            } else {
                throw new SQLException("Error executing transaction function", e);
            }
        } finally {
            try {
                tx.close();
            } catch (SQLException closeException) {
                // Log but don't throw - connection cleanup failure shouldn't mask original error
                // In a real implementation, you'd use a proper logger here
                System.err.println("Warning: Failed to close transaction connection: " + closeException.getMessage());
            }
        }
    }
    
    /**
     * Convenience method that works with raw Connections.
     * Useful when you already have a connection and want transaction semantics.
     */
    public static void withTx(Connection connection, TxOperation operation) throws SQLException {
        withTxV(connection, conn -> {
            operation.execute(conn);
            return null;
        });
    }
    
    /**
     * Convenience method that works with raw Connections and returns a value.
     * Useful when you already have a connection and want transaction semantics.
     */
    public static <T> T withTxV(Connection connection, TxFunction<T> function) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            T result = function.execute(connection);
            connection.commit();
            return result;
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                e.addSuppressed(rollbackException);
            }
            if (e instanceof SQLException) {
                throw (SQLException) e;
            } else {
                throw new SQLException("Error executing transaction function", e);
            }
        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException restoreException) {
                // Log but don't throw - this shouldn't mask original error
                System.err.println("Warning: Failed to restore autoCommit setting: " + restoreException.getMessage());
            }
        }
    }
}