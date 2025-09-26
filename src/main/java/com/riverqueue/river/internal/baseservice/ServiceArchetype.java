package com.riverqueue.river.internal.baseservice;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * ServiceArchetype contains the set of base service properties that are immutable,
 * or otherwise safe for services to copy from another service. This configuration
 * is designed to be shared across multiple services.
 * 
 * This is the Java21 equivalent of Go's Archetype struct.
 */
public record ServiceArchetype(
    boolean disableSleep,
    Logger logger,
    Supplier<Instant> timeProvider
) {
    
    /**
     * Compact constructor for validation
     */
    public ServiceArchetype {
        Objects.requireNonNull(logger, "Logger cannot be null");
        Objects.requireNonNull(timeProvider, "Time provider cannot be null");
    }
    
    /**
     * Creates a builder for ServiceArchetype
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a default configuration suitable for most services
     */
    public static ServiceArchetype defaultArchetype(Logger logger) {
        return new ServiceArchetype(
            false,
            logger,
            Instant::now
        );
    }
    
    /**
     * Creates a test configuration with sleep disabled
     */
    public static ServiceArchetype testArchetype(Logger logger) {
        return new ServiceArchetype(
            true,
            logger,
            Instant::now
        );
    }
    
    /**
     * Creates a test configuration with a simple console logger
     */
    public static ServiceArchetype testArchetype() {
        return new ServiceArchetype(
            true,
            Logger.getLogger("TestService"),
            Instant::now
        );
    }
    
    /**
     * Builder class for ServiceArchetype
     */
    public static final class Builder {
        private boolean disableSleep = false;
        private Logger logger;
        private Supplier<Instant> timeProvider = Instant::now;
        
        private Builder() {}
        
        public Builder disableSleep(boolean disableSleep) {
            this.disableSleep = disableSleep;
            return this;
        }
        
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }
        
        public Builder timeProvider(Supplier<Instant> timeProvider) {
            this.timeProvider = timeProvider;
            return this;
        }
        
        public ServiceArchetype build() {
            Objects.requireNonNull(logger, "Logger must be set");
            return new ServiceArchetype(disableSleep, logger, timeProvider);
        }
    }
}