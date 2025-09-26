package io.river4j.internal.baseservice;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Test class that closely mirrors Go's base_service_test.go structure and behavior.
 * This version focuses on maintaining exact parity with the Go test cases.
 */
class BaseServiceAlignedTest {
    
    @Test
    void testInit() {
        // Direct translation of Go's TestInit
        ServiceArchetype archetype = archetype();
        
        MyService myService = BaseServiceFactory.initialize(archetype, new MyService());
        
        // Exact mirror of Go assertions
        assertTrue(myService.getBaseService().isDisableSleep());
        assertNotNull(myService.getBaseService().getLogger());
        assertEquals("MyService", myService.getBaseService().getServiceName());
        
        // Time check equivalent to Go's require.WithinDuration
        Instant now = myService.getBaseService().now();
        Instant systemNow = Instant.now();
        long diffMillis = Math.abs(now.toEpochMilli() - systemNow.toEpochMilli());
        assertTrue(diffMillis < 2000, "Time should be within 2 seconds");
    }
    
    @Test 
    void testBaseService_CancellableSleep() {
        // Direct translation of Go's TestBaseService_CancellableSleep
        ServiceArchetype archetype = archetype();
        MyService myService = BaseServiceFactory.initialize(archetype, new MyService());
        
        // Test timeout protection (equivalent to Go's goroutine with timeout)
        long testStart = System.currentTimeMillis();
        
        // Test 1: Thread interruption (Java equivalent of context cancellation)
        Thread currentThread = Thread.currentThread();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100); // Give sleep time to start
                currentThread.interrupt();
            } catch (InterruptedException ignored) {}
        });
        
        // Create service with sleep enabled to test interruption
        ServiceArchetype enabledSleepArchetype = ServiceArchetype.builder()
                .disableSleep(false)
                .logger(Logger.getLogger("MyService"))
                .timeProvider(Instant::now)
                .build();
        MyService enabledSleepService = BaseServiceFactory.initialize(enabledSleepArchetype, new MyService());
        
        long sleepStart = System.currentTimeMillis();
        enabledSleepService.getBaseService().cancellableSleep(Duration.ofSeconds(15));
        long sleepEnd = System.currentTimeMillis();
        
        // Should return early due to interruption
        assertTrue(sleepEnd - sleepStart < 5000, "Should return early when interrupted");
        Thread.interrupted(); // Clear interrupt status
        
        // Test 2: Returns immediately because DisableSleep flag is on
        sleepStart = System.currentTimeMillis();
        myService.getBaseService().cancellableSleep(Duration.ofSeconds(15));
        sleepEnd = System.currentTimeMillis();
        
        assertTrue(sleepEnd - sleepStart < 100, "Should return immediately when sleep disabled");
        
        // Ensure test doesn't take too long (Go has 5 second timeout)
        long totalTime = System.currentTimeMillis() - testStart;
        assertTrue(totalTime < 5000, "Test case took too long to run");
    }
    
    /**
     * MyService struct equivalent - mirrors Go's MyService exactly
     */
    public static class MyService implements BaseServiceProvider {
        private final BaseService baseService = new BaseService();
        
        @Override
        public BaseService getBaseService() {
            return baseService;
        }
    }
    
    /**
     * archetype() function equivalent - mirrors Go's archetype() function exactly
     */
    private ServiceArchetype archetype() {
        return ServiceArchetype.builder()
                .disableSleep(true)
                .logger(Logger.getLogger("MyService"))
                .timeProvider(Instant::now)
                .build();
    }
}