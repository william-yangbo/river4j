package io.river4j.internal.database.model;

import java.time.Instant;

/**
 * River migration model for database schema versioning.
 * Corresponds to river_migration table in PostgreSQL.
 */
public record RiverMigration(
    Long id,
    long version,
    String name,
    Instant createdAt
) {
    public RiverMigration {
        if (createdAt == null) {
            throw new IllegalArgumentException("Created at cannot be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version must be non-negative");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Migration name cannot be null or blank");
        }
    }
}