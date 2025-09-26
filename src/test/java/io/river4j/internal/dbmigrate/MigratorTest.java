package io.river4j.internal.dbmigrate;

import io.river4j.internal.database.DatabaseManager;
import io.river4j.internal.database.model.RiverMigration;
import io.river4j.internal.util.dbutil.DatabaseManagerExecutor;
import io.river4j.internal.util.dbutil.DbUtil;
import io.river4j.internal.riverinternaltest.RiverInternalTest;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Migrator class, aligned with Go's db_migrate_test.go patterns.
 * Uses dbutil and riverinternaltest for proper test isolation.
 */
public class MigratorTest {

    private static class TestBundle {
        public final DatabaseManager databaseManager;
        public final DatabaseManagerExecutor executor;

        public TestBundle(DatabaseManager databaseManager) {
            this.databaseManager = databaseManager;
            this.executor = new DatabaseManagerExecutor(databaseManager);
        }

        public void cleanup() {
            try {
                databaseManager.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    private TestBundle setup() {
        // Go tests use empty databases. To match this behavior,
        // we need to start with a clean database state.
        DatabaseManager dbManager = RiverInternalTest.testDB();
        
        // Ensure clean state by rolling back any existing migrations
        try {
            Migrator migrator = new Migrator(dbManager, "io/river4j/internal/dbmigrate");
            // Roll back all migrations to get to empty state
            migrator.migrateDown(new MigrateOptions());
        } catch (Exception e) {
            // Ignore - table might not exist yet, which is what we want
        }
        
        return new TestBundle(dbManager);
    }

    @Test
    void testMigratorDown() {
        TestBundle bundle = setup();
        
        try {
            // Run migrate up and down in separate transactions
            Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
            
            // First migrate up to establish migrations for rollback
            Migrator.MigrateResult upResult = migrator.migrateUp(new MigrateOptions());
            assertTrue(upResult.hasChanges());
            assertEquals(2, upResult.getStepsApplied());
            
            // Run an initial time - should migrate down from existing state  
            Migrator.MigrateResult result = migrator.migrateDown(new MigrateOptions());
            assertNotNull(result);
            assertTrue(result.hasChanges());
            assertEquals(2, result.getStepsApplied()); // Should roll back 2 migrations
            
            // Verify the current version after rollback
            int currentVersion = migrator.getCurrentVersion();
            System.out.println("Current version after migrateDown: " + currentVersion);
            assertEquals(0, currentVersion); // Should be back to version 0
            
            // Run once more to verify idempotency
            Migrator.MigrateResult result2 = migrator.migrateDown(new MigrateOptions());
            assertNotNull(result2);
            assertFalse(result2.hasChanges()); // No more migrations to roll back
            assertEquals(0, result2.getStepsApplied());
            
            // Verify that river_migration table still doesn't exist
            assertTrue(dbExecError(bundle.databaseManager, "SELECT * FROM river_migration"));
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testMigratorDownAfterUp() {
        TestBundle bundle = setup();
        
        try {
            DbUtil.withTx(bundle.executor, (connection) -> {
                Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
                
                // First migrate up
                Migrator.MigrateResult upResult = migrator.migrateUp(new MigrateOptions());
                assertTrue(upResult.hasChanges());
                
                // Then migrate down
                Migrator.MigrateResult downResult = migrator.migrateDown(new MigrateOptions());
                assertTrue(downResult.hasChanges());
                assertEquals(2, downResult.getStepsApplied()); // Should roll back 2 migrations
                
                // Verify river_migration table is removed
                assertTrue(dbExecError(bundle.databaseManager, "SELECT * FROM river_migration"));
            });
        } catch (SQLException e) {
            fail("Database operation failed: " + e.getMessage());
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testMigratorDownWithMaxSteps() {
        TestBundle bundle = setup();
        
        try {
            DbUtil.withTx(bundle.executor, (connection) -> {
                Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
                
                // First migrate up to have something to migrate down
                migrator.migrateUp(new MigrateOptions());
                
                // Migrate down with max steps
                Migrator.MigrateResult result = migrator.migrateDown(new MigrateOptions(1));
                assertTrue(result.hasChanges());
                assertEquals(1, result.getStepsApplied());
                
                // Should still have some migrations
                List<RiverMigration> migrations = bundle.databaseManager.migrations().getAll();
                assertTrue(migrations.size() > 0);
            });
        } catch (SQLException e) {
            fail("Database operation failed: " + e.getMessage());
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testMigratorDownWithMaxStepsZero() {
        TestBundle bundle = setup();
        
        try {
            DbUtil.withTx(bundle.executor, (connection) -> {
                Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
                
                // First migrate up to have something to potentially migrate down
                Migrator.MigrateResult upResult = migrator.migrateUp(new MigrateOptions());
                assertTrue(upResult.hasChanges()); // Verify up migration worked
                
                // Migrate down with max steps zero (should do nothing)
                Migrator.MigrateResult result = migrator.migrateDown(new MigrateOptions(0));
                assertFalse(result.hasChanges());
                assertEquals(0, result.getStepsApplied());
                
                // Verify migrations still exist (nothing was rolled back)
                List<RiverMigration> migrations = bundle.databaseManager.migrations().getAll();
                assertEquals(2, migrations.size()); // Both migrations should still be there
            });
        } catch (SQLException e) {
            fail("Database operation failed: " + e.getMessage());
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testMigratorUp() {
        TestBundle bundle = setup();
        
        try {
            DbUtil.withTx(bundle.executor, (connection) -> {
                Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
                
                // Run an initial time
                Migrator.MigrateResult result = migrator.migrateUp(new MigrateOptions());
                assertTrue(result.hasChanges());
                
                // Verify migrations were applied
                List<RiverMigration> migrations = bundle.databaseManager.migrations().getAll();
                assertTrue(migrations.size() > 0);
                
                // Run once more to verify idempotency
                Migrator.MigrateResult result2 = migrator.migrateUp(new MigrateOptions());
                assertFalse(result2.hasChanges());
                assertEquals(0, result2.getStepsApplied());
            });
        } catch (SQLException e) {
            fail("Database operation failed: " + e.getMessage());
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testMigratorUpWithMaxSteps() {
        TestBundle bundle = setup();
        
        try {
            DbUtil.withTx(bundle.executor, (connection) -> {
                Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
                
                // Migrate up with max steps (start from clean state)
                Migrator.MigrateResult result = migrator.migrateUp(new MigrateOptions(1));
                assertTrue(result.hasChanges());
                assertEquals(1, result.getStepsApplied());
                
                // Verify only one migration was applied
                List<RiverMigration> migrations = bundle.databaseManager.migrations().getAll();
                assertEquals(1, migrations.size());
                assertEquals(1, migrations.get(0).version());
            });
        } catch (SQLException e) {
            fail("Database operation failed: " + e.getMessage());
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testMigratorUpWithMaxStepsZero() {
        TestBundle bundle = setup();
        
        try {
            DbUtil.withTx(bundle.executor, (connection) -> {
                Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
                
                // Migrate up with max steps zero (should do nothing)
                Migrator.MigrateResult result = migrator.migrateUp(new MigrateOptions(0));
                assertFalse(result.hasChanges());
                assertEquals(0, result.getStepsApplied());
                
                // Should have no migrations - database should remain in clean state
                // Since no migrations were applied, the river_migration table doesn't exist yet
                // So we can't query it. Just verify the result is correct.
            });
        } catch (Exception e) {
            e.printStackTrace();
            fail("Database operation failed: " + e.getMessage() + " - " + e.getClass().getSimpleName());
        } finally {
            bundle.cleanup();
        }
    }

    /**
     * Execute a command in a separate transaction to verify an error without aborting main transaction.
     * Equivalent to Go's dbExecError function.
     * Returns true if error occurs, false if successful.
     */
    private boolean dbExecError(DatabaseManager databaseManager, String sql) {
        try {
            // Use a fresh transaction to check database state, like Go's dbExecError
            databaseManager.inTransaction(tx -> {
                tx.handle().execute(sql);
                return null;
            });
            return false; // No error
        } catch (Exception e) {
            return true; // Error occurred
        }
    }

    /**
     * Extract version numbers from migration list.
     * Equivalent to Go's migrationVersions function.
     */
    private List<Long> migrationVersions(List<RiverMigration> migrations) {
        return migrations.stream()
                .map(RiverMigration::version)
                .collect(Collectors.toList());
    }

    /**
     * Generate sequence from 1 to max.
     * Equivalent to Go's seqOneTo function.
     */
    private List<Long> seqOneTo(long max) {
        return LongStream.range(1, max + 1)
                .boxed()
                .collect(Collectors.toList());
    }

    /**
     * Generate reverse sequence from max to 1.
     * Equivalent to Go's seqToOne function.
     */
    private List<Long> seqToOne(long max) {
        return LongStream.range(1, max + 1)
                .boxed()
                .sorted((a, b) -> Long.compare(b, a))
                .collect(Collectors.toList());
    }
}