package org.apache.ibatis.reflection.wrapper;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectionException;

/**
 * 默认的对象包装器工厂
 *
 * @author
 */
public class DefaultObjectWrapperFactory implements ObjectWrapperFactory {

    public boolean hasWrapperFor(Object object) {
        return false;
    }

    public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
        throw new ReflectionException("The DefaultObjectWrapperFactory should never be called to provide an ObjectWrapper.");
    }

}
