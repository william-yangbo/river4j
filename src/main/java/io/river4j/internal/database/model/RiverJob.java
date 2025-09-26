package io.river4j.internal.database.model;

import java.time.Instant;
import java.util.List;

/**
 * River job model.
 * Corresponds to river_job table in PostgreSQL.
 */
public record RiverJob(
    long id,
    byte[] args,
    short attempt,
    Instant attemptedAt,
    List<String> attemptedBy,
    Instant createdAt,
    List<AttemptError> errors,
    Instant finalizedAt,
    String kind,
    short maxAttempts,
    byte[] metadata,
    short priority,
    String queue,
    JobState state,
    Instant scheduledAt,
    List<String> tags
) {
    public RiverJob {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("Job kind cannot be null or blank");
        }
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("Job queue cannot be null or blank");
        }
        if (state == null) {
            throw new IllegalArgumentException("Job state cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created at cannot be null");
        }
        if (scheduledAt == null) {
            throw new IllegalArgumentException("Scheduled at cannot be null");
        }
        if (priority < 1 || priority > 4) {
            throw new IllegalArgumentException("Priority must be between 1 and 4");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Max attempts must be positive");
        }
        
        // Ensure non-null collections
        if (attemptedBy == null) {
            attemptedBy = List.of();
        }
        if (errors == null) {
            errors = List.of();
        }
        if (tags == null) {
            tags = List.of();
        }
        
        // Default empty metadata if null
        if (metadata == null) {
            metadata = new byte[0];
        }
    }
    
    /**
     * Check if job is in a finalized state
     */
    public boolean isFinalized() {
        return state == JobState.CANCELLED || 
               state == JobState.COMPLETED || 
               state == JobState.DISCARDED;
    }
    
    /**
     * Check if job is available for work
     */
    public boolean isAvailable() {
        return state == JobState.AVAILABLE && 
               scheduledAt.isBefore(Instant.now());
    }
}