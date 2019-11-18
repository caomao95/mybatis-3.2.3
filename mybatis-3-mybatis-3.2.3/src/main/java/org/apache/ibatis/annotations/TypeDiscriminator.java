package org.apache.ibatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * 类型鉴别器
 *
 * @author
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TypeDiscriminator {
    String column();

    Class<?> javaType() default void.class;

    JdbcType jdbcType() default JdbcType.UNDEFINED;

    Class<? extends TypeHandler<?>> typeHandler() default UnknownTypeHandler.class;

    Case[] cases();
}
