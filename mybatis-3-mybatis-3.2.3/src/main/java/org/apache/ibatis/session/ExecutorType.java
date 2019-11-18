package org.apache.ibatis.session;

/**
 * 执行器类型
 *
 * @author
 */
public enum ExecutorType {

    /**
     * 简单执行器，为每个语句的执行创建一个新的预处理语句
     */
    SIMPLE,

    /**
     * 复用预处理语句
     */
    REUSE,

    /**
     * 批量执行所有的更新语句
     */
    BATCH
}
