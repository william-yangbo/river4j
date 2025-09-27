package io.river4j.internal.dbmigrate;

import io.river4j.internal.database.DatabaseManager;
import io.river4j.internal.database.model.RiverMigration;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Migrator class, strictly aligned with Go's db_migrate_test.go.
 * Test cases match Go version exactly: Down, DownAfterUp, DownWithMaxSteps, 
 * DownWithMaxStepsZero, Up, UpWithMaxSteps, UpWithMaxStepsZero
 */
public class MigratorTest {

    // Constants matching Go test setup
    private static final long RIVER_MIGRATIONS_MAX_VERSION = 2; // Our base migrations end at version 2
    
    // Test migrations - equivalent to Go's testVersions with inline SQL
    private static final Map<Integer, MigrationBundle> TEST_MIGRATIONS = createTestMigrations();
    
    private static Map<Integer, MigrationBundle> createTestMigrations() {
        Map<Integer, MigrationBundle> result = new HashMap<>();
        
        // Add base migrations (equivalent to riverMigrations in Go)
        result.put(1, new MigrationBundle(1, "create_river_migration"));
        result.put(2, new MigrationBundle(2, "initial_schema"));
        
        // Add test-specific migrations with inline SQL (like Go version)
        // These match exactly the Go test migrations:
        result.put(3, new MigrationBundle(3, "test_table", 
            "CREATE TABLE test_table(id bigserial PRIMARY KEY);",
            "DROP TABLE test_table;"));
        result.put(4, new MigrationBundle(4, "test_table_with_name",
            "ALTER TABLE test_table ADD COLUMN name varchar(200); CREATE INDEX idx_test_table_name ON test_table(name);",
            "DROP INDEX idx_test_table_name; ALTER TABLE test_table DROP COLUMN name;"));
        
        return result;
    }

    private static class TestBundle {
        public final DatabaseManager databaseManager;
        public final io.river4j.internal.testdb.TestDB testDB;

        public TestBundle(DatabaseManager databaseManager, io.river4j.internal.testdb.TestDB testDB) {
            this.databaseManager = databaseManager;
            this.testDB = testDB;
        }

