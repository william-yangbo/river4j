package io.river4j.internal.baseservice;

import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Example application demonstrating River4J BaseService usage.
 * This shows how the migrated Go baseservice functionality works in Java21.
 */
public class BaseServiceExample {
    
    public static void main(String[] args) {
        System.out.println("=== River4J BaseService Migration Example ===\n");
        
        // Create archetype for production use
        ServiceArchetype prodArchetype = ServiceArchetype.builder()
                .disableSleep(false)
                .logger(Logger.getLogger("ProductionService"))
                .timeProvider(Instant::now)
                .build();
        
        // Create archetype for testing
        ServiceArchetype testArchetype = ServiceArchetype.testArchetype();
        
        // Demonstrate production service
        System.out.println("1. Production Service Example:");
        demonstrateService(prodArchetype, "Production");
        
        System.out.println("\n2. Test Service Example (sleep disabled):");
        demonstrateService(testArchetype, "Test");
        
        System.out.println("\n3. Custom Time Provider Example:");
        demonstrateCustomTimeProvider();
        
        System.out.println("\n4. Random Operations Example:");
        demonstrateRandomOperations();
        
        System.out.println("\n=== Example completed successfully ===");
    }
    
    private static void demonstrateService(ServiceArchetype archetype, String mode) {
        // Create and initialize service
        ExampleService service = BaseServiceFactory.initialize(archetype, new ExampleService());
        
        System.out.printf("  Service Name: %s%n", service.getBaseService().getServiceName());
        System.out.printf("  Sleep Disabled: %s%n", service.getBaseService().isDisableSleep());
        System.out.printf("  Current Time: %s%n", service.getBaseService().now());
        
        // Demonstrate sleep functionality
        long startTime = System.currentTimeMillis();
        service.performWork();
        long endTime = System.currentTimeMillis();
        
        System.out.printf("  Work Duration: %d ms%n", endTime - startTime);
        
        // Cleanup
        service.getBaseService().shutdown();
    }
    
    private static void demonstrateCustomTimeProvider() {
        // Fixed time for testing
        Instant fixedTime = Instant.parse("2025-01-01T00:00:00Z");
        
        ServiceArchetype customArchetype = ServiceArchetype.builder()
                .disableSleep(true)
                .logger(Logger.getLogger("CustomTimeService"))
                .timeProvider(() -> fixedTime)
                .build();
        
        ExampleService service = BaseServiceFactory.initialize(customArchetype, new ExampleService());
        
        System.out.printf("  Fixed Time: %s%n", service.getBaseService().now());
        System.out.printf("  Matches Expected: %s%n", fixedTime.equals(service.getBaseService().now()));
        
        service.getBaseService().shutdown();
    }
    
    private static void demonstrateRandomOperations() {
        ServiceArchetype archetype = ServiceArchetype.testArchetype();
        ExampleService service = BaseServiceFactory.initialize(archetype, new ExampleService());
        
        System.out.println("  Random numbers between 1-10:");
        for (int i = 0; i < 5; i++) {
            int random = service.getBaseService().intBetween(1, 11);
            System.out.printf("    %d", random);
        }
        System.out.println();
        
        System.out.println("  Random numbers between 100-200:");
        for (int i = 0; i < 5; i++) {
            long random = service.getBaseService().longBetween(100L, 200L);
            System.out.printf("    %d", random);
        }
        System.out.println();
        
        service.getBaseService().shutdown();
    }
    
    /**
     * Example service that uses BaseService functionality
     */
    public static class ExampleService implements BaseServiceProvider {
        private final BaseService baseService = new BaseService();
        
        @Override
        public BaseService getBaseService() {
            return baseService;
        }
        
        public void performWork() {
            baseService.getLogger().info("Starting work at " + baseService.now());
            
            // Simulate some processing time
            baseService.cancellableSleep(Duration.ofMillis(100));
            
            // Random delay between operations
            baseService.cancellableSleepRandomBetween(
                Duration.ofMillis(10), 
                Duration.ofMillis(50)
            );
            
            baseService.getLogger().info("Work completed at " + baseService.now());
        }
        
        public void performAsyncWork() {
            baseService.getLogger().info("Starting async work");
            
            baseService.cancellableSleepAsync(Duration.ofMillis(200))
                .thenRun(() -> baseService.getLogger().info("Async work completed"));
        }
    }
}