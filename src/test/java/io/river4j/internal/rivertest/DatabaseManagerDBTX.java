package io.river4j.internal.rivertest;

import io.river4j.internal.database.DatabaseManager;
import io.river4j.internal.database.model.RiverJob;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * DatabaseManagerDBTX adapts DatabaseManager to implement the DBTX interface.
 * This allows rivertest assertions to work with DatabaseManager instances.
 */
public class DatabaseManagerDBTX implements DBTX {
    
    private final DatabaseManager databaseManager;
    
    public DatabaseManagerDBTX(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    @Override
    public int execute(String sql, Object... params) throws SQLException {
        try {
            return databaseManager.jdbi().withHandle(handle -> 
                handle.createUpdate(sql)
                    .bindArray(0, params)
                    .execute()
            );
        } catch (Exception e) {
            throw new SQLException("Failed to execute statement", e);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> query(String sql, Class<T> type, Object... params) throws SQLException {
        try {
            if (type == RiverJob.class) {
                // Special handling for RiverJob queries
                return (List<T>) databaseManager.jdbi().withHandle(handle -> 
                    handle.createQuery(sql)
                        .bindArray(0, params)
                        .mapToBean(RiverJob.class)
                        .list()
                );
            } else {
                return databaseManager.jdbi().withHandle(handle -> 
                    handle.createQuery(sql)
                        .bindArray(0, params)
                        .mapToBean(type)
                        .list()
                );
            }
        } catch (Exception e) {
            throw new SQLException("Failed to execute query", e);
        }
    }
    
    @Override
    public <T> T queryForObject(String sql, Class<T> type, Object... params) throws SQLException {
        try {
            List<T> results = query(sql, type, params);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            throw new SQLException("Failed to execute query for object", e);
        }
    }
    
    @Override
    public List<Map<String, Object>> queryForList(String sql, Object... params) throws SQLException {
        try {
            return databaseManager.jdbi().withHandle(handle -> 
                handle.createQuery(sql)
                    .bindArray(0, params)
                    .mapToMap()
                    .list()
            );
        } catch (Exception e) {
            throw new SQLException("Failed to execute query for list", e);
        }
    }
    
    @Override
    public Map<String, Object> queryForMap(String sql, Object... params) throws SQLException {
        try {
            List<Map<String, Object>> results = queryForList(sql, params);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            throw new SQLException("Failed to execute query for map", e);
        }
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        try {
            return databaseManager.jdbi().withHandle(handle -> 
                handle.getConnection()
            );
        } catch (Exception e) {
            throw new SQLException("Failed to get connection", e);
        }
    }
}