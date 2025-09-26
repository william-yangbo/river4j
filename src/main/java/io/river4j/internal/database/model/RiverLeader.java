package io.river4j.internal.database.model;

import java.time.Instant;

/**
 * River leader model for leader election.
 * Corresponds to river_leader table in PostgreSQL.
 */
public record RiverLeader(
    Instant electedAt,
    Instant expiresAt,
    String leaderID,
    String name
) {
    public RiverLeader {
        if (electedAt == null) {
            throw new IllegalArgumentException("Elected at cannot be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Expires at cannot be null");
        }
        if (leaderID == null || leaderID.isBlank()) {
            throw new IllegalArgumentException("Leader ID cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or blank");
        }
        if (expiresAt.isBefore(electedAt)) {
            throw new IllegalArgumentException("Expires at must be after elected at");
        }
    }
    
    /**
     * Check if this leader election has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Check if this leader election is still valid
     */
    public boolean isActive() {
        return !isExpired();
    }
}