package io.river4j.internal.database.dao;

import io.river4j.internal.database.model.JobState;
import io.river4j.internal.database.model.RiverJob;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * River job data access object.
 * Corresponds to the queries in river_job.sql from dbsqlc.
 */
public interface JobDao extends SqlObject {

    /**
     * Complete many jobs at once.
     * Corresponds to JobCompleteMany in Go.
     */
    @SqlBatch("UPDATE river_job SET finalized_at = :finalizedAt, state = 'completed' WHERE id = :id")
    int[] completeMany(@Bind("id") List<Long> ids, @Bind("finalizedAt") List<Instant> finalizedAts);

    /**
     * Count running jobs.
     * Corresponds to JobCountRunning in Go.
     */
    @SqlQuery("SELECT count(*) FROM river_job WHERE state = 'running'")
    long countRunning();

    /**
     * Delete jobs before certain time horizons.
     * Corresponds to JobDeleteBefore in Go.
     */
    @SqlQuery("""
        WITH deleted_jobs AS (
          DELETE FROM river_job
          WHERE id IN (
            SELECT id FROM river_job
            WHERE 
              (state = 'cancelled' AND finalized_at < :cancelledHorizon) OR
              (state = 'completed' AND finalized_at < :completedHorizon) OR
              (state = 'discarded' AND finalized_at < :discardedHorizon)
            ORDER BY id
            LIMIT :maxCount
          )
          RETURNING id
        )
        SELECT count(*) FROM deleted_jobs
        """)
    long deleteBefore(
        @Bind("cancelledHorizon") Instant cancelledHorizon,
        @Bind("completedHorizon") Instant completedHorizon,
        @Bind("discardedHorizon") Instant discardedHorizon,
        @Bind("maxCount") long maxCount
    );

    /**
     * Get available jobs for processing.
     * Corresponds to JobGetAvailable in Go.
     */
    @SqlQuery("""
        WITH locked_jobs AS (
          SELECT * FROM river_job
          WHERE state = 'available'
            AND queue = ANY(:queues)
            AND scheduled_at <= :now
          ORDER BY priority DESC, scheduled_at, id
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
        )
        UPDATE river_job SET
          state = 'running',
          attempt = attempt + 1,
          attempted_at = :now,
          attempted_by = array_append(river_job.attempted_by, :workerName)
        FROM locked_jobs
        WHERE river_job.id = locked_jobs.id
        RETURNING river_job.*
        """)
    List<RiverJob> getAvailable(
        @BindList("queues") List<String> queues,
        @Bind("limit") int limit,
        @Bind("now") Instant now,
        @Bind("workerName") String workerName
    );

    /**
     * Get job by ID.
     * Corresponds to JobGetByID in Go.
     */
    @SqlQuery("SELECT * FROM river_job WHERE id = :id")
    Optional<RiverJob> findById(@Bind("id") long id);

    /**
     * Get multiple jobs by IDs.
     * Corresponds to JobGetByIDMany in Go.
     */
    @SqlQuery("SELECT * FROM river_job WHERE id = ANY(:ids)")
    List<RiverJob> findByIds(@BindList("ids") List<Long> ids);

    /**
     * Get jobs by kind.
     * Corresponds to JobGetByKind in Go.
     */
    @SqlQuery("SELECT * FROM river_job WHERE kind = :kind ORDER BY id")
    List<RiverJob> findByKind(@Bind("kind") String kind);

    /**
     * Get jobs by multiple kinds.
     * Corresponds to JobGetByKindMany in Go.
     */
    @SqlQuery("SELECT * FROM river_job WHERE kind = ANY(:kinds) ORDER BY id")
    List<RiverJob> findByKinds(@BindList("kinds") List<String> kinds);

    /**
     * Insert a new job.
     * Simplified version for common use cases.
     */
    @SqlUpdate("""
        INSERT INTO river_job 
        (kind, args, queue, priority, scheduled_at, max_attempts, metadata, tags)
        VALUES (:kind, :args, :queue, :priority, :scheduledAt, :maxAttempts, :metadata, :tags)
        """)
    int insert(
        @Bind("kind") String kind,
        @Bind("args") byte[] args,
        @Bind("queue") String queue,
        @Bind("priority") short priority,
        @Bind("scheduledAt") Instant scheduledAt,
        @Bind("maxAttempts") short maxAttempts,
        @Bind("metadata") byte[] metadata,
        @BindList("tags") List<String> tags
    );

    /**
     * Update job state.
     */
    @SqlUpdate("UPDATE river_job SET state = :state WHERE id = :id")
    int updateState(@Bind("id") long id, @Bind("state") JobState state);

    /**
     * Cancel job.
     * Corresponds to JobCancel in Go.
     */
    @SqlUpdate("""
        UPDATE river_job SET 
          state = 'cancelled',
          finalized_at = :finalizedAt
        WHERE id = :id AND state NOT IN ('cancelled', 'completed', 'discarded')
        """)
    int cancel(@Bind("id") long id, @Bind("finalizedAt") Instant finalizedAt);

    /**
     * Retry job.
     * Corresponds to JobRetry in Go.
     */
    @SqlUpdate("""
        UPDATE river_job SET
          state = CASE 
            WHEN scheduled_at <= :now THEN 'available'::river_job_state 
            ELSE 'scheduled'::river_job_state 
          END,
          max_attempts = :maxAttempts
        WHERE id = :id
        """)
    int retry(@Bind("id") long id, @Bind("maxAttempts") short maxAttempts, @Bind("now") Instant now);

    /**
     * Get stuck jobs (running too long).
     * Corresponds to JobGetStuck in Go.
     */
    @SqlQuery("""
        SELECT * FROM river_job
        WHERE state = 'running'
          AND attempted_at < :stuckHorizon
        ORDER BY id
        LIMIT :limit
        """)
    List<RiverJob> getStuck(@Bind("stuckHorizon") Instant stuckHorizon, @Bind("limit") int limit);

    /**
     * Rescue stuck jobs.
     * Corresponds to JobRescueMany in Go.
     */
    @SqlUpdate("""
        UPDATE river_job SET
          state = 'available',
          scheduled_at = :now
        WHERE id = ANY(:ids)
        """)
    int rescueMany(@BindList("ids") List<Long> ids, @Bind("now") Instant now);
}