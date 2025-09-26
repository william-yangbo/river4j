package io.river4j.internal.rivertest;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * TestTx provides transaction utilities for testing.
 * This provides the Java equivalent of Go's TestTx functionality.
 */
public class TestTx {

    /**
     * Creates a DBTX wrapper around a given connection.
     * 
     * @param connection the database connection to wrap
     * @return a DBTX instance
     */
    public static DBTX wrap(Connection connection) {
        return new TestDBTX(connection);
    }
    
    /**
     * Begins a transaction and returns a DBTX that can be used to execute queries
     * within that transaction. The transaction is automatically rolled back when done.
     * 
     * This is equivalent to Go's TestTx functionality.
     * 
     * @param connection the database connection to use
     * @param callback function to execute within the transaction
     * @return the result of the callback function
     * @throws SQLException if database operation fails
     */
    public static <T> T withTx(Connection connection, Function<DBTX, T> callback) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            DBTX dbtx = new TestDBTX(connection);
            T result = callback.apply(dbtx);
            connection.rollback(); // Always rollback for testing
            return result;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
    
    /**
     * Simple test DBTX implementation using a JDBC Connection.
     */
    private static class TestDBTX implements DBTX {
        private final Connection connection;
        
        public TestDBTX(Connection connection) {
            this.connection = connection;
        }
        
        @Override
        public Connection getConnection() throws SQLException {
            return connection;
        }
        
        @Override
        public int execute(String sql, Object... params) throws SQLException {
            try (var stmt = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                return stmt.executeUpdate();
            }
        }
        
        @Override
        public <T> List<T> query(String sql, Class<T> clazz, Object... params) throws SQLException {
            List<T> results = new java.util.ArrayList<>();
            try (var stmt = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        T result = rs.getObject(1, clazz);
                        results.add(result);
                    }
                }
            }
            return results;
        }
        
        @Override
        public <T> T queryForObject(String sql, Class<T> clazz, Object... params) throws SQLException {
            List<T> results = query(sql, clazz, params);
            if (results.isEmpty()) {
                return null;
            }
            return results.get(0);
        }
        
        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... params) throws SQLException {
            List<Map<String, Object>> results = new java.util.ArrayList<>();
            try (var stmt = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                try (var rs = stmt.executeQuery()) {
                    var metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    
                    while (rs.next()) {
                        Map<String, Object> row = new java.util.HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            row.put(columnName, value);
                        }
                        results.add(row);
                    }
                }
            }
            return results;
        }
        
        @Override
        public Map<String, Object> queryForMap(String sql, Object... params) throws SQLException {
            List<Map<String, Object>> results = queryForList(sql, params);
            if (results.isEmpty()) {
                return null;
            }
            return results.get(0);
        }
    }
}