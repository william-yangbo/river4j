/**
 * Database utilities package providing transaction management and database abstractions.
 * 
 * <p>This package is the Java equivalent of Go's dbutil package, providing:
 * <ul>
 *   <li>{@link io.river4j.internal.util.dbutil.DBTX} - Database transaction interface</li>
 *   <li>{@link io.river4j.internal.util.dbutil.TxBeginner} - Transaction starter interface</li>
 *   <li>{@link io.river4j.internal.util.dbutil.Executor} - Combined transaction and query interface</li>
 *   <li>{@link io.river4j.internal.util.dbutil.DbUtil} - Transaction management utilities</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Simple Transaction</h3>
 * <pre>{@code
 * DatabaseManager dbManager = // ... get database manager
 * DatabaseManagerExecutor executor = new DatabaseManagerExecutor(dbManager);
 * 
 * DbUtil.withTx(executor, connection -> {
 *     // Your database operations here
 *     try (var stmt = connection.createStatement()) {
 *         stmt.executeUpdate("INSERT INTO table VALUES (1, 'test')");
 *     }
 * });
 * }</pre>
 * 
 * <h3>Transaction with Return Value</h3>
 * <pre>{@code
 * DatabaseManager dbManager = // ... get database manager
 * DatabaseManagerExecutor executor = new DatabaseManagerExecutor(dbManager);
 * 
 * Integer count = DbUtil.withTxV(executor, connection -> {
 *     try (var stmt = connection.createStatement();
 *          var rs = stmt.executeQuery("SELECT COUNT(*) FROM table")) {
 *         return rs.next() ? rs.getInt(1) : 0;
 *     }
 * });
 * }</pre>
 * 
 * <h3>Direct Connection Usage</h3>
 * <pre>{@code
 * try (Connection connection = dataSource.getConnection()) {
 *     DbUtil.withTx(connection, conn -> {
 *         // Your database operations here
 *     });
 * }
 * }</pre>
 * 
 * @since 1.0
 */
package io.river4j.internal.util.dbutil;