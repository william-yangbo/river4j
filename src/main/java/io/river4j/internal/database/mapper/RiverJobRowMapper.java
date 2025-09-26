package io.river4j.internal.database.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.river4j.internal.database.model.AttemptError;
import io.river4j.internal.database.model.JobState;
import io.river4j.internal.database.model.RiverJob;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * JDBI row mapper for RiverJob.
 */
public class RiverJobRowMapper implements RowMapper<RiverJob> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public RiverJob map(ResultSet rs, StatementContext ctx) throws SQLException {
        // Handle arrays
        List<String> attemptedBy = mapArray(rs.getArray("attempted_by"));
        List<String> tags = mapArray(rs.getArray("tags"));
        
        // Handle JSONB errors
        List<AttemptError> errors = mapJsonbErrors(rs.getString("errors"));
        
        // Handle timestamps
        Instant attemptedAt = mapTimestamp(rs.getTimestamp("attempted_at"));
        Instant finalizedAt = mapTimestamp(rs.getTimestamp("finalized_at"));
        
        return new RiverJob(
            rs.getLong("id"),
            rs.getBytes("args"),
            rs.getShort("attempt"),
            attemptedAt,
            attemptedBy,
            rs.getTimestamp("created_at").toInstant(),
            errors,
            finalizedAt,
            rs.getString("kind"),
            rs.getShort("max_attempts"),
            rs.getBytes("metadata"),
            rs.getShort("priority"),
            rs.getString("queue"),
            JobState.fromString(rs.getString("state")),
            rs.getTimestamp("scheduled_at").toInstant(),
            tags
        );
    }
    
    @SuppressWarnings("unchecked")
    private List<String> mapArray(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        String[] array = (String[]) sqlArray.getArray();
        return Arrays.asList(array);
    }
    
    private List<AttemptError> mapJsonbErrors(String jsonbValue) {
        if (jsonbValue == null || jsonbValue.isBlank()) {
            return List.of();
        }
        
        try {
            return objectMapper.readValue(jsonbValue, new TypeReference<List<AttemptError>>() {});
        } catch (Exception e) {
            // Log error and return empty list
            return List.of();
        }
    }
    
    private Instant mapTimestamp(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}