package io.river4j.internal.database.mapper;

import io.river4j.internal.database.model.JobState;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBI column mapper for JobState enum.
 */
public class JobStateColumnMapper implements ColumnMapper<JobState> {
    
    @Override
    public JobState map(ResultSet rs, int columnNumber, StatementContext ctx) throws SQLException {
        String value = rs.getString(columnNumber);
        return value != null ? JobState.fromString(value) : null;
    }
}