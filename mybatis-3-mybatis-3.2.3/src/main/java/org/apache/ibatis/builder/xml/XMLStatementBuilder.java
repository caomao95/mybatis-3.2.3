/*
 *    Copyright 2009-2013 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.util.List;
import java.util.Locale;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

public class XMLStatementBuilder extends BaseBuilder {

    private MapperBuilderAssistant builderAssistant;
    private XNode context;
    private String requiredDatabaseId;

    public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context) {
        this(configuration, builderAssistant, context, null);
    }

    public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context, String databaseId) {
        super(configuration);
        this.builderAssistant = builderAssistant;
        this.context = context;
        this.requiredDatabaseId = databaseId;
    }

    /**
     * 解析每一条 SQL 语句
     */
    public void parseStatementNode() {
        // id 属性
        String id = context.getStringAttribute("id");
        String databaseId = context.getStringAttribute("databaseId");

        if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
            return;
        }

        // 每批 返回多少条记录
        Integer fetchSize = context.getIntAttribute("fetchSize");
        Integer timeout = context.getIntAttribute("timeout");
        String parameterMap = context.getStringAttribute("parameterMap");
        String parameterType = context.getStringAttribute("parameterType");
        Class<?> parameterTypeClass = resolveClass(parameterType);
        String resultMap = context.getStringAttribute("resultMap");
        String resultType = context.getStringAttribute("resultType");

        // MyBatis 从 3.2 开始支持可插拔的脚本语言，因此你可以在插入一种语言的驱动（language driver）之后来写基于这种语言的动态 SQL 查询。
        String lang = context.getStringAttribute("lang");

        // 语言驱动，默认为 XMLLanguageDriver
        LanguageDriver langDriver = getLanguageDriver(lang);

        Class<?> resultTypeClass = resolveClass(resultType);

        // 结果集的类型，FORWARD_ONLY，SCROLL_SENSITIVE 或 SCROLL_INSENSITIVE 中的一个，默认值为 unset （依赖驱动）。
        String resultSetType = context.getStringAttribute("resultSetType");

        // 执行 CRUD 语句的类型，mybatis目前支持三种,prepare（预编译）、硬编码（直接执行）、以及存储过程调用
        StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));

        ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);

        // xml 的类型 select/update/insert/delete
        String nodeName = context.getNode().getNodeName();

        // 解析SQL命令类型
        SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
        boolean isSelect = sqlCommandType == SqlCommandType.SELECT;


        //(1）当为select语句时：
        //flushCache默认为false，表示任何时候语句被调用，都不会去清空本地缓存和二级缓存。
        //useCache默认为true，表示会将本条语句的结果进行二级缓存。
        //（2）当为insert、update、delete语句时：
        //flushCache默认为true，表示任何时候语句被调用，都会导致本地缓存和二级缓存被清空。
        //useCache属性在该情况下没有。
        // insert/delete/update 后是否刷新缓存
        boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
        boolean useCache = context.getBooleanAttribute("useCache", isSelect);

        // 嵌套查询（关联查询）时：
        //这个设置仅针对嵌套结果 select 语句适用：如果为 true，就是假设包含了嵌套结果集或是分组，
        // 这样的话当返回一个主结果行的时候，就不会发生有对前面结果集的引用的情况。
        // 这就使得在获取嵌套的结果集的时候不至于导致内存不够用。默认值：false。
        boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);

        // include 标签的转换器  <include refid="allColumn"/>
        XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
        // 通过该解析后获取到SQL就没有嵌套语句了
        includeParser.applyIncludes(context.getNode());

        //解析 selectKey 节点， selectKey 节点用于不支持自动生成自增主键的数据库比如Oracle，或者可能JDBC驱动不支持自动生成主键时的情况。
        // 对于数据库支持自动生成主键的字段（比如MySQL和SQL Server），那么你可以设置useGeneratedKeys=”true”
        // 同时设置 keyProperty 到你已经做好的目标属性上就可以了，不需要使用selectKey节点
        List<XNode> selectKeyNodes = context.evalNodes("selectKey");
        if (configuration.getDatabaseId() != null) {
            parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, configuration.getDatabaseId());
        }
        parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, null);

        // Parse the SQL (pre: <selectKey> and <include> were parsed and removed)
        // 主要进行解析 动态标签，创建一个SqlSource
        SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);

        String resultSets = context.getStringAttribute("resultSets");
        String keyProperty = context.getStringAttribute("keyProperty");
        String keyColumn = context.getStringAttribute("keyColumn");
        KeyGenerator keyGenerator;
        String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
        keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
        //为selectKey 对应的SQL维护一个 KeyGenerator
        if (configuration.hasKeyGenerator(keyStatementId)) {
            keyGenerator = configuration.getKeyGenerator(keyStatementId);
        } else {
            keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
                    configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
                    ? new Jdbc3KeyGenerator() : new NoKeyGenerator();
        }

        builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
                fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
                resultSetTypeEnum, flushCache, useCache, resultOrdered,
                keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
    }

    /**
     * 解析 SelectKey
     *
     * @param parentId
     * @param list
     * @param parameterTypeClass
     * @param langDriver
     * @param skRequiredDatabaseId
     */
    public void parseSelectKeyNodes(String parentId, List<XNode> list, Class<?> parameterTypeClass, LanguageDriver langDriver, String skRequiredDatabaseId) {
        for (XNode nodeToHandle : list) {
            String id = parentId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
            String databaseId = nodeToHandle.getStringAttribute("databaseId");
            if (databaseIdMatchesCurrent(id, databaseId, skRequiredDatabaseId)) {
                parseSelectKeyNode(id, nodeToHandle, parameterTypeClass, langDriver, databaseId);
            }
        }
    }

    public void parseSelectKeyNode(String id, XNode nodeToHandle, Class<?> parameterTypeClass, LanguageDriver langDriver, String databaseId) {

        // 开始时获取各个属性
        String resultType = nodeToHandle.getStringAttribute("resultType");
        Class<?> resultTypeClass = resolveClass(resultType);
        StatementType statementType = StatementType.valueOf(nodeToHandle.getStringAttribute("statementType", StatementType.PREPARED.toString()));
        String keyProperty = nodeToHandle.getStringAttribute("keyProperty");
        boolean executeBefore = "BEFORE".equals(nodeToHandle.getStringAttribute("order", "AFTER"));

        //defaults
        boolean useCache = false;
        boolean resultOrdered = false;
        KeyGenerator keyGenerator = new NoKeyGenerator();
        Integer fetchSize = null;
        Integer timeout = null;
        boolean flushCache = false;
        String parameterMap = null;
        String resultMap = null;
        ResultSetType resultSetTypeEnum = null;

        // 生成对应的 SqlSource
        SqlSource sqlSource = langDriver.createSqlSource(configuration, nodeToHandle, parameterTypeClass);
        SqlCommandType sqlCommandType = SqlCommandType.SELECT;

        // 使用 SqlSource 创建 MappedStatement 对象
        builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
                fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
                resultSetTypeEnum, flushCache, useCache, resultOrdered,
                keyGenerator, keyProperty, null, databaseId, langDriver, null);

        id = builderAssistant.applyCurrentNamespace(id, false);

        MappedStatement keyStatement = configuration.getMappedStatement(id, false);

        // 添加到 Configuration 中， 并通过 executeBefore 判断是在sql之前执行还是之后执行， oracle 是在insert之前获取主键， mysql是在insert之后获取主键
        configuration.addKeyGenerator(id, new SelectKeyGenerator(keyStatement, executeBefore));
        nodeToHandle.getParent().getNode().removeChild(nodeToHandle.getNode());
    }

    private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
        if (requiredDatabaseId != null) {
            if (!requiredDatabaseId.equals(databaseId)) {
                return false;
            }
        } else {
            if (databaseId != null) {
                return false;
            }
            // skip this statement if there is a previous one with a not null databaseId
            id = builderAssistant.applyCurrentNamespace(id, false);
            if (this.configuration.hasStatement(id, false)) {
                MappedStatement previous = this.configuration.getMappedStatement(id, false);
                if (previous.getDatabaseId() != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private LanguageDriver getLanguageDriver(String lang) {
        Class<?> langClass;
        if (lang == null) {
            langClass = configuration.getLanguageRegistry().getDefaultDriverClass();
        } else {
            langClass = resolveClass(lang);
            configuration.getLanguageRegistry().register(langClass);
        }
        if (langClass == null) {
            langClass = configuration.getLanguageRegistry().getDefaultDriverClass();
        }
        return configuration.getLanguageRegistry().getDriver(langClass);
    }

}
