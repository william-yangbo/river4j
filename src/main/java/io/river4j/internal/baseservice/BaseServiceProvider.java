package io.river4j.internal.baseservice;

/**
 * Interface for objects that embed BaseService functionality.
 * This is the Java equivalent of Go's withBaseService interface.
 */
public interface BaseServiceProvider {
    /**
     * Returns the embedded BaseService instance
     */
    BaseService getBaseService();
}