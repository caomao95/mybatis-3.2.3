package org.apache.ibatis.transaction;

import java.sql.Connection;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.session.TransactionIsolationLevel;

/**
 * 事物工厂
 * Creates {@link Transaction} instances.
 * <p>
 * 用于 创建 Transaction 对象
 *
 * @author
 */
public interface TransactionFactory {

    /**
     * Sets transaction factory custom properties.
     *
     * @param props
     */
    void setProperties(Properties props);

    /**
     * Creates a {@link Transaction} out of an existing connection.
     *
     * @param conn Existing database connection
     * @return Transaction
     * @since 3.1.0
     */
    Transaction newTransaction(Connection conn);

    /**
     * Creates a {@link Transaction} out of a datasource.
     *
     * @param dataSource DataSource to take the connection from
     * @param level      Desired isolation level
     * @param autoCommit Desired autocommit
     * @return Transaction
     * @since 3.1.0
     */
    Transaction newTransaction(DataSource dataSource, TransactionIsolationLevel level, boolean autoCommit);

}
