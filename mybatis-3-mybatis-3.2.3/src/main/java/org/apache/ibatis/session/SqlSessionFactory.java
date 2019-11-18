package org.apache.ibatis.session;

import java.sql.Connection;

/**
 * @author
 */
public interface SqlSessionFactory {

    SqlSession openSession();

    /**
     * 获取一个可定义是否自动提交的sqlSession
     *
     * @param autoCommit 是否自动提交
     * @return
     */
    SqlSession openSession(boolean autoCommit);

    SqlSession openSession(Connection connection);

    SqlSession openSession(TransactionIsolationLevel level);

    SqlSession openSession(ExecutorType execType);

    SqlSession openSession(ExecutorType execType, boolean autoCommit);

    SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level);

    SqlSession openSession(ExecutorType execType, Connection connection);

    Configuration getConfiguration();

}
