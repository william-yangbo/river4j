package io.river4j.internal.database;

import io.river4j.internal.database.model.JobState;
import io.river4j.internal.database.model.RiverJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Test class for database layer functionality.
 * Tests basic model creation and validation.
 */
class DatabaseModelTest {

    @Test
    void testJobStateEnum() {
        assertEquals("available", JobState.AVAILABLE.getValue());
        assertEquals("running", JobState.RUNNING.getValue());
        assertEquals("completed", JobState.COMPLETED.getValue());
        
        assertEquals(JobState.AVAILABLE, JobState.fromString("available"));
        assertEquals(JobState.RUNNING, JobState.fromString("running"));
        
        assertThrows(IllegalArgumentException.class, () -> JobState.fromString("invalid"));
    }

    @Test
    void testRiverJobCreation() {
        Instant now = Instant.now();
        
        RiverJob job = new RiverJob(
            1L,
            "{}".getBytes(),
            (short) 0,
            null,
            List.of(),
            now,
            List.of(),
            null,
            "TestJob",
            (short) 3,
            "{}".getBytes(),
            (short) 1,
            "default",
            JobState.AVAILABLE,
            now,
            List.of("tag1", "tag2")
        );
        
        assertEquals(1L, job.id());
        assertEquals("TestJob", job.kind());
        assertEquals("default", job.queue());
        assertEquals(JobState.AVAILABLE, job.state());
        assertEquals(2, job.tags().size());
        assertTrue(job.isAvailable());
        assertFalse(job.isFinalized());
    }

    @Test
    void testRiverJobValidation() {
        Instant now = Instant.now();
        
        // Test null kind validation
        assertThrows(IllegalArgumentException.class, () -> 
            new RiverJob(1L, "{}".getBytes(), (short) 0, null, List.of(), now, List.of(),
                        null, null, (short) 3, "{}".getBytes(), (short) 1, "default",
                        JobState.AVAILABLE, now, List.of())
        );
        
        // Test invalid priority validation
        assertThrows(IllegalArgumentException.class, () -> 
            new RiverJob(1L, "{}".getBytes(), (short) 0, null, List.of(), now, List.of(),
                        null, "TestJob", (short) 3, "{}".getBytes(), (short) 5, "default",
                        JobState.AVAILABLE, now, List.of())
        );
        
        // Test invalid max attempts validation
        assertThrows(IllegalArgumentException.class, () -> 
            new RiverJob(1L, "{}".getBytes(), (short) 0, null, List.of(), now, List.of(),
                        null, "TestJob", (short) 0, "{}".getBytes(), (short) 1, "default",
                        JobState.AVAILABLE, now, List.of())
        );
    }

    @Test
    void testRiverJobStates() {
        Instant now = Instant.now();
        
        // Test available job
        RiverJob availableJob = new RiverJob(
            1L, "{}".getBytes(), (short) 0, null, List.of(), now, List.of(),
            null, "TestJob", (short) 3, "{}".getBytes(), (short) 1, "default",
            JobState.AVAILABLE, now.minusSeconds(60), List.of()
        );
        
        assertTrue(availableJob.isAvailable());
        assertFalse(availableJob.isFinalized());
        
        // Test completed job
        RiverJob completedJob = new RiverJob(
            2L, "{}".getBytes(), (short) 1, now, List.of("worker1"), now, List.of(),
            now, "TestJob", (short) 3, "{}".getBytes(), (short) 1, "default",
            JobState.COMPLETED, now.minusSeconds(60), List.of()
        );
        
        assertFalse(completedJob.isAvailable());
        assertTrue(completedJob.isFinalized());
        
        // Test scheduled job (future)
        RiverJob scheduledJob = new RiverJob(
            3L, "{}".getBytes(), (short) 0, null, List.of(), now, List.of(),
            null, "TestJob", (short) 3, "{}".getBytes(), (short) 1, "default",
            JobState.AVAILABLE, now.plusSeconds(60), List.of()
        );
        
        assertFalse(scheduledJob.isAvailable()); // Not available yet (scheduled in future)
        assertFalse(scheduledJob.isFinalized());
    }
}