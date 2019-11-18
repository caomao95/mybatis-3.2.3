package org.apache.ibatis.builder.annotation;

import java.lang.reflect.Method;

/**
 * 解析方法
 *
 * @author
 */
public class MethodResolver {
    private final MapperAnnotationBuilder annotationBuilder;
    private Method method;

    public MethodResolver(MapperAnnotationBuilder annotationBuilder, Method method) {
        this.annotationBuilder = annotationBuilder;
        this.method = method;
    }

    public void resolve() {
        annotationBuilder.parseStatement(method);
    }

}