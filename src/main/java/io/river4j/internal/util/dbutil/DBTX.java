package io.river4j.internal.util.dbutil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * DBTX is a database transaction interface for executing queries and commands.
 * This is the Java equivalent of Go's dbsqlc.DBTX interface.
 */
public interface DBTX {
    
    /**
     * Get the underlying database connection.
     */
    Connection getConnection() throws SQLException;
    
    /**
     * Execute a SQL statement and return the number of affected rows.
     */
    int execute(String sql, Object... params) throws SQLException;
    
    /**
     * Query for a list of objects of the specified type.
     */
    <T> List<T> query(String sql, Class<T> clazz, Object... params) throws SQLException;
    
    /**
     * Query for a single object of the specified type.
     */
    <T> T queryForObject(String sql, Class<T> clazz, Object... params) throws SQLException;
    
    /**
     * Query for a list of maps (column name -> value).
     */
    List<Map<String, Object>> queryForList(String sql, Object... params) throws SQLException;
    
    /**
     * Query for a single map (column name -> value).
     */
    Map<String, Object> queryForMap(String sql, Object... params) throws SQLException;
}