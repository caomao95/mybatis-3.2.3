package org.apache.ibatis.plugin;

import java.util.Properties;

/**
 * mybatis插件的基础接口，所有mybatis的插件必须实现该接口
 *
 * @author
 */
public interface Interceptor {

    Object intercept(Invocation invocation) throws Throwable;

    Object plugin(Object target);

    void setProperties(Properties properties);

}
