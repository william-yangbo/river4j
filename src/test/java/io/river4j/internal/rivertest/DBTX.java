package io.river4j.internal.rivertest;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * DBTX is a database-like executor interface implemented by Connection, DataSource, 
 * and transaction wrappers. It's used to let rivertest assertions be as flexible 
 * as possible in what database argument they can take.
 * 
 * This is the Java equivalent of the Go DBTX interface.
 */
public interface DBTX {
    
    /**
     * Execute a SQL statement and return the number of affected rows.
     * 
     * @param sql the SQL statement
     * @param params the parameters
     * @return number of affected rows
     * @throws SQLException if execution fails
     */
    int execute(String sql, Object... params) throws SQLException;
    
    /**
     * Query for a list of objects of the specified type.
     * 
     * @param sql the SQL query
     * @param type the target class type
     * @param params the parameters
     * @return list of mapped objects
     * @throws SQLException if query fails
     */
    <T> List<T> query(String sql, Class<T> type, Object... params) throws SQLException;
    
    /**
     * Query for a single object of the specified type.
     * 
     * @param sql the SQL query
     * @param type the target class type
     * @param params the parameters
     * @return the mapped object, or null if not found
     * @throws SQLException if query fails
     */
    <T> T queryForObject(String sql, Class<T> type, Object... params) throws SQLException;
    
    /**
     * Query for a list of maps (key-value pairs).
     * 
     * @param sql the SQL query
     * @param params the parameters
     * @return list of maps representing rows
     * @throws SQLException if query fails
     */
    List<Map<String, Object>> queryForList(String sql, Object... params) throws SQLException;
    
    /**
     * Query for a single map (key-value pairs).
     * 
     * @param sql the SQL query
     * @param params the parameters
     * @return a map representing the row, or null if not found
     * @throws SQLException if query fails
     */
    Map<String, Object> queryForMap(String sql, Object... params) throws SQLException;
    
    /**
     * Get the underlying connection for advanced operations.
     * 
     * @return the database connection
     * @throws SQLException if connection retrieval fails
     */
    Connection getConnection() throws SQLException;
}