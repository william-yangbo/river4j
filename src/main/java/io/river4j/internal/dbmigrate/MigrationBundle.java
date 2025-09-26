package io.river4j.internal.dbmigrate;

/**
 * Migration bundle containing version and name information.
 * Used for tracking available migrations and their metadata.
 */
public record MigrationBundle(
    long version,
    String name
) {
    public MigrationBundle {
        if (version <= 0) {
            throw new IllegalArgumentException("Migration version must be positive, got: " + version);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Migration name cannot be null or blank");
        }
    }
    
    /**
     * Create MigrationBundle with int version for convenience
     */
    public MigrationBundle(int version, String name) {
        this((long) version, name);
    }
}