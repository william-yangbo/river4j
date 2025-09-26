package io.river4j.internal.database.mapper;

import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;

import java.sql.Array;
import java.sql.Types;
import java.util.List;

/**
 * JDBI argument factory for PostgreSQL arrays.
 */
public class PostgresArrayArgumentFactory extends AbstractArgumentFactory<List<?>> {
    
    public PostgresArrayArgumentFactory() {
        super(Types.ARRAY);
    }
    
    @Override
    public Argument build(List<?> value, ConfigRegistry config) {
        return (position, statement, ctx) -> {
            if (value == null || value.isEmpty()) {
                statement.setNull(position, Types.ARRAY);
            } else {
                // Convert List to Array based on element type
                Object[] array = value.toArray();
                String typeName = determineArrayType(array);
                Array sqlArray = statement.getConnection().createArrayOf(typeName, array);
                statement.setArray(position, sqlArray);
            }
        };
    }
    
    private String determineArrayType(Object[] array) {
        if (array.length == 0) {
            return "text";
        }
        
        Object first = array[0];
        if (first instanceof String) {
            return "text";
        } else if (first instanceof Long) {
            return "bigint";
        } else if (first instanceof Integer) {
            return "integer";
        } else {
            return "text"; // fallback
        }
    }
}