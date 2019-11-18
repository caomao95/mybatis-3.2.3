package org.apache.ibatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;

/**
 * @author
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CacheNamespace {
    Class<? extends org.apache.ibatis.cache.Cache> implementation() default PerpetualCache.class;

    Class<? extends org.apache.ibatis.cache.Cache> eviction() default LruCache.class;

    long flushInterval() default 3600000;

    int size() default 1000;

    boolean readWrite() default true;
}
