package org.apache.ibatis.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;

/**
 * 入口：
 * sqlSessionFactoryBuilder 用于创建SqlSessionFactory.
 * 承担了mybatis 配置文件的加载，解析，内部构建等职责。
 * <p>
 * SqlSessionFactoryBuilder -> XMLConfigBuilder -> SqlSessionFactoryBuilder -> XPathParser -> Configuration
 * <p>
 * SqlSessionFactoryBuilder 提供了根据字节流、字符流以及直接使用 org.apache.ibatis.session.Configuration 配置类 三种途径的读取配置信息方法。
 * <p>
 *
 * @author
 */
public class SqlSessionFactoryBuilder {

    /**
     * 使用字符流构建
     *
     * @param reader
     * @return
     */
    public SqlSessionFactory build(Reader reader) {
        return build(reader, null, null);
    }

    public SqlSessionFactory build(Reader reader, String environment) {
        return build(reader, environment, null);
    }

    public SqlSessionFactory build(Reader reader, Properties properties) {
        return build(reader, null, properties);
    }

    public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
        try {
            XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
            return build(parser.parse());
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error building SqlSession.", e);
        } finally {
            ErrorContext.instance().reset();
            try {
                reader.close();
            } catch (IOException e) {
                // Intentionally ignore. Prefer previous error.
            }
        }
    }

    /**
     * 使用字节流构建
     *
     * @param inputStream
     * @return
     */
    public SqlSessionFactory build(InputStream inputStream) {
        return build(inputStream, null, null);
    }

    public SqlSessionFactory build(InputStream inputStream, String environment) {
        return build(inputStream, environment, null);
    }

    public SqlSessionFactory build(InputStream inputStream, Properties properties) {
        return build(inputStream, null, properties);
    }

    public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
        try {
            XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);

            // 真正 构建 Configuration 逻辑就在 XMLConfigBuilder.parse() 中
            return build(parser.parse());
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error building SqlSession.", e);
        } finally {
            ErrorContext.instance().reset();
            try {
                inputStream.close();
            } catch (IOException e) {
                // Intentionally ignore. Prefer previous error.
            }
        }
    }

    /**
     * 使用配置类构建
     *
     * @param config
     * @return
     */
    public SqlSessionFactory build(Configuration config) {
        return new DefaultSqlSessionFactory(config);
    }

}
