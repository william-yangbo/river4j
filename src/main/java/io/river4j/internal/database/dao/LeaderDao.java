package io.river4j.internal.database.dao;

import io.river4j.internal.database.model.RiverLeader;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * River leader data access object for leader election.
 * Corresponds to the queries in river_leader.sql from dbsqlc.
 */
public interface LeaderDao extends SqlObject {

    /**
     * Delete expired leaders.
     * Corresponds to LeaderDeleteExpired in Go.
     */
    @SqlQuery("""
        WITH deleted_leaders AS (
          DELETE FROM river_leader 
          WHERE expires_at < :now
          RETURNING *
        )
        SELECT count(*) FROM deleted_leaders
        """)
    long deleteExpired(@Bind("now") Instant now);

    /**
     * Get current leader.
     * Corresponds to LeaderGetElectedLeader in Go.
     */
    @SqlQuery("""
        SELECT * FROM river_leader 
        WHERE name = :name 
          AND expires_at > :now
        ORDER BY elected_at DESC 
        LIMIT 1
        """)
    Optional<RiverLeader> getElectedLeader(@Bind("name") String name, @Bind("now") Instant now);

    /**
     * Insert or update leader election.
     * Corresponds to LeaderInsert in Go.
     */
    @SqlUpdate("""
        INSERT INTO river_leader (elected_at, expires_at, leader_id, name)
        VALUES (:electedAt, :expiresAt, :leaderID, :name)
        ON CONFLICT (name) DO UPDATE SET
          elected_at = EXCLUDED.elected_at,
          expires_at = EXCLUDED.expires_at,
          leader_id = EXCLUDED.leader_id
        """)
    int insertOrUpdate(
        @Bind("electedAt") Instant electedAt,
        @Bind("expiresAt") Instant expiresAt,
        @Bind("leaderID") String leaderID,
        @Bind("name") String name
    );

    /**
     * Attempt to elect as leader.
     * Corresponds to LeaderAttemptElect in Go.
     */
    @SqlUpdate("""
        INSERT INTO river_leader (elected_at, expires_at, leader_id, name)
        VALUES (:electedAt, :expiresAt, :leaderID, :name)
        ON CONFLICT (name) DO UPDATE SET
          elected_at = EXCLUDED.elected_at,
          expires_at = EXCLUDED.expires_at,
          leader_id = EXCLUDED.leader_id
        WHERE river_leader.expires_at < :electedAt
        """)
    int attemptElect(
        @Bind("electedAt") Instant electedAt,
        @Bind("expiresAt") Instant expiresAt,
        @Bind("leaderID") String leaderID,
        @Bind("name") String name
    );

    /**
     * Resign leadership.
     * Corresponds to LeaderResign in Go.
     */
    @SqlUpdate("""
        UPDATE river_leader SET
          expires_at = :now
        WHERE name = :name 
          AND leader_id = :leaderID
          AND expires_at > :now
        """)
    int resign(
        @Bind("name") String name,
        @Bind("leaderID") String leaderID,
        @Bind("now") Instant now
    );

    /**
     * Get all current leaders.
     */
    @SqlQuery("""
        SELECT * FROM river_leader 
        WHERE expires_at > :now
        ORDER BY name
        """)
    List<RiverLeader> getAllActive(@Bind("now") Instant now);
}