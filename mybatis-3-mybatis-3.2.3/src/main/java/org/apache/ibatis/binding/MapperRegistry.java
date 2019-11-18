package org.apache.ibatis.binding;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * mapper 注册器
 *
 * @author
 */
public class MapperRegistry {

    private Configuration config;

    /**
     * 维护Mapper接口和代理类的映射关系，key是mapper接口类，value 是 MapperProxyFactory.
     */
    private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<Class<?>, MapperProxyFactory<?>>();

    public MapperRegistry(Configuration config) {
        this.config = config;
    }

    @SuppressWarnings("unchecked")
    public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
        final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
        if (mapperProxyFactory == null) {
            throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
        }
        try {
            return mapperProxyFactory.newInstance(sqlSession);
        } catch (Exception e) {
            throw new BindingException("Error getting mapper instance. Cause: " + e, e);
        }
    }

    /**
     * 判重出错
     *
     * @param type
     * @param <T>
     * @return
     */
    public <T> boolean hasMapper(Class<T> type) {
        return knownMappers.containsKey(type);
    }

    /**
     * 放入
     *
     * @param type
     * @param <T>
     */
    public <T> void addMapper(Class<T> type) {

        // mybatis mapper 必须是 接口
        if (type.isInterface()) {
            // 判重，确保只会加载一次，不会被覆盖
            if (hasMapper(type)) {
                throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
            }
            boolean loadCompleted = false;
            try {

                // 为 mapper 接口创建一个 MapperProxyFactory 代理, 用于创建MapperProxy对象，
                // MapperProxy 对象主要是为了处理Mapper接口中方法的注解，参数，和返回值。
                knownMappers.put(type, new MapperProxyFactory<T>(type));

                // 初始化 MapperAnnotationBuilder ，真正进行Mapper的解析
                MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
                // 调用builder.parse 进行具体mapper接口文件加载与解析
                parser.parse();

                // 加载成功
                loadCompleted = true;
            } finally {
                // 如果解析异常，从 代理map中移除
                if (!loadCompleted) {
                    knownMappers.remove(type);
                }
            }
        }
    }

    /**
     * @since 3.2.2
     */
    public Collection<Class<?>> getMappers() {
        return Collections.unmodifiableCollection(knownMappers.keySet());
    }

    /**
     * 允许外部调用
     *
     * @param packageName package名
     * @param superType   继承的某个类/某个接口
     */
    public void addMappers(String packageName, Class<?> superType) {
        // mybatis 框架提供的搜索package 以及自 package 中符合条件 （注解或者继承与某个类/接口）的类，
        // 默认使用 Thread.currentThread().getContextClassLoader()返回的加载器。
        ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<Class<?>>();
        // 无条件的加载所有的类，调用放传递了 Object.class作为父类，
        resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
        // 所有匹配的 class 都被存储在 ResolverUtil.matches 字段中。
        Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
        for (Class<?> mapperClass : mapperSet) {
            // 调用 addMapper 方法进行具体的mapper类、接口解析
            addMapper(mapperClass);
        }
    }

    /**
     * 外部调用的入口
     *
     * @since 3.2.2
     */
    public void addMappers(String packageName) {
        addMappers(packageName, Object.class);
    }

}
