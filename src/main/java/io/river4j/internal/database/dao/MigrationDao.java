package io.river4j.internal.database.dao;

import io.river4j.internal.database.model.RiverMigration;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * River migration data access object for schema versioning.
 * Corresponds to the queries in river_migration.sql from dbsqlc.
 */
public interface MigrationDao extends SqlObject {

    /**
     * Get all migrations.
     * Corresponds to MigrationGetAll in Go.
     */
    @SqlQuery("SELECT * FROM river_migration ORDER BY version")
    List<RiverMigration> getAll();

    /**
     * Get migration by version.
     * Corresponds to MigrationGetByVersion in Go.
     */
    @SqlQuery("SELECT * FROM river_migration WHERE version = :version")
    Optional<RiverMigration> getByVersion(@Bind("version") long version);

    /**
     * Insert new migration.
     * Corresponds to MigrationInsert in Go.
     */
    @SqlUpdate("""
        INSERT INTO river_migration (version, name, created_at)
        VALUES (:version, :name, :createdAt)
        """)
    int insert(@Bind("version") long version, @Bind("name") String name, @Bind("createdAt") Instant createdAt);
    
    /**
     * Insert new migration from model.
     */
    default int insert(RiverMigration migration) {
        return insert(migration.version(), migration.name(), migration.createdAt());
    }

    /**
     * Get latest migration version.
     */
    @SqlQuery("SELECT version FROM river_migration ORDER BY version DESC LIMIT 1")
    Optional<Long> getLatestVersion();
    
    /**
     * Get current migration version (same as getLatestVersion but returns int).
     */
    default Optional<Integer> getCurrentVersion() {
        return getLatestVersion().map(Long::intValue);
    }
    
    /**
     * Get applied migrations in reverse order (for rollback).
     */
    @SqlQuery("SELECT * FROM river_migration ORDER BY version DESC")
    List<RiverMigration> getAppliedMigrations();

    /**
     * Delete migration by version.
     */
    @SqlUpdate("DELETE FROM river_migration WHERE version = :version")
    int deleteByVersion(@Bind("version") long version);
}