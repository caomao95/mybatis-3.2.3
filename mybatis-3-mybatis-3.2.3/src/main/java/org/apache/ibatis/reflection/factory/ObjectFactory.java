package org.apache.ibatis.reflection.factory;

import java.util.List;
import java.util.Properties;

/**
 * MyBatis uses an ObjectFactory to create all needed new Objects.
 * <p>
 * Mybatis 对象工厂的顶级接口
 * <p>
 * 无论是创建集合类型、Map类型还是其他类型，都是同样的处理方式，如果想要覆盖对象工厂的默认行为，则可以通过创建自己的对象工厂来实现。
 * ObjectFactory 接口很简单，它包含两个创建用的方法，一个是处理默认构造方式。另一个是处理带参数的构造方法。
 * 最后 {@link ObjectFactory#setProperties(Properties p)} 可以被用来配置 ObjectFactory ，在初始化ObjectFactory 实例后，ObjectFactory
 * 元素提中定义的属性会被传递给setProperties方法。
 * <p>
 * MyBatis 每次创建结果对象的新实例时，都会使用一个对象工厂（ObjectFactory）实例来完成。
 * 默认的对象工厂DefaultObjectFactory仅仅是实例化目标类，要么通过默认构造方法，要么在参数映射存在的时候通过参数构造方法来实例化。
 * <p>
 * 如果想覆盖对象工厂的默认行为比如给某些属性设置默认值，则可以通过创建自己的对象工厂来实现。可以 自己实现 ObjectFactory 接口，也可继承 DefaultObjectFactory 类。
 *
 * @author
 */
public interface ObjectFactory {

    /**
     * Sets configuration properties.
     *
     * @param properties configuration properties
     */
    void setProperties(Properties properties);

    /**
     * 使用默认的构造方式创建对象
     *
     * @param type Object type
     * @return
     */
    <T> T create(Class<T> type);

    /**
     * 使用带参数的构造方法创建对象
     *
     * @param type                Object type
     * @param constructorArgTypes Constructor argument types
     * @param constructorArgs     Constructor argument values
     * @return
     */
    <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs);

    /**
     * Returns true if this object can have a set of other objects.
     * It's main purpose is to support non-java.util.Collection objects like Scala collections.
     *
     * @param type Object type
     * @return whether it is a collection or not
     * @since 3.1.0
     */
    <T> boolean isCollection(Class<T> type);

}
