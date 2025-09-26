package io.river4j.internal.database.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.river4j.internal.database.mapper.JobStateArgumentFactory;
import io.river4j.internal.database.mapper.JobStateColumnMapper;
import io.river4j.internal.database.mapper.PostgresArrayArgumentFactory;
import io.river4j.internal.database.mapper.PostgresArrayColumnMapper;
import io.river4j.internal.database.mapper.RiverJobRowMapper;
import io.river4j.internal.database.mapper.RiverLeaderRowMapper;
import io.river4j.internal.database.mapper.RiverMigrationRowMapper;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.jackson2.Jackson2Plugin;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import javax.sql.DataSource;

/**
 * Database configuration for River4J.
 * Sets up JDBI with PostgreSQL support and custom mappers.
 */
public class DatabaseConfig {
    
    private final DataSource dataSource;
    private final Jdbi jdbi;
    
    public DatabaseConfig(String jdbcUrl, String username, String password) {
        this.dataSource = createDataSource(jdbcUrl, username, password);
        this.jdbi = createJdbi(dataSource);
    }
    
    public DatabaseConfig(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbi = createJdbi(dataSource);
    }
    
    private DataSource createDataSource(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        
        // PostgreSQL optimizations
        config.setDriverClassName("org.postgresql.Driver");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        // Connection pool settings
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        
        return new HikariDataSource(config);
    }
    
    private Jdbi createJdbi(DataSource dataSource) {
        Jdbi jdbi = Jdbi.create(dataSource);
        
        // Install plugins
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.installPlugin(new Jackson2Plugin());
        
        // Configure Jackson for JSON handling - will be configured separately if needed
        
        // Register custom mappers and argument factories
        jdbi.registerRowMapper(new RiverJobRowMapper());
        jdbi.registerRowMapper(new RiverLeaderRowMapper());
        jdbi.registerRowMapper(new RiverMigrationRowMapper());
        
        jdbi.registerColumnMapper(new JobStateColumnMapper());
        jdbi.registerColumnMapper(new PostgresArrayColumnMapper<>());
        
        jdbi.registerArgument(new JobStateArgumentFactory());
        jdbi.registerArgument(new PostgresArrayArgumentFactory());
        
        return jdbi;
    }
    
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    
    public Jdbi getJdbi() {
        return jdbi;
    }
    
    public DataSource getDataSource() {
        return dataSource;
    }
    
    public void close() {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }
}