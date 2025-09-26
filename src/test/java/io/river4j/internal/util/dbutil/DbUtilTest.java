package io.river4j.internal.util.dbutil;

import io.river4j.internal.database.DatabaseManager;
import io.river4j.internal.riverinternaltest.RiverInternalTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DbUtil transaction utilities.
 * This is the Java equivalent of Go's dbutil tests.
 */
public class DbUtilTest {
    
    @Test
    public void testWithTx() throws SQLException {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        DatabaseManagerExecutor executor = new DatabaseManagerExecutor(dbManager);
        
        assertDoesNotThrow(() -> {
            DbUtil.withTx(executor, connection -> {
                // Execute a simple query within transaction
                try (var stmt = connection.createStatement()) {
                    stmt.executeQuery("SELECT 1");
                }
            });
        });
    }
    
    @Test
    public void testWithTxV() throws SQLException {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        DatabaseManagerExecutor executor = new DatabaseManagerExecutor(dbManager);
        
        Integer result = DbUtil.withTxV(executor, connection -> {
            // Execute a query and return a value
            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT 7")) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        });
        
        assertEquals(7, result);
    }
    
    @Test
    public void testWithTxConnection() throws SQLException {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        
        try (Connection connection = dbManager.jdbi().open().getConnection()) {
            assertDoesNotThrow(() -> {
                DbUtil.withTx(connection, conn -> {
                    // Execute a simple query within transaction
                    try (var stmt = conn.createStatement()) {
                        stmt.executeQuery("SELECT 1");
                    }
                });
            });
        }
    }
    
    @Test
    public void testWithTxVConnection() throws SQLException {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        
        try (Connection connection = dbManager.jdbi().open().getConnection()) {
            Integer result = DbUtil.withTxV(connection, conn -> {
                // Execute a query and return a value
                try (var stmt = conn.createStatement();
                     var rs = stmt.executeQuery("SELECT 42")) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }
            });
            
            assertEquals(42, result);
        }
    }
    
    @Test
    public void testTransactionRollbackOnException() throws SQLException {
        DatabaseManager dbManager = RiverInternalTest.testDatabaseManager();
        DatabaseManagerExecutor executor = new DatabaseManagerExecutor(dbManager);
        
        // Test that exceptions cause rollback
        assertThrows(SQLException.class, () -> {
            DbUtil.withTx(executor, connection -> {
                // This should trigger a rollback
                throw new RuntimeException("Test exception");
            });
        });
    }
}