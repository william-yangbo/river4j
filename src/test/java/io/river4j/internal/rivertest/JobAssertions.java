package io.river4j.internal.rivertest;

import io.river4j.internal.database.model.RiverJob;
import io.river4j.internal.database.model.JobState;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * JobAssertions provides test utilities for verifying job insertion and state.
 * This is the Java equivalent of the Go rivertest job assertion functions.
 * 
 * NOTE: This is a prototype implementation showing alignment with Go rivertest.
 * Full implementation would require additional Job/JobArgs/JobRow classes and 
 * proper query methods on DatabaseManager.
 */
public class JobAssertions {
    
    /**
     * Options for job insertion requirements including expectations for various
     * queuing properties that stem from InsertOpts.
     */
    public static class RequireInsertedOpts {
        private int maxAttempts;
        private int priority;
        private String queue;
        private Instant scheduledAt;
        private JobState state;
        private String[] tags;
        
        public RequireInsertedOpts maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }
        
        public RequireInsertedOpts priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public RequireInsertedOpts queue(String queue) {
            this.queue = queue;
            return this;
        }
        
        public RequireInsertedOpts scheduledAt(Instant scheduledAt) {
            this.scheduledAt = scheduledAt;
            return this;
        }
        
        public RequireInsertedOpts state(JobState state) {
            this.state = state;
            return this;
        }
        
        public RequireInsertedOpts tags(String... tags) {
            this.tags = tags;
            return this;
        }
        
