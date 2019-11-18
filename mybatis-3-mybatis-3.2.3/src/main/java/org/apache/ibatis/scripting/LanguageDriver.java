package org.apache.ibatis.scripting;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;

/**
 * SQL解析器
 * 从3.2版本开始，mybatis提供了LanguageDriver接口，我们可以使用该接口自定义SQL的解析方式。先来看下LanguageDriver接口中的3个方法：
 * <p>
 * 实现 LanguageDriver 之后，可以在配置文件中指定该实现类作为SQL的解析器，在XML中我们可以使用 lang 属性来进行指定
 * <p>
 * 1.
 * <typeAliases>
 * <typeAlias type="cn.com.lfan.MyLanguageDriver" alias="myLanguage"/>
 * </typeAliases>
 *
 * <select id="selectTestTable" lang="myLanguage">
 * SELECT * FROM TEST_TABLE
 * </select>
 * <p>
 * 2.
 * 设置全局的默认SQL解析器
 * <settings>
 * <setting name="defaultScriptingLanguage" value="myLanguage"/>
 * </settings>
 * <p>
 * 3.
 * 针对注解使用  {@link org.apache.ibatis.annotations.Lang}
 *
 * @author
 */
public interface LanguageDriver {

    /**
     * Creates a {@link ParameterHandler} that will set the parameters of the
     * 创建一个ParameterHandler对象，用于将实际参数赋值到JDBC语句中
     *
     * @param mappedStatement The mapped statement that is being executed
     * @param parameterObject The input parameter object (can be null)
     * @param boundSql        The resulting SQL once the dynamic language has been executed.
     * @return
     */
    ParameterHandler createParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql);

    /**
     * Creates an {@link SqlSource} that will hold the statement read from a mapper xml file
     * 将XML中读入的语句解析并返回一个sqlSource对象
     *
     * @param configuration The MyBatis configuration
     * @param script        XNode parsed from a XML file
     * @param parameterType input parameter type got from a mapper method or specified in the parameterType xml attribute. Can be null.
     * @return
     */
    SqlSource createSqlSource(Configuration configuration, XNode script, Class<?> parameterType);

    /**
     * Creates an {@link SqlSource} that will hold the statement read from an annotation
     * 将注解中读入的语句解析并返回一个sqlSource对象
     *
     * @param configuration The MyBatis configuration
     * @param script        The content of the annotation
     * @param parameterType input parameter type got from a mapper method or specified in the parameterType xml attribute. Can be null.
     * @return
     */
    SqlSource createSqlSource(Configuration configuration, String script, Class<?> parameterType);

}
