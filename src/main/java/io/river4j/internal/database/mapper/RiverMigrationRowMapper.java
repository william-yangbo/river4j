package io.river4j.internal.database.mapper;

import io.river4j.internal.database.model.RiverMigration;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBI row mapper for RiverMigration.
 */
public class RiverMigrationRowMapper implements RowMapper<RiverMigration> {
    
    @Override
    public RiverMigration map(ResultSet rs, StatementContext ctx) throws SQLException {
        Long id = rs.getObject("id", Long.class);
        return new RiverMigration(
            id,
            rs.getLong("version"),
            rs.getString("name"),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}