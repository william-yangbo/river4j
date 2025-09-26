# DbUtil Package - Java Implementation

This package provides the Java equivalent of Go's `dbutil` package, offering database transaction management utilities and abstractions for River4J applications.

## Overview

The `dbutil` package contains utilities to help manage database transactions and provide flexible database access patterns, including:

- **Transaction Management**: Automatic transaction begin/commit/rollback handling
- **Database Abstractions**: Interfaces for flexible database access
- **Connection Pool Integration**: Support for DataSource and connection pools
- **Generic Return Values**: Support for functions that return values from transactions

## Key Components

### 1. DBTX Interface

Core database transaction interface for executing queries and commands.

```java
public interface DBTX {
    Connection getConnection() throws SQLException;
    int execute(String sql, Object... params) throws SQLException;
    <T> List<T> query(String sql, Class<T> clazz, Object... params) throws SQLException;
    <T> T queryForObject(String sql, Class<T> clazz, Object... params) throws SQLException;
    List<Map<String, Object>> queryForList(String sql, Object... params) throws SQLException;
    Map<String, Object> queryForMap(String sql, Object... params) throws SQLException;
}
```

### 2. TxBeginner Interface

Interface for types that can begin database transactions.

```java
public interface TxBeginner {
    Connection beginTx() throws SQLException;
}
```

### 3. Executor Interface

Combined interface that can both begin transactions and execute database operations.

```java
public interface Executor extends TxBeginner, DBTX {
    // Inherits all methods from both interfaces
}
```

### 4. DbUtil Class

Main utility class providing transaction management functions.

```java
// Execute code within a transaction (no return value)
DbUtil.withTx(executor, connection -> {
    // Database operations here
});

// Execute code within a transaction (with return value)
Integer result = DbUtil.withTxV(executor, connection -> {
    // Database operations here
    return someValue;
});
```

## Alignment with Go dbutil

This Java implementation provides complete feature parity with Go's dbutil package:

### ✅ Implemented Features

