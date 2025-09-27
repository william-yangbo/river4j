package io.river4j.internal.dbmigrate;

import io.river4j.internal.database.DatabaseManager;
import io.river4j.internal.database.DatabaseTransaction;
import io.river4j.internal.database.model.RiverMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
// import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Migrator handles database schema migrations, equivalent to Go's Migrator.
 * Provides up and down migration capabilities with transaction support.
 */
public class Migrator {
    private static final Logger logger = LoggerFactory.getLogger(Migrator.class);
    
    // private static final Pattern MIGRATION_FILE_PATTERN = 
    //     Pattern.compile("^(\\d+)_([^.]+)\\.(up|down)\\.sql$");
    
    private final DatabaseManager databaseManager;
    private final String migrationsPath;
    private Map<Integer, MigrationBundle> migrations;
    
    /**
     * Create a new Migrator instance
     * @param databaseManager Database manager for executing migrations
     * @param migrationsPath Classpath path to migration files (e.g., "db/migrations")
     */
    public Migrator(DatabaseManager databaseManager, String migrationsPath) {
        this.databaseManager = databaseManager;
        this.migrationsPath = migrationsPath.endsWith("/") ? migrationsPath : migrationsPath + "/";
        this.migrations = loadMigrations();
    }
    
    /**
     * Migrate up to the latest version
     */
    public MigrateResult migrateUp() {
        return migrateUp(new MigrateOptions());
    }
    
    /**
     * Migrate up with options
     */
    public MigrateResult migrateUp(MigrateOptions options) {
        // First check current version outside transaction to avoid bootstrap issues
        int currentVersion = getCurrentVersion();
        logger.info("Current migration version: {}", currentVersion);
        
        // Find pending migrations
        List<MigrationBundle> pending = getPendingMigrations(currentVersion);
        if (pending.isEmpty()) {
            logger.info("Database is up to date");
            return new MigrateResult(Collections.emptyList());
        }
        
        // Apply max steps limit
        int effectiveLimit = options.getEffectiveMaxSteps(pending.size());
        List<MigrationBundle> toApply = pending.subList(0, effectiveLimit);
        
        logger.info("Applying {} migration(s)", toApply.size());
        
        return databaseManager.inTransaction(tx -> {
            
            List<Long> appliedVersions = new ArrayList<>();
            for (MigrationBundle migration : toApply) {
                logger.info("Applying migration {} - {}", migration.version(), migration.name());
                
                try {
                    // Execute the migration SQL
                    String sql = loadMigrationSql(migration.version(), migration.name(), true);
                    tx.handle().execute(sql);
                    
                    // Record the migration
                    recordMigration(tx, migration);
                    appliedVersions.add(migration.version());
                    
                    logger.info("Successfully applied migration {}", migration.version());
                } catch (Exception e) {
                    logger.error("Failed to apply migration {}: {}", migration.version(), e.getMessage());
                    throw new MigrationException("Migration " + migration.version() + " failed", e);
                }
            }
            
            return new MigrateResult(appliedVersions);
        });
    }
    
    /**
     * Migrate down by one step
     */
    public MigrateResult migrateDown() {
        return migrateDown(new MigrateOptions(1));
    }
    
    /**
     * Migrate down with options
     */
    public MigrateResult migrateDown(MigrateOptions options) {
        return databaseManager.inTransaction(tx -> {
            // Get current applied migrations in reverse order
            List<RiverMigration> applied = getAppliedMigrations(tx);
            if (applied.isEmpty()) {
                logger.info("No migrations to roll back");
                return new MigrateResult(Collections.emptyList());
            }
            
            // Apply max steps limit
            int effectiveLimit = options.getEffectiveMaxSteps(applied.size());
            List<RiverMigration> toRollback = applied.subList(0, effectiveLimit);
            
            logger.info("Rolling back {} migration(s)", toRollback.size());
            
            List<Long> rolledBackVersions = new ArrayList<>();
            for (RiverMigration migration : toRollback) {
                logger.info("Rolling back migration {} - {}", migration.version(), migration.name());
                
                try {
                    // For migration 1, remove the record first since the down SQL will drop the table
                    if (migration.version() == 1) {
                        removeMigrationRecord(tx, migration.version());
                    }
                    
                    // Execute the down migration SQL
                    String sql = loadMigrationSql(migration.version(), migration.name(), false);
                    logger.info("Executing down SQL for migration {}: {}", migration.version(), sql.trim());
                    
                    try {
                        int result = tx.handle().execute(sql);
                        logger.info("Migration {} down SQL executed, affected rows: {}", migration.version(), result);
                    } catch (Exception sqlEx) {
                        logger.error("Error executing down SQL for migration {}: {}", migration.version(), sqlEx.getMessage());
                        throw sqlEx;
                    }
                    
                    // For other migrations, remove the record after executing the SQL
                    if (migration.version() != 1) {
                        removeMigrationRecord(tx, migration.version());
                    }
                    
                    // Add version to result - Go returns versions in order they were rolled back
                    rolledBackVersions.add(migration.version());
                    
                    logger.info("Successfully rolled back migration {}", migration.version());
                } catch (Exception e) {
                    logger.error("Failed to roll back migration {}: {}", migration.version(), e.getMessage());
                    throw new MigrationException("Migration rollback " + migration.version() + " failed", e);
                }
            }
            
            return new MigrateResult(rolledBackVersions);
        });
    }
    
