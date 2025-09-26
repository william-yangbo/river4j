package com.riverqueue.river.internal.baseservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Test class for BaseService functionality.
 * This is the Java equivalent of Go's base_service_test.go
 */
class BaseServiceTest {
    
    private ServiceArchetype testArchetype;
    private TestService testService;
    
    @BeforeEach
    void setUp() {
        testArchetype = ServiceArchetype.testArchetype();
        testService = new TestService();
    }
    
    @AfterEach
    void tearDown() {
        if (testService != null && testService.getBaseService() != null) {
            testService.getBaseService().shutdown();
        }
    }
    
    @Test
    void testInit() {
        // This test mirrors the Go TestInit function exactly
        ServiceArchetype archetype = createTestArchetype();
        
        TestService myService = BaseServiceFactory.initialize(archetype, new TestService());
        
        // Mirror the Go assertions exactly
        assertTrue(myService.getBaseService().isDisableSleep(), "DisableSleep should be true");
        assertNotNull(myService.getBaseService().getLogger(), "Logger should not be null");
        assertEquals("TestService", myService.getBaseService().getServiceName(), "Service name should match");
        
        // Time assertion - ensure time provider works (equivalent to Go's WithinDuration check)
        Instant now = myService.getBaseService().now();
        Instant systemNow = Instant.now();
        assertTrue(Math.abs(now.toEpochMilli() - systemNow.toEpochMilli()) < 2000,
                  "Time should be within 2 seconds of system time");
    }
    
