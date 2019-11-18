package org.apache.ibatis.executor;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 缓存执行器
 *
 * @author
 */
public class CachingExecutor implements Executor {

    private Executor delegate;
    private boolean autoCommit;
    private TransactionalCacheManager tcm = new TransactionalCacheManager();

    /**
     * 是否禁用对次回话使用缓存数据，在清理缓存之后就设置为true，表示禁用缓存
     */
    private boolean dirty;

    public CachingExecutor(Executor delegate) {
        this(delegate, false);
    }

    public CachingExecutor(Executor delegate, boolean autoCommit) {
        this.delegate = delegate;
        this.autoCommit = autoCommit;
    }

    public Transaction getTransaction() {
        return delegate.getTransaction();
    }

    public void close(boolean forceRollback) {
        try {
            if (dirty && !autoCommit) {
                tcm.rollback();
            } else {
                tcm.commit();
            }
        } finally {
            delegate.close(forceRollback);
        }
    }

    public boolean isClosed() {
        return delegate.isClosed();
    }

    public int update(MappedStatement ms, Object parameterObject) throws SQLException {
        flushCacheIfRequired(ms);
        return delegate.update(ms, parameterObject);
    }

    public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameterObject);
        CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);
        return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
    }

    /**
     * 查询， 如果使用缓存，1. 判断是否需要刷新缓存，如果刷新了缓存，则不从缓存中获取数据。如果使用了缓存，则将查询结果放入缓存中。
     *
     * @param ms
     * @param parameterObject
     * @param rowBounds
     * @param resultHandler
     * @param key
     * @param boundSql
     * @param <E>
     * @return
     * @throws SQLException
     */
    public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
        Cache cache = ms.getCache();
        if (cache != null) {
            // 刷新缓存
            flushCacheIfRequired(ms);
            if (ms.isUseCache() && resultHandler == null) {
                // 当为存储过程时的判断：不支持使用OUT params 缓存存储过程，请在中配置useCache=false
                ensureNoOutParams(ms, parameterObject, boundSql);
                if (!dirty) {
                    cache.getReadWriteLock().readLock().lock();
                    try {
                        @SuppressWarnings("unchecked")
                        List<E> cachedList = (List<E>) cache.getObject(key);
                        if (cachedList != null) {
                            return cachedList;
                        }
                    } finally {
                        cache.getReadWriteLock().readLock().unlock();
                    }
                }
                List<E> list = delegate.<E>query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
                tcm.putObject(cache, key, list);
                return list;
            }
        }
        return delegate.<E>query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
    }

    public List<BatchResult> flushStatements() throws SQLException {
        return delegate.flushStatements();
    }

    public void commit(boolean required) throws SQLException {
        delegate.commit(required);
        tcm.commit();
        dirty = false;
    }

    public void rollback(boolean required) throws SQLException {
        try {
            delegate.rollback(required);
            dirty = false;
        } finally {
            if (required) {
                tcm.rollback();
            }
        }
    }

    private void ensureNoOutParams(MappedStatement ms, Object parameter, BoundSql boundSql) {
        if (ms.getStatementType() == StatementType.CALLABLE) {
            for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
                if (parameterMapping.getMode() != ParameterMode.IN) {
                    throw new ExecutorException("Caching stored procedures with OUT params is not supported.  Please configure useCache=false in " + ms.getId() + " statement.");
                }
            }
        }
    }

    public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
        return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
    }

    public boolean isCached(MappedStatement ms, CacheKey key) {
        throw new UnsupportedOperationException("The CachingExecutor should not be used by result loaders and thus isCached() should never be called.");
    }

    public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
        throw new UnsupportedOperationException("The CachingExecutor should not be used by result loaders and thus deferLoad() should never be called.");
    }

    public void clearLocalCache() {
        delegate.clearLocalCache();
    }

    private void flushCacheIfRequired(MappedStatement ms) {
        Cache cache = ms.getCache();
        if (cache != null && ms.isFlushCacheRequired()) {
            dirty = true; // issue #524. Disable using cached data for this session
            tcm.clear(cache);
        }
    }

}
