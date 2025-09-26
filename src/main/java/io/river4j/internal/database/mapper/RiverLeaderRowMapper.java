package io.river4j.internal.database.mapper;

import io.river4j.internal.database.model.RiverLeader;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBI row mapper for RiverLeader.
 */
public class RiverLeaderRowMapper implements RowMapper<RiverLeader> {
    
    @Override
    public RiverLeader map(ResultSet rs, StatementContext ctx) throws SQLException {
        return new RiverLeader(
            rs.getTimestamp("elected_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getString("leader_id"),
            rs.getString("name")
        );
    }
}