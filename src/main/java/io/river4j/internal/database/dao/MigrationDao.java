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
        INSERT INTO river_migration (created_at, version)
        VALUES (:createdAt, :version)
        """)
    int insert(@Bind("createdAt") Instant createdAt, @Bind("version") long version);

    /**
     * Get latest migration version.
     */
    @SqlQuery("SELECT version FROM river_migration ORDER BY version DESC LIMIT 1")
    Optional<Long> getLatestVersion();

    /**
     * Delete migration by version.
     */
    @SqlUpdate("DELETE FROM river_migration WHERE version = :version")
    int deleteByVersion(@Bind("version") long version);
}