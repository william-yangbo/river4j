package io.river4j.internal.util.dbutil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Simple implementation of TxBeginner using a DataSource.
 * This provides a basic way to begin transactions from a connection pool.
 */
public class DataSourceTxBeginner implements TxBeginner {
    
    private final DataSource dataSource;
    
    public DataSourceTxBeginner(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public Connection beginTx() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }
}