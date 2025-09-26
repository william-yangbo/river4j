package com.riverqueue.river.internal.baseservice;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 * BaseService provides common functionality for service-like objects.
 * It's designed to be embedded in other service classes to provide
 * shared capabilities like logging, time functions, and sleep operations.
 * 
 * This is the Java21 equivalent of Go's BaseService struct.
 */
public final class BaseService {
    private ServiceArchetype archetype;
    private String serviceName;
    private final ThreadLocalRandom random;
    private final ExecutorService virtualThreadExecutor;
    private volatile boolean initialized = false;
    
    /**
     * Creates a new BaseService with the given archetype and service name
     */
    public BaseService(ServiceArchetype archetype, String serviceName) {
        this.archetype = archetype;
        this.serviceName = serviceName;
        this.random = ThreadLocalRandom.current();
        // Java21 virtual threads for async operations
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.initialized = true;
    }
    
    /**
     * Default constructor - requires initialization via BaseServiceFactory
     */
    public BaseService() {
        this.archetype = null; // Will be set during initialization
        this.serviceName = null; // Will be set during initialization
        this.random = ThreadLocalRandom.current();
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.initialized = false;
    }
    
    // Package-private initialization method used by BaseServiceFactory
    void initialize(ServiceArchetype archetype, String serviceName) {
        if (this.initialized) {
            throw new IllegalStateException("BaseService already initialized");
        }
        this.archetype = archetype;
        this.serviceName = serviceName;
        this.initialized = true;
    }
    
    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("BaseService not initialized. Use BaseServiceFactory.initialize() first.");
        }
    }
    
    /**
     * Returns the service archetype
     */
    public ServiceArchetype getArchetype() {
        ensureInitialized();
        return archetype;
    }
    
    /**
     * Returns the service name
     */
    public String getServiceName() {
        ensureInitialized();
        return serviceName;
    }
    
    /**
     * Returns the logger from the archetype
     */
    public Logger getLogger() {
        ensureInitialized();
        return archetype.logger();
    }
    
    /**
     * Returns current time using the configured time provider
     */
    public Instant now() {
        ensureInitialized();
        return archetype.timeProvider().get();
    }
    
    /**
     * Returns whether sleep operations are disabled
     */
    public boolean isDisableSleep() {
        ensureInitialized();
        return archetype.disableSleep();
    }
    
    /**
     * Sleeps for the given duration, but can be interrupted.
     * If disableSleep is true, returns immediately.
     * 
     * This is the Java equivalent of Go's CancellableSleep.
     */
    public void cancellableSleep(Duration sleepDuration) {
        ensureInitialized();
        if (archetype.disableSleep()) {
            return;
        }
        
        try {
            Thread.sleep(sleepDuration.toMillis());
        } catch (InterruptedException e) {
            // Restore interrupt status and return
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Sleeps for a random duration between the given bounds (max bound is exclusive).
     * If disableSleep is true, returns immediately.
     * 
     * This is the Java equivalent of Go's CancellableSleepRandomBetween.
     */
    public void cancellableSleepRandomBetween(Duration minDuration, Duration maxDuration) {
        ensureInitialized();
        if (archetype.disableSleep()) {
            return;
        }
        
        long minMillis = minDuration.toMillis();
        long maxMillis = maxDuration.toMillis();
        
        if (maxMillis <= minMillis) {
            throw new IllegalArgumentException("maxDuration must be greater than minDuration");
        }
        
        long randomMillis = minMillis + random.nextLong(maxMillis - minMillis);
        cancellableSleep(Duration.ofMillis(randomMillis));
    }
    
    /**
     * Asynchronous version of cancellableSleep using virtual threads
     */
    public CompletableFuture<Void> cancellableSleepAsync(Duration sleepDuration) {
        ensureInitialized();
        if (archetype.disableSleep()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(
            () -> cancellableSleep(sleepDuration),
            virtualThreadExecutor
        );
    }
    
    /**
     * Generates a random integer between min (inclusive) and max (exclusive).
     * This is the Java equivalent of Go's randutil.IntBetween.
     */
    public int intBetween(int min, int max) {
        if (max <= min) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        return random.nextInt(min, max);
    }
    
    /**
     * Generates a random long between min (inclusive) and max (exclusive)
     */
    public long longBetween(long min, long max) {
        if (max <= min) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        return min + random.nextLong(max - min);
    }
    
    /**
     * Shutdown method to clean up resources
     */
    public void shutdown() {
        if (virtualThreadExecutor != null && !virtualThreadExecutor.isShutdown()) {
            virtualThreadExecutor.shutdown();
        }
    }
    
    @Override
    public String toString() {
        return String.format("BaseService{name='%s', disableSleep=%s}", 
                           serviceName, archetype != null ? archetype.disableSleep() : "unknown");
    }
}