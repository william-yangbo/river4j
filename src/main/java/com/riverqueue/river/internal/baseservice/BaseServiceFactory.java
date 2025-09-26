package com.riverqueue.river.internal.baseservice;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Factory class for initializing services with BaseService functionality.
 * This is the Java equivalent of Go's Init function.
 */
public final class BaseServiceFactory {
    
    private BaseServiceFactory() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Initializes a service with the given archetype.
     * Returns the same service that was passed in for convenience.
     * 
     * This is the Java equivalent of Go's Init[TService withBaseService] function.
     * 
     * @param archetype the service archetype containing configuration
     * @param service the service instance to initialize
     * @param <T> the service type that implements BaseServiceProvider
     * @return the initialized service
     */
    public static <T extends BaseServiceProvider> T initialize(
            ServiceArchetype archetype, 
            T service) {
        Objects.requireNonNull(archetype, "Archetype cannot be null");
        Objects.requireNonNull(service, "Service cannot be null");
        
        BaseService baseService = service.getBaseService();
        if (baseService == null) {
            throw new IllegalArgumentException("Service must provide a non-null BaseService instance");
        }
        
        // Get the service name from the class name, similar to Go's reflect.TypeOf(service).Elem().Name()
        String serviceName = getServiceName(service);
        
        // Initialize the base service
        // Note: In a real implementation, we'd need to handle the immutability of BaseService
        // This is a simplified version for demonstration
        baseService.initialize(archetype, serviceName);
        
        return service;
    }
    
    /**
     * Extracts the service name from the service class.
     * This mirrors Go's reflect.TypeOf(service).Elem().Name() behavior.
     */
    private static String getServiceName(Object service) {
        // Return the simple class name as-is to match Go behavior
        return service.getClass().getSimpleName();
    }
    
    /**
     * Creates a properly configured BaseService instance.
     * This is an alternative to the initialize method for cases where
     * you want to create a BaseService directly.
     */
    public static BaseService createBaseService(ServiceArchetype archetype, String serviceName) {
        Objects.requireNonNull(archetype, "Archetype cannot be null");
        Objects.requireNonNull(serviceName, "Service name cannot be null");
        
        return new BaseService(archetype, serviceName);
    }
}