package io.river4j.internal.util.dbutil;

import io.river4j.internal.database.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * DatabaseManagerExecutor adapts DatabaseManager to implement the Executor interface.
 * This provides a bridge between JDBI-based DatabaseManager and the dbutil interfaces.
 */
public class DatabaseManagerExecutor implements Executor {
    
    private final DatabaseManager databaseManager;
    
    public DatabaseManagerExecutor(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    @Override
    public Connection beginTx() throws SQLException {
        Connection connection = databaseManager.jdbi().open().getConnection();
        connection.setAutoCommit(false);
        return connection;
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return databaseManager.jdbi().open().getConnection();
    }
    
    @Override
    public int execute(String sql, Object... params) throws SQLException {
        try {
            return databaseManager.jdbi().withHandle(handle -> {
                var update = handle.createUpdate(sql);
                for (int i = 0; i < params.length; i++) {
                    update.bind(i, params[i]);
                }
                return update.execute();
            });
        } catch (Exception e) {
            throw new SQLException("Error executing update", e);
        }
    }
    
    @Override
    public <T> List<T> query(String sql, Class<T> clazz, Object... params) throws SQLException {
        try {
            return databaseManager.jdbi().withHandle(handle -> {
                var query = handle.createQuery(sql);
                for (int i = 0; i < params.length; i++) {
                    query.bind(i, params[i]);
                }
                return query.mapTo(clazz).list();
            });
        } catch (Exception e) {
            throw new SQLException("Error executing query", e);
        }
    }
    
    @Override
    public <T> T queryForObject(String sql, Class<T> clazz, Object... params) throws SQLException {
        List<T> results = query(sql, clazz, params);
        return results.isEmpty() ? null : results.get(0);
    }
    
    @Override
    public List<Map<String, Object>> queryForList(String sql, Object... params) throws SQLException {
        try {
            return databaseManager.jdbi().withHandle(handle -> {
                var query = handle.createQuery(sql);
                for (int i = 0; i < params.length; i++) {
                    query.bind(i, params[i]);
                }
                return query.mapToMap().list();
            });
        } catch (Exception e) {
            throw new SQLException("Error executing query for list", e);
        }
    }
    
    @Override
    public Map<String, Object> queryForMap(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> results = queryForList(sql, params);
        return results.isEmpty() ? null : results.get(0);
    }
}