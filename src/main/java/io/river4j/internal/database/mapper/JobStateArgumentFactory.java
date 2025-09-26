package io.river4j.internal.database.mapper;

import io.river4j.internal.database.model.JobState;
import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;

import java.sql.Types;

/**
 * JDBI argument factory for JobState enum.
 */
public class JobStateArgumentFactory extends AbstractArgumentFactory<JobState> {
    
    public JobStateArgumentFactory() {
        super(Types.OTHER);
    }
    
    @Override
    public Argument build(JobState value, ConfigRegistry config) {
        return (position, statement, ctx) -> {
            if (value == null) {
                statement.setNull(position, Types.OTHER);
            } else {
                statement.setObject(position, value.getValue(), Types.OTHER);
            }
        };
    }
}