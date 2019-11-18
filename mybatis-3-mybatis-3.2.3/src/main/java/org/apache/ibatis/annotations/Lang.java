package org.apache.ibatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 使用 何种 SQL 解析器
 *
 * @author
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Lang {

    /**
     * SQL 解析器 {@link org.apache.ibatis.scripting.LanguageDriver}
     *
     * @return
     */
    Class<?> value();
}
