package io.river4j.internal.database.model;

import java.time.Instant;

/**
 * Represents an error that occurred during job attempt execution.
 * This is stored as JSONB in PostgreSQL.
 */
public record AttemptError(
    int attempt,
    Instant at,
    String error,
    String trace
) {
    public AttemptError {
        if (attempt < 0) {
            throw new IllegalArgumentException("Attempt must be non-negative");
        }
        if (at == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        if (error == null || error.isBlank()) {
            throw new IllegalArgumentException("Error message cannot be null or blank");
        }
    }
}