        // Getters
        public int getMaxAttempts() { return maxAttempts; }
        public int getPriority() { return priority; }
        public String getQueue() { return queue; }
        public Instant getScheduledAt() { return scheduledAt; }
        public JobState getState() { return state; }
        public String[] getTags() { return tags; }
    }
    
    /**
     * Expected job encapsulating job kind and possible insertion options.
     */
    public static class ExpectedJob {
        private final String kind;
        private final RequireInsertedOpts opts;
        
        public ExpectedJob(String kind, RequireInsertedOpts opts) {
            this.kind = kind;
            this.opts = opts;
        }
        
        public ExpectedJob(String kind) {
            this(kind, null);
        }
        
        public String getKind() { return kind; }
        public RequireInsertedOpts getOpts() { return opts; }
    }
    
    /**
     * Verifies that a job of the given kind was inserted for work.
     * Fails the test if it wasn't found or if more than one job was found.
     * 
     * @param dbtx the database executor (can be DatabaseManager, Connection, etc.)
     * @param expectedKind the expected job kind
     * @param opts optional requirements for the job
     * @return the found job
     * @throws AssertionError if job requirements are not met
     */
    public static RiverJob requireInserted(
            DBTX dbtx, 
            String expectedKind, 
            RequireInsertedOpts opts) {
        
        try {
            String query = "SELECT * FROM river_job WHERE kind = ? ORDER BY id";
            List<RiverJob> dbJobs = dbtx.query(query, RiverJob.class, expectedKind);
            
            if (dbJobs.isEmpty()) {
                throw new AssertionError(failureString("No jobs found with kind: %s", expectedKind));
            }
            
            if (dbJobs.size() > 1) {
                throw new AssertionError(failureString(
                    "More than one job found with kind: %s (you might want requireManyInserted instead)", 
                    expectedKind));
            }
            
            RiverJob dbJob = dbJobs.get(0);
            
            // Validate options if provided
            if (opts != null) {
                validateJobOptions(dbJob, opts, -1);
            }
            
            return dbJob;
        } catch (Exception e) {
            throw new AssertionError(failureString("Error querying for job: %s", e.getMessage()), e);
        }
    }
    
    /**
     * Verifies that jobs of the given kinds were inserted for work in the correct order.
     * 
     * @param dbtx the database executor (can be DatabaseManager, Connection, etc.)
     * @param expectedJobs list of expected jobs with their options
     * @return list of found jobs
     * @throws AssertionError if job requirements are not met
     */
    public static List<RiverJob> requireManyInserted(
            DBTX dbtx, 
            List<ExpectedJob> expectedJobs) {
        
        try {
            List<String> expectedKinds = expectedJobs.stream()
                .map(ExpectedJob::getKind)
                .toList();
            
            // Build query with IN clause
            StringBuilder queryBuilder = new StringBuilder("SELECT * FROM river_job WHERE kind IN (");
            for (int i = 0; i < expectedKinds.size(); i++) {
                if (i > 0) queryBuilder.append(", ");
                queryBuilder.append("?");
            }
            queryBuilder.append(") ORDER BY id");
            
            List<RiverJob> dbJobs = dbtx.query(queryBuilder.toString(), RiverJob.class, expectedKinds.toArray());
            
            List<String> actualKinds = dbJobs.stream()
                .map(RiverJob::kind)
                .toList();
            
            if (!expectedKinds.equals(actualKinds)) {
                throw new AssertionError(failureString(
                    "Inserted jobs didn't match expectation; expected: %s, actual: %s",
                    expectedKinds, actualKinds));
            }
            
            // Validate each job's options if provided
            for (int i = 0; i < dbJobs.size(); i++) {
                RequireInsertedOpts opts = expectedJobs.get(i).getOpts();
                if (opts != null) {
                    validateJobOptions(dbJobs.get(i), opts, i);
                }
            }
            
            return dbJobs;
        } catch (Exception e) {
            throw new AssertionError(failureString("Error querying for jobs: %s", e.getMessage()), e);
        }
    }
    
    /**
     * Validates job options against expected requirements.
     */
    private static void validateJobOptions(RiverJob riverJob, RequireInsertedOpts expectedOpts, int index) {
        String positionStr = index == -1 ? "" : String.format(" (expected job slice index %d)", index);
        
        if (expectedOpts.getMaxAttempts() != 0 && riverJob.maxAttempts() != expectedOpts.getMaxAttempts()) {
            throw new AssertionError(failureString(
                "Job with kind '%s'%s max attempts %d not equal to expected %d",
                riverJob.kind(), positionStr, riverJob.maxAttempts(), expectedOpts.getMaxAttempts()));
        }
        
        if (expectedOpts.getQueue() != null && !Objects.equals(riverJob.queue(), expectedOpts.getQueue())) {
            throw new AssertionError(failureString(
                "Job with kind '%s'%s queue '%s' not equal to expected '%s'",
                riverJob.kind(), positionStr, riverJob.queue(), expectedOpts.getQueue()));
        }
        
        if (expectedOpts.getPriority() != 0 && riverJob.priority() != expectedOpts.getPriority()) {
            throw new AssertionError(failureString(
                "Job with kind '%s'%s priority %d not equal to expected %d",
                riverJob.kind(), positionStr, riverJob.priority(), expectedOpts.getPriority()));
        }
        
        if (expectedOpts.getScheduledAt() != null) {
            // Truncate to millisecond precision for comparison (similar to Go's microsecond truncation)
            Instant actualScheduledAt = riverJob.scheduledAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            Instant expectedScheduledAt = expectedOpts.getScheduledAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            
            if (!actualScheduledAt.equals(expectedScheduledAt)) {
                throw new AssertionError(failureString(
                    "Job with kind '%s'%s scheduled at %s not equal to expected %s",
                    riverJob.kind(), positionStr, actualScheduledAt, expectedScheduledAt));
            }
        }
        
        if (expectedOpts.getState() != null && !Objects.equals(riverJob.state(), expectedOpts.getState())) {
            throw new AssertionError(failureString(
                "Job with kind '%s'%s state '%s' not equal to expected '%s'",
                riverJob.kind(), positionStr, riverJob.state(), expectedOpts.getState()));
        }
        
        if (expectedOpts.getTags() != null) {
            String[] jobTags = riverJob.tags() != null ? riverJob.tags().toArray(new String[0]) : new String[0];
            if (!Arrays.equals(jobTags, expectedOpts.getTags())) {
                throw new AssertionError(failureString(
                    "Job with kind '%s'%s tags %s not equal to expected %s",
                    riverJob.kind(), positionStr, Arrays.toString(jobTags), Arrays.toString(expectedOpts.getTags())));
            }
        }
    }
    
    /**
     * Formats failure messages with River-specific styling.
     */
    private static String failureString(String format, Object... args) {
        return "\n    River assertion failure:\n    " + String.format(format, args) + "\n";
    }
}