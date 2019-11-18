package org.apache.ibatis.reflection.factory;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.ibatis.reflection.ReflectionException;

/**
 * 默认的对象工厂，仅仅是用于实例化目标类，
 * 两种方式实例化目标类：默认的构造函数 和 带参数的构造函数。
 *
 * @author
 */
public class DefaultObjectFactory implements ObjectFactory, Serializable {

    private static final long serialVersionUID = -8855120656740914948L;

    @Override
    public <T> T create(Class<T> type) {
        return create(type, null, null);
    }

    @Override
    public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        // 传入集合接口时，返回默认的实现
        Class<?> classToCreate = resolveInterface(type);
        @SuppressWarnings("unchecked")
        T created = (T) instantiateClass(classToCreate, constructorArgTypes, constructorArgs);
        return created;
    }

    @Override
    public void setProperties(Properties properties) {
        // no props for default
    }

    private <T> T instantiateClass(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        try {
            Constructor<T> constructor;

            // 通过无参构造函数创建实例
            if (constructorArgTypes == null || constructorArgs == null) {
                // 获取构造器
                constructor = type.getDeclaredConstructor();

                //判断构造函数是否为不可见（private），如果是private，需要设置为true，否则抛出
                //java.lang.IllegalAccessException: Class （类的全限定名）
                // can not access a member of class （类的全限定名） with modifiers "private"
                //	at sun.reflect.Reflection.ensureMemberAccess(Reflection.java:102)
                if (!constructor.isAccessible()) {
                    constructor.setAccessible(true);
                }
                // 通过无参构造函数获取实例
                return constructor.newInstance();
            }

            // 通过有参构造函数创建实例， constructorArgTypes 和 constructorArgs 中的每一个值的顺序必须一一对应
            constructor = type.getDeclaredConstructor(constructorArgTypes.toArray(new Class[constructorArgTypes.size()]));
            if (!constructor.isAccessible()) {
                constructor.setAccessible(true);
            }
            return constructor.newInstance(constructorArgs.toArray(new Object[constructorArgs.size()]));
        } catch (Exception e) {
            StringBuilder argTypes = new StringBuilder();
            if (constructorArgTypes != null) {
                for (Class<?> argType : constructorArgTypes) {
                    argTypes.append(argType.getSimpleName());
                    argTypes.append(",");
                }
            }
            StringBuilder argValues = new StringBuilder();
            if (constructorArgs != null) {
                for (Object argValue : constructorArgs) {
                    argValues.append(argValue);
                    argValues.append(",");
                }
            }
            throw new ReflectionException("Error instantiating " + type + " with invalid types (" + argTypes + ") or values (" + argValues + "). Cause: " + e, e);
        }
    }

    protected Class<?> resolveInterface(Class<?> type) {
        Class<?> classToCreate;
        if (type == List.class || type == Collection.class || type == Iterable.class) {
            classToCreate = ArrayList.class;
        } else if (type == Map.class) {
            classToCreate = HashMap.class;
        } else if (type == SortedSet.class) {
            classToCreate = TreeSet.class;
        } else if (type == Set.class) {
            classToCreate = HashSet.class;
        } else {
            classToCreate = type;
        }
        return classToCreate;
    }

    /**
     * 实例的时候使用 instanceof ，类或接口时使用 isAssignableFrom()方法
     *
     * @param type Object type
     * @param <T>
     * @return
     */
    @Override
    public <T> boolean isCollection(Class<T> type) {
        // isAssignableFrom() 方法从类继承的角度去判断，instanceof关键字是从实例继承的角度去判断。
        // isAssignableFrom() 方法是判断是否为某个类 的父类， instanceof 是判断是否为某个类的子类。

        // 父类.class.isAssignableFrom(子类.class)
        // 子类实例 instanceof 父类类型
        return Collection.class.isAssignableFrom(type);
    }

}
