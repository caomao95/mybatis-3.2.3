package org.apache.ibatis.executor.keygen;

import java.sql.Statement;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * KeyGenerator 为键生成器。 在我们使用主键自动生成时， 会生成一个对应的主键生成器实例。
 *
 * @author
 */
public interface KeyGenerator {

    /**
     * before key generator 主要用于oracle等使用序列机制的ID生成方式，之前生成主键
     *
     * @param executor
     * @param ms
     * @param stmt
     * @param parameter
     */
    void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

    /**
     * after key generator 主要用于mysql等使用自增机制的ID生成方式
     *
     * @param executor
     * @param ms
     * @param stmt
     * @param parameter
     */
    void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

}
