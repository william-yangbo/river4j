# River4J BaseService Module

This module contains the Java21 migration of River4Go's baseservice functionality. The baseservice module provides common infrastructure for service-like objects in the River queue system.

## Overview

The baseservice module has been successfully migrated from Go to Java21, maintaining all core functionality while leveraging Java21's modern features.

## Architecture

### Key Components

1. **ServiceArchetype** (Record) - Immutable configuration container
2. **BaseService** - Core service functionality provider  
3. **BaseServiceProvider** - Interface for services that embed BaseService
4. **BaseServiceFactory** - Factory for service initialization

### Migration Mapping

| Go Component | Java21 Component | Notes |
|--------------|------------------|-------|
| `Archetype` struct | `ServiceArchetype` record | Uses Java21 record for immutability |
| `BaseService` struct | `BaseService` class | Composition instead of embedding |
| `withBaseService` interface | `BaseServiceProvider` interface | Type-safe service provider |
| `Init[T]` function | `BaseServiceFactory.initialize()` | Generic factory method |
| `CancellableSleep` | `cancellableSleep()` | Thread interruption based |
| `randutil.NewCryptoSeededRand()` | `ThreadLocalRandom` | Java's built-in thread-safe random |

## Features

### Core Functionality
- ✅ Configurable sleep operations with cancellation support
- ✅ Thread-safe random number generation
- ✅ Structured logging integration
- ✅ Customizable time providers for testing
- ✅ Service name auto-detection from class names
- ✅ Immutable configuration via records

### Java21 Enhancements
- ✅ Virtual threads for async operations
- ✅ Record-based configuration
- ✅ Pattern matching ready architecture
- ✅ Enhanced type safety
- ✅ Cleaner API design

## Usage Example

```java
### Usage Example

```java
import io.river4j.internal.baseservice.*;

// Create service archetype
ServiceArchetype archetype = ServiceArchetype.builder()
    .disableSleep(false)
    .logger(Logger.getLogger(MyService.class.getName()))
    .timeProvider(Instant::now)
    .build();

// Create and initialize service
MyService service = BaseServiceFactory.initialize(archetype, new MyService());

// Use service functionality
service.performWork();

// Service implementation
public class MyService implements BaseServiceProvider {
    private final BaseService baseService = new BaseService();
    
    @Override
    public BaseService getBaseService() {
        return baseService;
    }
    
    public void performWork() {
        // Use baseService functionality
        baseService.getLogger().info("Starting work");
        baseService.cancellableSleep(Duration.ofMillis(100));
        baseService.getLogger().info("Work completed");
    }
}
```
```

## Testing

### Test Configuration
```java
// Create test archetype with sleep disabled
ServiceArchetype testArchetype = ServiceArchetype.testArchetype();
MyService service = BaseServiceFactory.initialize(testArchetype, new MyService());

// All sleep operations return immediately in tests
service.performWork(); // Completes instantly
```

### Custom Time Provider
```java
// Fixed time for deterministic tests
Instant fixedTime = Instant.parse("2025-01-01T00:00:00Z");
ServiceArchetype archetype = ServiceArchetype.builder()
    .timeProvider(() -> fixedTime)
    .build();
```

## Building and Running

### Prerequisites
- Java 21 or later
- Maven 3.9+

### Build
```bash
cd river4j
mvn clean compile
```

### Run Tests
```bash
mvn test
```

### Run Example
```bash
mvn exec:java -Dexec.mainClass="io.river4j.internal.baseservice.BaseServiceExample"
```

## Migration Benefits

### Performance
- Virtual threads for better concurrency
- ThreadLocalRandom for high-performance random generation
- Reduced object allocation with records

### Developer Experience  
- Compile-time type safety
- IDE support and code completion
- Immutable configuration prevents bugs
- Clear separation of concerns

### Maintainability
- Modern Java idioms
- Comprehensive test coverage
- Self-documenting code with records
- Consistent error handling

## Implementation Notes

### Design Decisions

1. **Composition over Inheritance**: Java services contain a BaseService instance rather than extending it, following Java best practices.

2. **Record-based Configuration**: ServiceArchetype uses Java21 records for immutable, validated configuration.

3. **Interface-based Design**: BaseServiceProvider interface enables type-safe service initialization.

4. **Thread Safety**: All components are designed for concurrent access using Java's proven patterns.

### Differences from Go Version

1. **Sleep Cancellation**: Uses Thread.interrupt() instead of Go's context cancellation.

2. **Random Generation**: Uses ThreadLocalRandom instead of custom crypto-seeded implementation.

3. **Service Discovery**: Uses reflection for service name extraction instead of Go's type system.

4. **Error Handling**: Uses Java exceptions instead of Go's error values.

## Future Enhancements

- [ ] Integration with Spring Boot
- [ ] Metrics collection support  
- [ ] Distributed tracing integration
- [ ] Configuration externalization
- [ ] Health check endpoints
- [ ] Graceful shutdown coordination

## Contributing

This module follows standard Java21 development practices:
- Use records for immutable data
- Leverage virtual threads for concurrency  
- Follow conventional Maven project structure
- Maintain comprehensive test coverage
- Document public APIs with Javadoc