    /**
     * Get current migration version
     */
    public int getCurrentVersion() {
        DatabaseManager.TransactionCallback<Integer> callback = tx -> getCurrentVersion(tx);
        return databaseManager.inTransaction(callback);
    }
    
    /**
     * Get list of available migrations
     */
    public List<MigrationBundle> getAvailableMigrations() {
        return migrations.values().stream()
            .sorted(Comparator.comparing(MigrationBundle::version))
            .collect(Collectors.toList());
    }
    
    /**
     * Get list of pending migrations
     */
    public List<MigrationBundle> getPendingMigrations() {
        return getPendingMigrations(getCurrentVersion());
    }
    
    // Private helper methods
    
    private Map<Integer, MigrationBundle> loadMigrations() {
        // In a real application, you might scan the classpath
        // For now, we'll use a hardcoded list based on known migrations
        Map<Integer, MigrationBundle> result = new HashMap<>();
        
        // Add known migrations - this could be enhanced to scan classpath
        result.put(1, new MigrationBundle(1, "create_river_migration"));
        result.put(2, new MigrationBundle(2, "initial_schema"));
        
        return result;
    }
    
    /**
     * Set custom migrations for testing - allows overriding the default migration set
     */
    public void setMigrations(Map<Integer, MigrationBundle> testMigrations) {
        this.migrations = testMigrations;
    }
    
    private int getCurrentVersion(DatabaseTransaction tx) {
        try {
            return tx.getMigrationDao().getCurrentVersion().orElse(0);
        } catch (Exception e) {
            // Table might not exist yet - this is expected on first run
            logger.debug("Migration table not found, assuming version 0: {}", e.getMessage());
            return 0;
        }
    }
    
    private List<MigrationBundle> getPendingMigrations(int currentVersion) {
        return migrations.values().stream()
            .filter(m -> m.version() > currentVersion)
            .sorted(Comparator.comparing(MigrationBundle::version))
            .collect(Collectors.toList());
    }
    
    private List<RiverMigration> getAppliedMigrations(DatabaseTransaction tx) {
        try {
            return tx.getMigrationDao().getAppliedMigrations();
        } catch (Exception e) {
            // Table might not exist yet - this is expected on first run
            logger.debug("Migration table not found, returning empty list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    private String loadMigrationSql(long version, String name, boolean up) {
        // First check if we have inline SQL (for testing)
        MigrationBundle bundle = migrations.get((int) version);
        if (bundle != null && bundle.hasInlineSql()) {
            return up ? bundle.upSql() : bundle.downSql();
        }
        
        // Fall back to file-based migration loading
        String direction = up ? "up" : "down";
        String filename = String.format("%03d_%s.%s.sql", (int) version, name, direction);
        String resourcePath = migrationsPath + filename;
        
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new MigrationException("Migration file not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MigrationException("Failed to load migration file: " + resourcePath, e);
        }
    }
    
    private void recordMigration(DatabaseTransaction tx, MigrationBundle migration) {
        RiverMigration record = new RiverMigration(
            null,
            migration.version(),
            migration.name(),
            Instant.now()
        );
        tx.getMigrationDao().insert(record);
    }
    
    private void removeMigrationRecord(DatabaseTransaction tx, long version) {
        tx.getMigrationDao().deleteByVersion(version);
    }
    
    /**
     * Result of a migration operation - matches Go's MigrateResult structure
     */
    public record MigrateResult(
        List<Long> versions
    ) {
        public boolean hasChanges() {
            return !versions.isEmpty();
        }
        
        public int getStepsApplied() {
            return versions.size();
        }
        
        public int getFinalVersion() {
            return versions.isEmpty() ? 0 : versions.get(versions.size() - 1).intValue();
        }
    }
    
    /**
     * Exception thrown when migration operations fail
     */
    public static class MigrationException extends RuntimeException {
        public MigrationException(String message) {
            super(message);
        }
        
        public MigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}