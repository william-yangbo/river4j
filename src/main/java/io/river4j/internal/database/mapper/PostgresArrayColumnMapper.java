package io.river4j.internal.database.mapper;

import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * JDBI column mapper for PostgreSQL arrays.
 */
public class PostgresArrayColumnMapper<T> implements ColumnMapper<List<T>> {
    
    @Override
    @SuppressWarnings("unchecked")
    public List<T> map(ResultSet rs, int columnNumber, StatementContext ctx) throws SQLException {
        Array sqlArray = rs.getArray(columnNumber);
        if (sqlArray == null) {
            return List.of();
        }
        
        Object[] array = (Object[]) sqlArray.getArray();
        return Arrays.asList((T[]) array);
    }
}