package io.river4j.internal.database;

import io.river4j.internal.database.model.RiverJob;

import java.time.Instant;
import java.util.List;

/**
 * Example usage of River4J database layer.
 * This demonstrates the main API patterns.
 */
public class DatabaseUsageExample {
    
    public static void main(String[] args) {
        // Initialize database manager
        DatabaseManager db = new DatabaseManager(
            "jdbc:postgresql://localhost:5432/river_test",
            "river_user", 
            "river_password"
        );
        
        try {
            exampleBasicOperations(db);
            exampleTransactionUsage(db);
        } finally {
            db.close();
        }
    }
    
    private static void exampleBasicOperations(DatabaseManager db) {
        System.out.println("=== Basic Operations Example ===");
        
        // Insert a job
        Instant now = Instant.now();
        int inserted = db.jobs().insert(
            "EmailJob",
            "{\"to\":\"user@example.com\",\"subject\":\"Hello\"}".getBytes(),
            "default",
            (short) 1,
            now,
            (short) 3,
            "{}".getBytes(),
            List.of("email", "notification")
        );
        System.out.println("Inserted jobs: " + inserted);
        
        // Count running jobs
        long runningCount = db.jobs().countRunning();
        System.out.println("Running jobs: " + runningCount);
        
        // Get available jobs
        List<RiverJob> availableJobs = db.jobs().getAvailable(
            List.of("default", "priority"),
            10,
            now,
            "worker-1"
        );
        System.out.println("Available jobs fetched: " + availableJobs.size());
        
        // Find jobs by kind
        List<RiverJob> emailJobs = db.jobs().findByKind("EmailJob");
        System.out.println("Email jobs found: " + emailJobs.size());
        
        // Leader operations
        db.leaders().insertOrUpdate(
            now,
            now.plusSeconds(30),
            "leader-1",
            "main-service"
        );
        System.out.println("Leader elected");
        
        // Check current leader
        var currentLeader = db.leaders().getElectedLeader("main-service", now);
        if (currentLeader.isPresent()) {
            System.out.println("Current leader: " + currentLeader.get().leaderID());
        }
    }
    
    private static void exampleTransactionUsage(DatabaseManager db) {
        System.out.println("\n=== Transaction Usage Example ===");
        
        // Transaction with return value
        Long jobId = db.inTransaction(tx -> {
            // Insert job within transaction
            tx.jobs().insert(
                "ProcessingJob",
                "{\"data\":\"important\"}".getBytes(),
                "critical",
                (short) 2,
                Instant.now(),
                (short) 5,
                "{}".getBytes(),
                List.of("critical", "processing")
            );
            
            // Could do more operations here...
            // If any operation fails, the transaction will be rolled back
            
            return 123L; // Return the job ID (would be real ID in practice)
        });
        
        System.out.println("Job created in transaction with ID: " + jobId);
        
        // Transaction without return value
        db.inTransaction(tx -> {
            // Clean up old jobs
            Instant now = Instant.now();
            long deletedCount = tx.jobs().deleteBefore(
                now.minusSeconds(7 * 24 * 3600),  // cancelled jobs older than 7 days
                now.minusSeconds(1 * 24 * 3600),  // completed jobs older than 1 day
                now.minusSeconds(3 * 24 * 3600),  // discarded jobs older than 3 days
                1000
            );
            
            // Clean up expired leaders
            long expiredLeaders = tx.leaders().deleteExpired(Instant.now());
            
            System.out.println("Cleanup: deleted " + deletedCount + " jobs, " + 
                             expiredLeaders + " expired leaders");
        });
    }
}