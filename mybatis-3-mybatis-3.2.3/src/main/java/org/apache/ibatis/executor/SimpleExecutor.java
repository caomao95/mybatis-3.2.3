package org.apache.ibatis.executor;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

public class SimpleExecutor extends BaseExecutor {

    public SimpleExecutor(Configuration configuration, Transaction transaction) {
        super(configuration, transaction);
    }

    public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
        Statement stmt = null;
        try {
            Configuration configuration = ms.getConfiguration();
            StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
            stmt = prepareStatement(handler, ms.getStatementLog());
            return handler.update(stmt);
        } finally {
            closeStatement(stmt);
        }
    }

    @Override
    public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        Statement stmt = null;
        try {
            Configuration configuration = ms.getConfiguration();
            // 根据上下文参数和具体的执行器new一个StatementHandler, 其中包含了所有必要的信息,比如结果处理器、参数处理器、执行器等等,
            //TODO
            // 主要有三种类型的语句处理器 UNPREPARE、PREPARE、CALLABLE。默认是PREPARE类型，通过mapper语句上的statementType属性进行设置,
            // 一般除了存储过程外不应该设置
            StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, rowBounds, resultHandler, boundSql);

            // 真正JDBC处理，组装statement
            stmt = prepareStatement(handler, ms.getStatementLog());
            // 根据不同的语句处理器执行
            return handler.<E>query(stmt, resultHandler);
        } finally {
            closeStatement(stmt);
        }
    }

    public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
        return Collections.emptyList();
    }

    /**
     * 具体的JDBC处理
     *
     * @param handler
     * @param statementLog
     * @return
     * @throws SQLException
     */
    private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
        Statement stmt;
        // 获取数据库连接
        Connection connection = getConnection(statementLog);

        // 调用语句处理器的prepare方法
        stmt = handler.prepare(connection);

        // 设置参数
        handler.parameterize(stmt);
        return stmt;
    }

}
