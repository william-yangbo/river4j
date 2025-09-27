package io.river4j.internal.dbmigrate;

/**
 * Migration bundle containing version, name and optional inline SQL.
 * Used for tracking available migrations and their metadata.
 * Supports both file-based migrations and inline SQL (for testing).
 */
public record MigrationBundle(
    long version,
    String name,
    String upSql,    // Optional inline SQL for up migration
    String downSql   // Optional inline SQL for down migration
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
     * Create MigrationBundle with int version for convenience (file-based)
     */
    public MigrationBundle(int version, String name) {
        this((long) version, name, null, null);
    }
    
    /**
     * Create MigrationBundle with int version and inline SQL (for testing)
     */
    public MigrationBundle(int version, String name, String upSql, String downSql) {
        this((long) version, name, upSql, downSql);
    }
    
    /**
     * Check if this migration has inline SQL (used for testing)
     */
    public boolean hasInlineSql() {
        return upSql != null && downSql != null;
    }
}