package io.river4j.internal.baseservice;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test service implementation for testing BaseService functionality.
 * This is the Java equivalent of Go's MyService test struct.
 */
public class TestService implements BaseServiceProvider {
    private final BaseService baseService;
    private final AtomicInteger operationCount = new AtomicInteger(0);
    
    public TestService() {
        this.baseService = new BaseService();
    }
    
    public TestService(BaseService baseService) {
        this.baseService = baseService;
    }
    
    @Override
    public BaseService getBaseService() {
        return baseService;
    }
    
    /**
     * Example service operation that uses BaseService functionality
     */
    public void performOperation() {
        baseService.getLogger().info("Performing operation " + operationCount.incrementAndGet());
        
        // Simulate some work with a short sleep
        baseService.cancellableSleep(Duration.ofMillis(10));
    }
    
    /**
     * Example operation that performs random sleep
     */
    public void performRandomOperation() {
        baseService.getLogger().info("Performing random operation");
        
        // Sleep for a random duration between 5-15ms
        baseService.cancellableSleepRandomBetween(
            Duration.ofMillis(5), 
            Duration.ofMillis(15)
        );
    }
    
    /**
     * Returns the current operation count
     */
    public int getOperationCount() {
        return operationCount.get();
    }
    
    /**
     * Gets current time using the service's time provider
     */
    public Instant getCurrentTime() {
        return baseService.now();
    }
}