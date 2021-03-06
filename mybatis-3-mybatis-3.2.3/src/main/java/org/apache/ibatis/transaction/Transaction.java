package org.apache.ibatis.transaction;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 包装数据库连接。
 * 处理连接生命周期，包括：创建、准备、提交/回滚和关闭。
 *
 * @author
 */
public interface Transaction {

    /**
     * Retrieve inner database connection
     *
     * @return DataBase connection
     * @throws SQLException
     */
    Connection getConnection() throws SQLException;

    /**
     * Commit inner database connection.
     *
     * @throws SQLException
     */
    void commit() throws SQLException;

    /**
     * Rollback inner database connection.
     *
     * @throws SQLException
     */
    void rollback() throws SQLException;

    /**
     * Close inner database connection.
     *
     * @throws SQLException
     */
    void close() throws SQLException;

}
