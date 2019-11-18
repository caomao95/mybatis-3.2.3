package org.apache.ibatis.reflection.wrapper;

import org.apache.ibatis.reflection.MetaObject;

/**
 * 对象包装器工厂顶级接口
 * 是一个对象包装器工厂,用于对返回的结果对象进行二次处理,
 * 它主要在 org.apache.ibatis.executor.resultset.DefaultResultSetHandler.getRowValue
 * 方法中创建对象的 MetaObject 时作为参数设置进去,这样MetaObject中的objectWrapper属性就可以被设置为我们自定义的ObjectWrapper实现而不是mybatis内置实现
 *
 * @author
 */
public interface ObjectWrapperFactory {

    boolean hasWrapperFor(Object object);

    ObjectWrapper getWrapperFor(MetaObject metaObject, Object object);

}
