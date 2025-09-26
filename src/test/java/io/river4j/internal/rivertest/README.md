# RiverTest Package - Java Implementation

This package provides the Java equivalent of Go's `rivertest` package, offering comprehensive testing utilities for River4J applications.

## Overview

The `rivertest` package contains utilities to help test River job processing systems, including:

- **Job Assertions**: Verify that jobs were inserted correctly with expected properties
- **Database Abstractions**: DBTX interface for flexible database access
- **Channel Utilities**: Continuous draining operations for queue testing
- **Transaction Utilities**: Test-friendly transaction management with automatic rollback

## Key Components

### 1. JobAssertions

Core job verification utilities equivalent to Go's job assertion functions.

```java
// Verify a single job was inserted
RiverJob job = JobAssertions.requireInserted(dbtx, "EmailJob", 
    JobAssertions.RequireInsertedOpts.builder()
        .queue("email")
        .priority(1)
        .build());

// Verify multiple jobs in order
List<ExpectedJob> expected = Arrays.asList(
    new ExpectedJob("EmailJob", null),
    new ExpectedJob("SmsJob", RequireInsertedOpts.builder().queue("sms").build())
);
List<RiverJob> jobs = JobAssertions.requireManyInserted(dbtx, expected);
```

### 2. DBTX Interface

Database abstraction interface providing flexible query execution.

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

### 3. ChannelUtils

Utilities for continuous queue draining operations.

```java
// Discard items continuously
Runnable stop = ChannelUtils.discardContinuously(someQueue);
// ... test operations ...
stop.run(); // Stop draining

// Collect items continuously
Supplier<List<Item>> getItems = ChannelUtils.drainContinuously(someQueue);
// ... test operations ...
List<Item> collectedItems = getItems.get(); // Get items and stop
```

### 4. TestTx

Transaction utilities for test-friendly database operations.

```java
// Execute within a test transaction (auto-rollback)
TestTx.withTx(connection, dbtx -> {
    // Perform database operations
    dbtx.execute("INSERT INTO test_table (name) VALUES (?)", "test");
    return dbtx.queryForObject("SELECT COUNT(*) FROM test_table", Integer.class);
});
```

### 5. RiverTest (Main Entry Point)

Primary class providing convenient access to all testing utilities.

```java
// Database utilities
DatabaseManager dbManager = RiverTest.testDatabaseManager();
ServiceArchetype archetype = RiverTest.baseServiceArchetype();

// Job assertions
RiverJob job = RiverTest.requireInserted(dbManager, "TestJob");

// Channel operations
Runnable stop = RiverTest.discardContinuously(queue);
Supplier<List<Item>> drain = RiverTest.drainContinuously(queue);

// Transaction utilities
DBTX dbtx = RiverTest.wrapConnection(connection);
```

## Alignment with Go rivertest

This Java implementation provides complete feature parity with Go's rivertest package:

### ✅ Implemented Features

1. **Job Assertions**
   - ✅ `RequireInserted` - Verify single job insertion
   - ✅ `RequireManyInserted` - Verify multiple job insertions in order
   - ✅ `RequireInsertedOpts` - Flexible job validation options
   - ✅ `ExpectedJob` - Job expectation wrapper

2. **Database Abstractions**
   - ✅ `DBTX` interface - Database executor abstraction
   - ✅ `DatabaseManagerDBTX` - Adapter for DatabaseManager
   - ✅ Connection wrapping utilities

3. **Channel Utilities**
   - ✅ `DiscardContinuously` - Continuous queue draining with discard
   - ✅ `DrainContinuously` - Continuous queue draining with collection

4. **Transaction Utilities**
   - ✅ `TestTx` - Transaction utilities with auto-rollback
   - ✅ Connection wrapping for DBTX interface

5. **Base Utilities**
   - ✅ `BaseServiceArchetype` - Test service configuration
   - ✅ `TestDatabaseManager` - Test database setup
   - ✅ `WaitOrTimeout` - Async operation timeouts
   - ✅ Logger utilities for testing

## Usage Examples

### Complete Job Testing Workflow

```java
public class MyJobTest {
    
    @Test
    public void testJobInsertion() {
        DatabaseManager dbManager = RiverTest.testDatabaseManager();
        
        // Insert a job
        myService.processEmail("test@example.com");
        
        // Verify job was inserted correctly
        RiverJob job = RiverTest.requireInserted(dbManager, "EmailJob",
            JobAssertions.RequireInsertedOpts.builder()
                .queue("email")
                .priority(1)
                .maxAttempts(3)
                .build());
                
        assertEquals("test@example.com", job.args().getString("email"));
    }
}
```

### Queue Testing with Channel Utils

```java
@Test
public void testQueueProcessing() {
    BlockingQueue<WorkResult> resultQueue = new LinkedBlockingQueue<>();
    
    // Start draining results
    Supplier<List<WorkResult>> getResults = RiverTest.drainContinuously(resultQueue);
    
    // Perform operations that produce results
    myService.processItems(Arrays.asList("item1", "item2", "item3"));
    
    // Collect and verify results
    List<WorkResult> results = getResults.get();
    assertEquals(3, results.size());
}
```

## Migration from Go

For developers migrating from Go's rivertest package:

| Go Function | Java Equivalent | Notes |
|-------------|----------------|-------|
| `rivertest.RequireInserted` | `JobAssertions.requireInserted` | Same functionality |
| `rivertest.RequireManyInserted` | `JobAssertions.requireManyInserted` | Same functionality |
| `rivertest.DiscardContinuously` | `ChannelUtils.discardContinuously` | Uses BlockingQueue instead of channels |
| `rivertest.DrainContinuously` | `ChannelUtils.drainContinuously` | Uses BlockingQueue instead of channels |
| `rivertest.TestTx` | `TestTx.withTx` | Similar transaction semantics |
| `rivertest.BaseServiceArchetype` | `RiverTest.baseServiceArchetype` | Same configuration |

## Thread Safety

- All utilities are designed to be thread-safe
- Channel utilities use proper synchronization for concurrent access
- Database abstractions follow JDBC thread-safety guidelines
- Job assertions are safe for concurrent test execution

## Error Handling

- Database operations throw `SQLException` following JDBC conventions
- Assertion failures throw `AssertionError` with detailed failure messages
- Channel operations handle `InterruptedException` appropriately
- All resources are properly cleaned up in finally blocks