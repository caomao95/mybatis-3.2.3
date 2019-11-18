package org.apache.ibatis.scripting.xmltags;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;

/**
 * 默认的SQL解析器
 *
 * @author
 */
public class XMLLanguageDriver implements LanguageDriver {

    @Override
    public ParameterHandler createParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
        // 创建参数处理器，返回默认的实现
        return new DefaultParameterHandler(mappedStatement, parameterObject, boundSql);
    }

    @Override
    public SqlSource createSqlSource(Configuration configuration, XNode script, Class<?> parameterType) {
        XMLScriptBuilder builder = new XMLScriptBuilder(configuration, script);
        // 根据XML定义创建SqlSource
        return builder.parseScriptNode();
    }

    @Override
    public SqlSource createSqlSource(Configuration configuration, String script, Class<?> parameterType) {

        // 解析注解中的SQL语句
        if (script.startsWith("<script>")) {
            XMLScriptBuilder builder = new XMLScriptBuilder(configuration, script);
            return builder.parseScriptNode();
        } else {
            List<SqlNode> contents = new ArrayList<SqlNode>();
            contents.add(new TextSqlNode(script.toString()));
            MixedSqlNode rootSqlNode = new MixedSqlNode(contents);
            return new DynamicSqlSource(configuration, rootSqlNode);
        }
    }

}