        public void cleanup() {
            try {
                if (testDB != null) {
                    testDB.release();
                }
                if (databaseManager != null) {
                    databaseManager.close();
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    private TestBundle setup() {
        // Go version uses isolated databases + transactions:
        // 1. riverinternaltest.TestDB() returns a DB with base migrations applied  
        // 2. testDB.Begin() starts a transaction
        // 3. tx.Rollback() prevents schema changes from persisting
        // 4. migrator.migrations = testVersions sets complete migration list
        
        // Create a fresh TestDB for complete isolation (like Go version)
        io.river4j.internal.testdb.TestDBManager manager = new io.river4j.internal.testdb.TestDBManager(10, null, null);
        io.river4j.internal.testdb.TestDB testDB = manager.acquire();
        
        // Apply base migrations to match Go's riverinternaltest.TestDB() behavior
        DatabaseManager dbManager = testDB.getDatabaseManager();
        Migrator baseMigrator = new Migrator(dbManager, "io/river4j/internal/dbmigrate");
        baseMigrator.migrateUp(); // Apply base migrations (versions 1,2)
        
        return new TestBundle(dbManager, testDB);
    }

    @Test
    void testDown() {
        TestBundle bundle = setup();
        
        try {
            Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
            migrator.setMigrations(TEST_MIGRATIONS);
            
            // Run an initial time
            Migrator.MigrateResult result = migrator.migrateDown(new MigrateOptions());
            assertNotNull(result);
            assertEquals(Arrays.asList(2L, 1L), result.versions()); // Should roll back from 2 to 1 to 0
            
            // Verify river_migration table doesn't exist
            assertTrue(dbExecError(bundle.databaseManager, "SELECT * FROM river_migration"));
            
            // Run once more to verify idempotency
            Migrator.MigrateResult result2 = migrator.migrateDown(new MigrateOptions());
            assertNotNull(result2);
            assertEquals(Collections.emptyList(), result2.versions());
            
            // Verify river_migration table still doesn't exist
            assertTrue(dbExecError(bundle.databaseManager, "SELECT * FROM river_migration"));
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testDownAfterUp() {
        TestBundle bundle = setup();
        
        try {
            Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
            migrator.setMigrations(TEST_MIGRATIONS);
            
            // First migrate up (should apply test migrations 3,4)
            Migrator.MigrateResult upResult = migrator.migrateUp(new MigrateOptions());
            assertEquals(Arrays.asList(3L, 4L), upResult.versions());
            
            // Then migrate down all the way (should roll back all 4 migrations)
            Migrator.MigrateResult downResult = migrator.migrateDown(new MigrateOptions());
            assertEquals(Arrays.asList(4L, 3L, 2L, 1L), downResult.versions()); // All 4 migrations rolled back
            
            // Verify river_migration table is removed (migration 1 drops it)
            assertTrue(dbExecError(bundle.databaseManager, "SELECT * FROM river_migration"));
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testDownWithMaxSteps() {
        TestBundle bundle = setup();
        
        try {
            Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
            migrator.setMigrations(TEST_MIGRATIONS);
            
            // First migrate up to have something to migrate down
            migrator.migrateUp(new MigrateOptions());
            
            // Migrate down with max steps = 1
            Migrator.MigrateResult result = migrator.migrateDown(new MigrateOptions(1));
            assertEquals(Arrays.asList(4L), result.versions()); // Only roll back migration 4
            
            // Should still have migrations 1, 2, 3
            List<RiverMigration> migrations = bundle.databaseManager.migrations().getAll();
            assertEquals(seqOneTo(3), migrationVersions(migrations));
            
            // Verify test_table still exists but column 'name' should not exist
            // (name column is only added in migration 4)
            assertTrue(dbExecError(bundle.databaseManager, "SELECT name FROM test_table"));
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testDownWithMaxStepsZero() {
        TestBundle bundle = setup();
        
        try {
            Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
            migrator.setMigrations(TEST_MIGRATIONS);
            
            // First migrate up to have something to potentially migrate down
            migrator.migrateUp(new MigrateOptions());
            
            // Migrate down with max steps zero (should do nothing)
            Migrator.MigrateResult result = migrator.migrateDown(new MigrateOptions(0));
            assertEquals(Collections.emptyList(), result.versions());
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testUp() {
        TestBundle bundle = setup();
        
        try {
            Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
            migrator.setMigrations(TEST_MIGRATIONS);
            
            // Run an initial time - should apply test migrations 3,4 (versions riverMigrationsMaxVersion+1,+2)
            Migrator.MigrateResult result = migrator.migrateUp(new MigrateOptions());
            assertEquals(Arrays.asList(3L, 4L), result.versions());
            
            // Verify all migrations were applied
            List<RiverMigration> migrations = bundle.databaseManager.migrations().getAll();
            assertEquals(seqOneTo(4), migrationVersions(migrations));
            
            // Verify test_table exists 
            assertFalse(dbExecError(bundle.databaseManager, "SELECT * FROM test_table"));
            
            // Run once more to verify idempotency
            Migrator.MigrateResult result2 = migrator.migrateUp(new MigrateOptions());
            assertEquals(Collections.emptyList(), result2.versions());
            
            // Verify migrations are still there
            List<RiverMigration> migrations2 = bundle.databaseManager.migrations().getAll();
            assertEquals(seqOneTo(4), migrationVersions(migrations2));
            
            // Verify test_table still exists
            assertFalse(dbExecError(bundle.databaseManager, "SELECT * FROM test_table"));
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testUpWithMaxSteps() {
        TestBundle bundle = setup();
        
        try {
            Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
            migrator.setMigrations(TEST_MIGRATIONS);
            
            // Migrate up with max steps = 1 (should apply version 3 only, since DB starts at version 2)
            Migrator.MigrateResult result = migrator.migrateUp(new MigrateOptions(1));
            assertEquals(Arrays.asList(3L), result.versions());
            
            // Verify migrations 1,2,3 are applied (1,2 from setup, 3 from test)
            List<RiverMigration> migrations = bundle.databaseManager.migrations().getAll();
            assertEquals(seqOneTo(3), migrationVersions(migrations));
            
            // test_table should exist (created in migration 3)
            assertFalse(dbExecError(bundle.databaseManager, "SELECT * FROM test_table"));
            
            // Column `name` is only added in the fourth test version, so should not exist
            assertTrue(dbExecError(bundle.databaseManager, "SELECT name FROM test_table"));
        } finally {
            bundle.cleanup();
        }
    }

    @Test
    void testUpWithMaxStepsZero() {
        TestBundle bundle = setup();
        
        try {
            Migrator migrator = new Migrator(bundle.databaseManager, "io/river4j/internal/dbmigrate");
            migrator.setMigrations(TEST_MIGRATIONS);
            
            // Migrate up with max steps zero (should do nothing)
            Migrator.MigrateResult result = migrator.migrateUp(new MigrateOptions(0));
            assertEquals(Collections.emptyList(), result.versions());
        } finally {
            bundle.cleanup();
        }
    }

    // Helper functions matching Go test helpers
    
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
     * Extract version numbers from migration list - equivalent to Go's migrationVersions
     */
    private List<Long> migrationVersions(List<RiverMigration> migrations) {
        return migrations.stream()
            .map(RiverMigration::version)
            .sorted()
            .toList();
    }
    
    /**
     * Generate sequence from 1 to max - equivalent to Go's seqOneTo
     */
    private List<Long> seqOneTo(long max) {
        return IntStream.rangeClosed(1, (int) max)
            .mapToLong(i -> i)
            .boxed()
            .toList();
    }
}