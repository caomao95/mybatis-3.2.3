package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author
 */
public class XMLMapperBuilder extends BaseBuilder {

    private XPathParser parser;
    private MapperBuilderAssistant builderAssistant;

    /**
     * SQL 片段，key：sql标签的id，value：sql标签的节点信息
     */
    private Map<String, XNode> sqlFragments;
    private String resource;

    @Deprecated
    public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
        this(reader, configuration, resource, sqlFragments);
        this.builderAssistant.setCurrentNamespace(namespace);
    }

    @Deprecated
    public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        // configuration.getVariables() 配置文件中配置的properties属性值
        this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
                configuration, resource, sqlFragments);
    }

    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
        this(inputStream, configuration, resource, sqlFragments);
        this.builderAssistant.setCurrentNamespace(namespace);
    }

    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        // configuration.getVariables() 配置文件中配置的properties属性值
        this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
                configuration, resource, sqlFragments);
    }

    private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        super(configuration);
        this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
        this.parser = parser;
        this.sqlFragments = sqlFragments;
        this.resource = resource;
    }

    public void parse() {
        if (!configuration.isResourceLoaded(resource)) {
            // 解析Mapper文件
            configurationElement(parser.evalNode("/mapper"));
            configuration.addLoadedResource(resource);
            //根据 namespace 创建 mapper
            bindMapperForNamespace();
        }

        // 解析挂起的信息
        parsePendingResultMaps();
        parsePendingChacheRefs();
        parsePendingStatements();
    }

    public XNode getSqlFragment(String refid) {
        return sqlFragments.get(refid);
    }

    private void configurationElement(XNode context) {
        try {
            String namespace = context.getStringAttribute("namespace");
            if (namespace.equals("")) {
                throw new BuilderException("Mapper's namespace cannot be empty");
            }
            builderAssistant.setCurrentNamespace(namespace);

            // 解析引用缓存的设置
            cacheRefElement(context.evalNode("cache-ref"));

            // 解析二级缓存设置
            cacheElement(context.evalNode("cache"));

            // 参数map解析
            parameterMapElement(context.evalNodes("/mapper/parameterMap"));

            // 结果集映射解析
            resultMapElements(context.evalNodes("/mapper/resultMap"));

            // 自定义的sql常量 <sql id = ""></sql> 解析 sql元素
            sqlElement(context.evalNodes("/mapper/sql"));

            // 解析CRUD，*
            buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing Mapper XML. Cause: " + e, e);
        }
    }

    private void buildStatementFromContext(List<XNode> list) {
        if (configuration.getDatabaseId() != null) {
            buildStatementFromContext(list, configuration.getDatabaseId());
        }
        buildStatementFromContext(list, null);
    }

    private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
        for (XNode context : list) {
            final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
            try {
                // 解析每一个sql语句
                statementParser.parseStatementNode();
            } catch (IncompleteElementException e) {
                configuration.addIncompleteStatement(statementParser);
            }
        }
    }

    /**
     * 解析挂起的结果映射
     */
    private void parsePendingResultMaps() {
        Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
        synchronized (incompleteResultMaps) {
            Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().resolve();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // ResultMap is still missing a resource...
                }
            }
        }
    }

    private void parsePendingChacheRefs() {
        Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
        synchronized (incompleteCacheRefs) {
            Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().resolveCacheRef();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // Cache ref is still missing a resource...
                }
            }
        }
    }

    private void parsePendingStatements() {
        Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
        synchronized (incompleteStatements) {
            Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().parseStatementNode();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // Statement is still missing a resource...
                }
            }
        }
    }

    private void cacheRefElement(XNode context) {
        if (context != null) {
            // 放入缓存引用中，key:namespace（该mapper文件的namespace），value:namespace(配置的引用 namespace)
            configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
            CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
            try {
                // 通过 namespace 公用其他缓存设置
                cacheRefResolver.resolveCacheRef();
            } catch (IncompleteElementException e) {
                configuration.addIncompleteCacheRef(cacheRefResolver);
            }
        }
    }

    private void cacheElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type", "PERPETUAL");
            //type：指定自定义缓存的全类名(实现Cache接口即可)
            Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
            // 缓存的回收策略 ，默认是LRU（最近最少使用，移除最长时间不被使用的对象）
            // 可选值有：
            // FIFO - 先进先出，按对象进入缓存的顺序来移除它们
            //SOFT - 软引用，移除基于垃圾回收器状态和软引用规则的对象
            //WEAK - 弱引用，更积极地移除基于垃圾收集器和弱引用规则的对象
            String eviction = context.getStringAttribute("eviction", "LRU");
            Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
            // 缓存刷新间隔，缓存多长时间清空一次，默认不清空，设置一个毫秒值
            Long flushInterval = context.getLongAttribute("flushInterval");
            //size：缓存存放多少个元素
            Integer size = context.getIntAttribute("size");
            // readOnly：是否只读,
            // 只读：mybatis认为所有从缓存中获取数据的操作都是只读操作，不会修改数据。mybatis为了加快获取数据，直接就会将数据在缓存中的引用交给用户 。不安全，速度快
            //false：读写(默认)：mybatis觉得获取的数据可能会被修改
            boolean readWrite = !context.getBooleanAttribute("readOnly", false);
            Properties props = context.getChildrenAsProperties();
            builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, props);
        }
    }

    private void parameterMapElement(List<XNode> list) throws Exception {
        for (XNode parameterMapNode : list) {
            String id = parameterMapNode.getStringAttribute("id");
            String type = parameterMapNode.getStringAttribute("type");
            Class<?> parameterClass = resolveClass(type);
            List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
            List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
            for (XNode parameterNode : parameterNodes) {
                String property = parameterNode.getStringAttribute("property");
                String javaType = parameterNode.getStringAttribute("javaType");
                String jdbcType = parameterNode.getStringAttribute("jdbcType");
                String resultMap = parameterNode.getStringAttribute("resultMap");
                String mode = parameterNode.getStringAttribute("mode");
                String typeHandler = parameterNode.getStringAttribute("typeHandler");
                Integer numericScale = parameterNode.getIntAttribute("numericScale");
                ParameterMode modeEnum = resolveParameterMode(mode);
                Class<?> javaTypeClass = resolveClass(javaType);
                JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
                @SuppressWarnings("unchecked")
                Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
                ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
                parameterMappings.add(parameterMapping);
            }
            builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
        }
    }

    private void resultMapElements(List<XNode> list) throws Exception {
        for (XNode resultMapNode : list) {
            try {
                resultMapElement(resultMapNode);
            } catch (IncompleteElementException e) {
                // ignore, it will be retried
            }
        }
    }

    private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
        return resultMapElement(resultMapNode, Collections.<ResultMapping>emptyList());
    }

    private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
        ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
        String id = resultMapNode.getStringAttribute("id",
                resultMapNode.getValueBasedIdentifier());
        String type = resultMapNode.getStringAttribute("type",
                resultMapNode.getStringAttribute("ofType",
                        resultMapNode.getStringAttribute("resultType",
                                resultMapNode.getStringAttribute("javaType"))));
        String extend = resultMapNode.getStringAttribute("extends");
        Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
        Class<?> typeClass = resolveClass(type);
        Discriminator discriminator = null;
        List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
        resultMappings.addAll(additionalResultMappings);
        List<XNode> resultChildren = resultMapNode.getChildren();
        for (XNode resultChild : resultChildren) {
            if ("constructor".equals(resultChild.getName())) {
                processConstructorElement(resultChild, typeClass, resultMappings);
            } else if ("discriminator".equals(resultChild.getName())) {
                discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
            } else {
                ArrayList<ResultFlag> flags = new ArrayList<ResultFlag>();
                if ("id".equals(resultChild.getName())) {
                    flags.add(ResultFlag.ID);
                }
                resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
            }
        }
        ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
        try {
            return resultMapResolver.resolve();
        } catch (IncompleteElementException e) {
            configuration.addIncompleteResultMap(resultMapResolver);
            throw e;
        }
    }

    /**
     * 遍历构造器
     *
     * @param resultChild
     * @param resultType
     * @param resultMappings
     * @throws Exception
     */
    private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
        List<XNode> argChildren = resultChild.getChildren();
        for (XNode argChild : argChildren) {
            ArrayList<ResultFlag> flags = new ArrayList<ResultFlag>();
            flags.add(ResultFlag.CONSTRUCTOR);
            if ("idArg".equals(argChild.getName())) {
                flags.add(ResultFlag.ID);
            }
            resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
        }
    }

    /**
     * 鉴别器的解析
     * <p>
     * README-DISCRIMINATOR.md
     *
     * @param context
     * @param resultType
     * @param resultMappings
     * @return
     * @throws Exception
     */
    private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {

        // 获取定义的列
        String column = context.getStringAttribute("column");

        // 获取定义的 javaType
        String javaType = context.getStringAttribute("javaType");

        // 获取定义的 jdbcTYpe
        String jdbcType = context.getStringAttribute("jdbcType");


        String typeHandler = context.getStringAttribute("typeHandler");
        Class<?> javaTypeClass = resolveClass(javaType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Map<String, String> discriminatorMap = new HashMap<String, String>();
        for (XNode caseChild : context.getChildren()) {
            String value = caseChild.getStringAttribute("value");
            String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
            discriminatorMap.put(value, resultMap);
        }
        return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
    }

    /**
     * mybatis可以根据不同的数据库执行不同的sql，这就是通过sql元素上的databaseId属性来区别的。
     * 同样，首先设置sql元素的id，它必须是在当前mapper文件所定义的命名空间中。sql元素本身的处理很简单，
     * 只是简单的过滤出databaseId和当前加载的配置文件相同的语句以备以后再解析crud遇到时进行引用。
     * 之所以不进行解析，是因为首先能够作为sql元素子元素的所有节点都可以作为crud的子元素，
     * 而且sql元素不会在运行时单独使用，不需要单独解析一次
     *
     * @param list
     * @throws Exception
     */
    private void sqlElement(List<XNode> list) throws Exception {
        if (configuration.getDatabaseId() != null) {
            sqlElement(list, configuration.getDatabaseId());
        }
        sqlElement(list, null);
    }

    private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
        for (XNode context : list) {
            String databaseId = context.getStringAttribute("databaseId");
            String id = context.getStringAttribute("id");
            id = builderAssistant.applyCurrentNamespace(id, false);
            if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
                sqlFragments.put(id, context);
            }
        }
    }

    /**
     * databaseId 是否与当前的 requiredDatabaseId 匹配
     *
     * @param id
     * @param databaseId
     * @param requiredDatabaseId
     * @return
     */
    private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
        if (requiredDatabaseId != null) {
            if (!requiredDatabaseId.equals(databaseId)) {
                return false;
            }
        } else {
            if (databaseId != null) {
                return false;
            }
            // skip this fragment if there is a previous one with a not null databaseId
            if (this.sqlFragments.containsKey(id)) {
                XNode context = this.sqlFragments.get(id);
                if (context.getStringAttribute("databaseId") != null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 主要是获取各个属性的值，
     * <p>
     * processNestedResultMappings 用于解析包含的association或collection复合类型。
     *
     * @param context
     * @param resultType
     * @param flags
     * @return
     * @throws Exception
     */
    private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, ArrayList<ResultFlag> flags) throws Exception {
        String property = context.getStringAttribute("property");
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String nestedSelect = context.getStringAttribute("select");
        // resultMap中可以包含association或collection复合类型,这些复合类型可以使用外部定义的公用resultMap或者内嵌resultMap,
        // 所以这里的处理逻辑是如果有resultMap就获取resultMap,如果没有,那就动态生成一个。如果自动生成的话，他的resultMap id通过调用XNode.getValueBasedIdentifier()来获得
        String nestedResultMap = context.getStringAttribute("resultMap",
                processNestedResultMappings(context, Collections.<ResultMapping>emptyList()));
        String notNullColumn = context.getStringAttribute("notNullColumn");
        String columnPrefix = context.getStringAttribute("columnPrefix");
        String typeHandler = context.getStringAttribute("typeHandler");
        // resultSet resulSet
        String resulSet = context.getStringAttribute("resultSet");
        String foreignColumn = context.getStringAttribute("foreignColumn");
        Class<?> javaTypeClass = resolveClass(javaType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resulSet, foreignColumn);
    }

    /**
     * 解析包含的association或collection复合类型。可以使用外部定义的公用resultMap或者内嵌resultMap,
     *
     * @param context
     * @param resultMappings
     * @return
     * @throws Exception
     */
    private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
        if ("association".equals(context.getName())
                || "collection".equals(context.getName())
                || "case".equals(context.getName())) {
            if (context.getStringAttribute("select") == null) {
                ResultMap resultMap = resultMapElement(context, resultMappings);
                return resultMap.getId();
            }
        }
        return null;
    }

    private void bindMapperForNamespace() {
        String namespace = builderAssistant.getCurrentNamespace();
        if (namespace != null) {
            Class<?> boundType = null;
            try {
                boundType = Resources.classForName(namespace);
            } catch (ClassNotFoundException e) {
                //ignore, bound type is not required
            }
            if (boundType != null) {
                if (!configuration.hasMapper(boundType)) {
                    // Spring may not know the real resource name so we set a flag
                    // to prevent loading again this resource from the mapper interface
                    // look at MapperAnnotationBuilder#loadXmlResource
                    configuration.addLoadedResource("namespace:" + namespace);
                    configuration.addMapper(boundType);
                }
            }
        }
    }

}
