package org.apache.ibatis.session;

import java.sql.Connection;

/**
 * 数据库事物隔离级别
 *
 * @author
 */
public enum TransactionIsolationLevel {


    NONE(Connection.TRANSACTION_NONE),

    /**
     * 读提交
     */
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),

    /**
     * 读未提交
     */
    READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),

    /**
     * 可重复读
     */
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),

    /**
     * 序列化
     */
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

    private final int level;

    private TransactionIsolationLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