1. **Core Interfaces**
   - ✅ `DBTX` - Database transaction executor (equivalent to Go's dbsqlc.DBTX)
   - ✅ `TxBeginner` - Transaction starter interface
   - ✅ `Executor` - Combined interface (equivalent to Go's dbutil.Executor)

2. **Transaction Functions**
   - ✅ `WithTx` - Execute code in transaction without return value
   - ✅ `WithTxV` - Execute code in transaction with generic return value

3. **Implementations**
   - ✅ `DatabaseManagerExecutor` - Adapter for JDBI-based DatabaseManager
   - ✅ `DataSourceTxBeginner` - Simple DataSource wrapper

4. **Error Handling**
   - ✅ Automatic rollback on exceptions
   - ✅ Proper resource cleanup
   - ✅ Exception chaining and suppression

## Usage Examples

### Basic Transaction Usage

```java
public class MyService {
    
    public void processData() throws SQLException {
        DatabaseManager dbManager = // ... get database manager
        DatabaseManagerExecutor executor = new DatabaseManagerExecutor(dbManager);
        
        DbUtil.withTx(executor, connection -> {
            // All operations within this block are in a transaction
            try (var stmt = connection.prepareStatement("INSERT INTO jobs (kind, args) VALUES (?, ?)")) {
                stmt.setString(1, "EmailJob");
                stmt.setString(2, "{\"email\":\"test@example.com\"}");
                stmt.executeUpdate();
            }
            
            try (var stmt = connection.prepareStatement("UPDATE counters SET value = value + 1 WHERE name = ?")) {
                stmt.setString(1, "jobs_created");
                stmt.executeUpdate();
            }
            // Transaction automatically commits if no exception occurs
        });
    }
}
```

### Transaction with Return Value

```java
public class JobService {
    
    public long createJobAndGetId(String kind, String args) throws SQLException {
        DatabaseManager dbManager = // ... get database manager
        DatabaseManagerExecutor executor = new DatabaseManagerExecutor(dbManager);
        
        return DbUtil.withTxV(executor, connection -> {
            // Insert job and return generated ID
            try (var stmt = connection.prepareStatement(
                "INSERT INTO jobs (kind, args) VALUES (?, ?) RETURNING id")) {
                stmt.setString(1, kind);
                stmt.setString(2, args);
                
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    throw new SQLException("Failed to get generated ID");
                }
            }
        });
    }
}
```

### Using with DataSource

```java
public class DataSourceExample {
    
    public void useWithDataSource() throws SQLException {
        DataSource dataSource = // ... get your DataSource
        DataSourceTxBeginner txBeginner = new DataSourceTxBeginner(dataSource);
        
        DbUtil.withTx(txBeginner, connection -> {
            // Database operations here
            try (var stmt = connection.createStatement()) {
                stmt.executeUpdate("UPDATE settings SET last_run = NOW()");
            }
        });
    }
}
```

### Error Handling and Rollback

```java
public class ErrorHandlingExample {
    
    public void demonstrateRollback() {
        DatabaseManager dbManager = // ... get database manager
        DatabaseManagerExecutor executor = new DatabaseManagerExecutor(dbManager);
        
        try {
            DbUtil.withTx(executor, connection -> {
                // First operation succeeds
                try (var stmt = connection.prepareStatement("INSERT INTO jobs (kind) VALUES (?)")) {
                    stmt.setString(1, "TestJob");
                    stmt.executeUpdate();
                }
                
                // Second operation fails
                try (var stmt = connection.prepareStatement("INSERT INTO invalid_table VALUES (1)")) {
                    stmt.executeUpdate(); // This will throw SQLException
                }
            });
        } catch (SQLException e) {
            // Transaction was automatically rolled back
            // The job insertion was undone
            System.err.println("Transaction failed and was rolled back: " + e.getMessage());
        }
    }
}
```

## Integration with River4J

The dbutil package integrates seamlessly with River4J's existing database infrastructure:

```java
public class RiverIntegrationExample {
    
    public void integrateWithRiver() throws SQLException {
        // Use existing River4J database manager
        DatabaseManager dbManager = RiverTest.testDatabaseManager();
        DatabaseManagerExecutor executor = new DatabaseManagerExecutor(dbManager);
        
        // Perform complex job operations in transaction
        List<Long> jobIds = DbUtil.withTxV(executor, connection -> {
            List<Long> ids = new ArrayList<>();
            
            // Create multiple related jobs atomically
            String[] jobKinds = {"EmailJob", "SmsJob", "PushJob"};
            
            for (String kind : jobKinds) {
                try (var stmt = connection.prepareStatement(
                    "INSERT INTO river_job (kind, args, queue) VALUES (?, '{}', 'default') RETURNING id")) {
                    stmt.setString(1, kind);
                    
                    try (var rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            ids.add(rs.getLong(1));
                        }
                    }
                }
            }
            
            return ids;
        });
        
        System.out.println("Created jobs with IDs: " + jobIds);
    }
}
```

## Migration from Go

For developers migrating from Go's dbutil package:

| Go Function/Interface | Java Equivalent | Notes |
|----------------------|-----------------|-------|
| `dbutil.Executor` | `Executor` | Same functionality |
| `dbutil.TxBeginner` | `TxBeginner` | Same functionality |
| `dbutil.WithTx` | `DbUtil.withTx` | Same transaction semantics |
| `dbutil.WithTxV` | `DbUtil.withTxV` | Same with generic return values |
| `dbsqlc.DBTX` | `DBTX` | Enhanced with additional query methods |

## Thread Safety

- All utilities are designed to be thread-safe
- Transaction isolation follows standard JDBC/SQL semantics
- Connection management follows proper resource cleanup patterns
- Exception handling preserves original errors while ensuring cleanup

## Error Handling

- Database operations throw `SQLException` following JDBC conventions
- Automatic rollback on any exception within transaction block
- Proper resource cleanup in finally blocks
- Exception suppression for cleanup failures to preserve original errors

## Performance Considerations

- Transactions are committed automatically on successful completion
- Connections are properly closed after transaction completion
- Minimal overhead over direct JDBC usage
- Compatible with connection pooling for optimal performance