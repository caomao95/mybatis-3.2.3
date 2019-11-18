package org.apache.ibatis.binding;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.session.SqlSession;

/**
 * Mapper代理的工厂，主要是维护 mapper 接口的方法与对应mapper文件中具体 CRUD 节点的关联关系，其中每个Method 与对应
 * MapperMethod维护在一起。
 * MapperMethod是mapper中具体映射语句节点的内部表示。
 * 首先为mapper接口创建MapperProxyFactory，然后创建MapperAnnotationBuilder进行具体的解析。
 * <p>
 * MapperAnnotationBuilder 在解析前的构造器中完成了下列工作。
 *
 * @param <T>
 * @author
 */
public class MapperProxyFactory<T> {

    private final Class<T> mapperInterface;
    private Map<Method, MapperMethod> methodCache = new ConcurrentHashMap<Method, MapperMethod>();

    public MapperProxyFactory(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    public Class<T> getMapperInterface() {
        return mapperInterface;
    }

    public Map<Method, MapperMethod> getMethodCache() {
        return methodCache;
    }

    @SuppressWarnings("unchecked")
    protected T newInstance(MapperProxy<T> mapperProxy) {
        // JDK 动态代理
        return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[]{mapperInterface}, mapperProxy);
    }

    public T newInstance(SqlSession sqlSession) {
        final MapperProxy<T> mapperProxy = new MapperProxy<T>(sqlSession, mapperInterface, methodCache);
        return newInstance(mapperProxy);
    }

}