    @Test
    void testCancellableSleep() {
        // Given
        TestService service = BaseServiceFactory.initialize(testArchetype, new TestService());
        BaseService baseService = service.getBaseService();
        
        // Set up timeout protection similar to Go version
        long timeoutStart = System.currentTimeMillis();
        
        // Test 1: Returns immediately because `DisableSleep` flag is on
        // This mirrors the Go test behavior
        long startTime = System.currentTimeMillis();
        baseService.cancellableSleep(Duration.ofSeconds(15)); // Same as Go: 15 seconds
        long endTime = System.currentTimeMillis();
        
        // Should complete almost instantly since sleep is disabled
        assertTrue(endTime - startTime < 100, 
                  "Sleep should return immediately when disabled");
        
        // Test 2: Thread interruption (Java equivalent of context cancellation)
        Thread testThread = Thread.currentThread();
        
        // Schedule interruption after short delay
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50); // Give sleep time to start
                testThread.interrupt();
            } catch (InterruptedException ignored) {}
        });
        
        // Create service with sleep enabled for interruption test
        ServiceArchetype enabledSleepArchetype = ServiceArchetype.builder()
                .disableSleep(false)
                .logger(Logger.getLogger("TestService"))
                .build();
        TestService interruptService = BaseServiceFactory.initialize(enabledSleepArchetype, new TestService());
        
        startTime = System.currentTimeMillis();
        interruptService.getBaseService().cancellableSleep(Duration.ofSeconds(15));
        endTime = System.currentTimeMillis();
        
        // Should return early due to interruption (similar to Go's context cancellation)
        assertTrue(endTime - startTime < 5000, 
                  "Sleep should return early when thread is interrupted");
        
        // Clear interrupt status
        Thread.interrupted();
        
        // Ensure entire test doesn't take too long (Go has 5 second timeout)
        long totalTime = System.currentTimeMillis() - timeoutStart;
        assertTrue(totalTime < 5000, 
                  "Test case took too long to run (sleep statements should return quickly)");
    }
    
    @Test
    void testCancellableSleepWithEnabledSleep() {
        // Given
        ServiceArchetype enabledSleepArchetype = ServiceArchetype.builder()
                .disableSleep(false)
                .logger(Logger.getLogger("TestService"))
                .build();
        
        TestService service = BaseServiceFactory.initialize(enabledSleepArchetype, new TestService());
        BaseService baseService = service.getBaseService();
        
        // When & Then - should actually sleep for a short duration
        long startTime = System.currentTimeMillis();
        baseService.cancellableSleep(Duration.ofMillis(50));
        long endTime = System.currentTimeMillis();
        
        // Should take approximately the sleep duration
        assertTrue(endTime - startTime >= 40, 
                  "Sleep should take approximately the specified duration");
        assertTrue(endTime - startTime < 200, 
                  "Sleep should not take significantly longer than specified");
    }
    
    @Test
    void testCancellableSleepRandomBetween() {
        // Given
        TestService service = BaseServiceFactory.initialize(testArchetype, new TestService());
        BaseService baseService = service.getBaseService();
        
        // When & Then - should return immediately because sleep is disabled
        long startTime = System.currentTimeMillis();
        baseService.cancellableSleepRandomBetween(Duration.ofSeconds(1), Duration.ofSeconds(5));
        long endTime = System.currentTimeMillis();
        
        // Should complete almost instantly since sleep is disabled
        assertTrue(endTime - startTime < 100, 
                  "Random sleep should return immediately when disabled");
    }
    
    @Test
    void testIntBetween() {
        // Given
        TestService service = BaseServiceFactory.initialize(testArchetype, new TestService());
        BaseService baseService = service.getBaseService();
        
        // When & Then
        for (int i = 0; i < 100; i++) {
            int randomValue = baseService.intBetween(10, 20);
            assertTrue(randomValue >= 10, "Random value should be >= min");
            assertTrue(randomValue < 20, "Random value should be < max");
        }
    }
    
    @Test
    void testIntBetweenInvalidRange() {
        // Given
        TestService service = BaseServiceFactory.initialize(testArchetype, new TestService());
        BaseService baseService = service.getBaseService();
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            baseService.intBetween(20, 10);
        }, "Should throw exception when max <= min");
        
        assertThrows(IllegalArgumentException.class, () -> {
            baseService.intBetween(10, 10);
        }, "Should throw exception when max = min");
    }
    
    @Test
    void testTimeProvider() {
        // Given
        AtomicReference<Instant> fixedTime = new AtomicReference<>(Instant.now());
        ServiceArchetype timeArchetype = ServiceArchetype.builder()
                .disableSleep(true)
                .logger(Logger.getLogger("TestService"))
                .timeProvider(fixedTime::get)
                .build();
        
        TestService service = BaseServiceFactory.initialize(timeArchetype, new TestService());
        BaseService baseService = service.getBaseService();
        
        // When
        Instant currentTime = baseService.now();
        
        // Then
        assertEquals(fixedTime.get(), currentTime, "Should use the configured time provider");
        
        // Update the fixed time and verify it changes
        Instant newTime = Instant.now().plusSeconds(3600);
        fixedTime.set(newTime);
        
        assertEquals(newTime, baseService.now(), "Should reflect time provider changes");
    }
    
    @Test
    void testServiceArchetypeValidation() {
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            new ServiceArchetype(false, null, Instant::now);
        }, "Should reject null logger");
        
        assertThrows(NullPointerException.class, () -> {
            new ServiceArchetype(false, Logger.getLogger("Test"), null);
        }, "Should reject null time provider");
    }
    
    @Test
    void testBaseServiceFactoryValidation() {
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            BaseServiceFactory.initialize(null, new TestService());
        }, "Should reject null archetype");
        
        assertThrows(NullPointerException.class, () -> {
            BaseServiceFactory.initialize(testArchetype, null);
        }, "Should reject null service");
    }
    
    @Test
    void testAsyncSleep() {
        // Given
        TestService service = BaseServiceFactory.initialize(testArchetype, new TestService());
        BaseService baseService = service.getBaseService();
        
        // When
        var future = baseService.cancellableSleepAsync(Duration.ofSeconds(5));
        
        // Then
        assertTrue(future.isDone(), "Async sleep should complete immediately when sleep is disabled");
        assertDoesNotThrow(() -> future.get(), "Future should complete successfully");
    }
    
    /**
     * Creates test archetype - mirrors the Go archetype() function
     */
    private ServiceArchetype createTestArchetype() {
        return ServiceArchetype.builder()
                .disableSleep(true)
                .logger(Logger.getLogger("TestService"))
                .timeProvider(Instant::now)
                .build();
    }
}