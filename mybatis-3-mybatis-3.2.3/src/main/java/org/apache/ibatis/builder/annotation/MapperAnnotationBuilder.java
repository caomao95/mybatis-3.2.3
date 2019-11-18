package org.apache.ibatis.builder.annotation;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.CacheNamespaceRef;
import org.apache.ibatis.annotations.Case;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.TypeDiscriminator;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * @author
 */
public class MapperAnnotationBuilder {

    private final Set<Class<? extends Annotation>> sqlAnnotationTypes = new HashSet<Class<? extends Annotation>>();
    private final Set<Class<? extends Annotation>> sqlProviderAnnotationTypes = new HashSet<Class<? extends Annotation>>();

    private Configuration configuration;

    /**
     * 和XMLConfigBuilder一样，都是继承与BaseBuilder。
     * Select.class、Insert.class 等注解指示该方法对应的真实sql语句类型分别是select/insert.
     * Mapper 构建的助手
     */
    private MapperBuilderAssistant assistant;
    private Class<?> type;

    public MapperAnnotationBuilder(Configuration configuration, Class<?> type) {
        String resource = type.getName().replace('.', '/') + ".java (best guess)";
        this.assistant = new MapperBuilderAssistant(configuration, resource);
        this.configuration = configuration;
        this.type = type;

        // 指示该方法对应的真实的sql语句类型
        sqlAnnotationTypes.add(Select.class);
        sqlAnnotationTypes.add(Insert.class);
        sqlAnnotationTypes.add(Update.class);
        sqlAnnotationTypes.add(Delete.class);

        // SelectProvider... 主要用于动态SQL，允许指定一个类名和一个方法在具体执行时返回要运行的SQL语句。Mybatis会实例化这个类，然后执行指定的方法。
        sqlProviderAnnotationTypes.add(SelectProvider.class);
        sqlProviderAnnotationTypes.add(InsertProvider.class);
        sqlProviderAnnotationTypes.add(UpdateProvider.class);
        sqlProviderAnnotationTypes.add(DeleteProvider.class);
    }

    /**
     * 首先加载mapper接口对应的xml文件并解析，loadXmlResource和通过resource、url解析相同，都是解析mapper文件中的定义，入口都是XMLMapperBuilder.parse()
     */
    public void parse() {
        String resource = type.toString();
        if (!configuration.isResourceLoaded(resource)) {

            // 加载 mapper xml文件
            loadXmlResource();
            configuration.addLoadedResource(resource);

            // 每个mapper文件自成一个 namespace，通常自动匹配就是这么来的，约定俗称代替人工设置最简化常用的开发。
            assistant.setCurrentNamespace(type.getName());
            parseCache();
            parseCacheRef();
            Method[] methods = type.getMethods();
            for (Method method : methods) {
                try {
                    // 解析非桥接方法
                    parseStatement(method);
                } catch (IncompleteElementException e) {
                    configuration.addIncompleteMethod(new MethodResolver(this, method));
                }
            }
        }

        // 解析挂起的方法
        parsePendingMethods();
    }

    /**
     * 解析挂起的方法
     * {@link MapperBuilderAssistant#useCacheRef(String)} 中 放入挂起的Map中
     */
    private void parsePendingMethods() {
        Collection<MethodResolver> incompleteMethods = configuration.getIncompleteMethods();
        synchronized (incompleteMethods) {
            Iterator<MethodResolver> iter = incompleteMethods.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().resolve();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // This method is still missing a resource
                }
            }
        }
    }

    /**
     * 解析xml映射文件
     */
    private void loadXmlResource() {
        if (!configuration.isResourceLoaded("namespace:" + type.getName())) {
            String xmlResource = type.getName().replace('.', '/') + ".xml";
            InputStream inputStream = null;
            try {
                inputStream = Resources.getResourceAsStream(type.getClassLoader(), xmlResource);
            } catch (IOException e) {
                // ignore, resource is not required
            }
            if (inputStream != null) {
                XMLMapperBuilder xmlParser = new XMLMapperBuilder(inputStream, assistant.getConfiguration(), xmlResource, configuration.getSqlFragments(), type.getName());
                xmlParser.parse();
            }
        }
    }

    /**
     * 解析注解缓存
     */
    private void parseCache() {
        CacheNamespace cacheDomain = type.getAnnotation(CacheNamespace.class);
        if (cacheDomain != null) {
            assistant.useNewCache(cacheDomain.implementation(), cacheDomain.eviction(), cacheDomain.flushInterval(), cacheDomain.size(), cacheDomain.readWrite(), null);
        }
    }

    private void parseCacheRef() {
        CacheNamespaceRef cacheDomainRef = type.getAnnotation(CacheNamespaceRef.class);
        if (cacheDomainRef != null) {
            assistant.useCacheRef(cacheDomainRef.value().getName());
        }
    }

    private String parseResultMap(Method method) {
        // 获取方法的返回类型
        Class<?> returnType = getReturnType(method);

        // 获取构造器
        ConstructorArgs args = method.getAnnotation(ConstructorArgs.class);

        // 获取 Results 注解
        Results results = method.getAnnotation(Results.class);

        // 获取鉴别器
        TypeDiscriminator typeDiscriminator = method.getAnnotation(TypeDiscriminator.class);

        // 产生resultMapId
        String resultMapId = generateResultMapName(method);

        applyResultMap(resultMapId, returnType, argsIf(args), resultsIf(results), typeDiscriminator);
        return resultMapId;
    }

    /**
     * 根据 方法 获取方法的参数类型，从而获取唯一的字符串
     *
     * @param method
     * @return
     */
    private String generateResultMapName(Method method) {
        StringBuilder suffix = new StringBuilder();
        for (Class<?> c : method.getParameterTypes()) {
            suffix.append("-");
            suffix.append(c.getSimpleName());
        }
        if (suffix.length() < 1) {
            suffix.append("-void");
        }
        return type.getName() + "." + method.getName() + suffix;
    }

    private void applyResultMap(String resultMapId, Class<?> returnType, Arg[] args, Result[] results, TypeDiscriminator discriminator) {
        List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
        applyConstructorArgs(args, returnType, resultMappings);
        applyResults(results, returnType, resultMappings);
        Discriminator disc = applyDiscriminator(resultMapId, returnType, discriminator);
        assistant.addResultMap(resultMapId, returnType, null, disc, resultMappings, null);
        createDiscriminatorResultMaps(resultMapId, returnType, discriminator);
    }

    private void createDiscriminatorResultMaps(String resultMapId, Class<?> resultType, TypeDiscriminator discriminator) {
        if (discriminator != null) {
            for (Case c : discriminator.cases()) {
                String caseResultMapId = resultMapId + "-" + c.value();
                List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
                applyConstructorArgs(c.constructArgs(), resultType, resultMappings);
                applyResults(c.results(), resultType, resultMappings);
                assistant.addResultMap(caseResultMapId, c.type(), resultMapId, null, resultMappings, null);
            }
        }
    }

    private Discriminator applyDiscriminator(String resultMapId, Class<?> resultType, TypeDiscriminator discriminator) {
        if (discriminator != null) {
            String column = discriminator.column();
            Class<?> javaType = discriminator.javaType() == void.class ? String.class : discriminator.javaType();
            JdbcType jdbcType = discriminator.jdbcType() == JdbcType.UNDEFINED ? null : discriminator.jdbcType();
            Class<? extends TypeHandler<?>> typeHandler = discriminator.typeHandler() == UnknownTypeHandler.class ? null : discriminator.typeHandler();
            Case[] cases = discriminator.cases();
            Map<String, String> discriminatorMap = new HashMap<String, String>();
            for (Case c : cases) {
                String value = c.value();
                String caseResultMapId = resultMapId + "-" + value;
                discriminatorMap.put(value, caseResultMapId);
            }
            return assistant.buildDiscriminator(resultType, column, javaType, jdbcType, typeHandler, discriminatorMap);
        }
        return null;
    }

    void parseStatement(Method method) {
        // 获取参数类型 3.2.3 版本 当有多个时返回Map.class
        Class<?> parameterTypeClass = getParameterType(method);

        //获取语言驱动，表示使用何种方式进行 Mapper 语句的解析
        LanguageDriver languageDriver = getLanguageDriver(method);

        // 获取方法的SqlSource对象，只有指定了@Select/@Insert/@Update/@Delete或者对应的Provider的方法才会被当作Mapper，否则只是和mapper文件中对应语句的一个运行时占位符。
        SqlSource sqlSource = getSqlSourceFromAnnotations(method, parameterTypeClass, languageDriver);
        if (sqlSource != null) {

            // 获取方法的属性设置，对应<select>中的各种属性
            Options options = method.getAnnotation(Options.class);
            final String mappedStatementId = type.getName() + "." + method.getName();
            Integer fetchSize = null;
            Integer timeout = null;
            StatementType statementType = StatementType.PREPARED;
            ResultSetType resultSetType = ResultSetType.FORWARD_ONLY;
            // 获取语句的CRUD类型
            SqlCommandType sqlCommandType = getSqlCommandType(method);
            boolean isSelect = sqlCommandType == SqlCommandType.SELECT;

            // Select 默认不刷新缓存
            boolean flushCache = !isSelect;
            boolean useCache = isSelect;

            KeyGenerator keyGenerator;
            String keyProperty = "id";
            String keyColumn = null;
            // 当 语句是 Insert 时，判断是否有 SelectKey 注解，用于返回主键，
            // 猜测 在该版本只支持 insert 语句返回主键，但是在 3.5.3 版本，Update 语句也支持该属性
            if (SqlCommandType.INSERT.equals(sqlCommandType)) {
                SelectKey selectKey = method.getAnnotation(SelectKey.class);
                if (selectKey != null) {
                    keyGenerator = handleSelectKeyAnnotation(selectKey, mappedStatementId, getParameterType(method), languageDriver);
                    keyProperty = selectKey.keyProperty();
                } else {
                    if (options == null) {
                        keyGenerator = configuration.isUseGeneratedKeys() ? new Jdbc3KeyGenerator() : new NoKeyGenerator();
                    } else {
                        keyGenerator = options.useGeneratedKeys() ? new Jdbc3KeyGenerator() : new NoKeyGenerator();
                        keyProperty = options.keyProperty();
                        keyColumn = options.keyColumn();
                    }
                }
            } else {
                keyGenerator = new NoKeyGenerator();
            }

            if (options != null) {
                flushCache = options.flushCache();
                useCache = options.useCache();
                fetchSize = options.fetchSize() > -1 || options.fetchSize() == Integer.MIN_VALUE ? options.fetchSize() : null;
                timeout = options.timeout() > -1 ? options.timeout() : null;
                statementType = options.statementType();
                resultSetType = options.resultSetType();
            }

            String resultMapId = null;
            // 解析 ResultMap 注解
            ResultMap resultMapAnnotation = method.getAnnotation(ResultMap.class);
            if (resultMapAnnotation != null) {
                String[] resultMaps = resultMapAnnotation.value();
                StringBuilder sb = new StringBuilder();
                for (String resultMap : resultMaps) {
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    sb.append(resultMap);
                }
                resultMapId = sb.toString();
            } else if (isSelect) {
                //如果是查询，且没有明确设置ResultMap，则根据返回类型自动解析生成ResultMap
                resultMapId = parseResultMap(method);
            }

            assistant.addMappedStatement(
                    mappedStatementId,
                    sqlSource,
                    statementType,
                    sqlCommandType,
                    fetchSize,
                    timeout,
                    null,
                    parameterTypeClass,
                    resultMapId,
                    getReturnType(method),
                    resultSetType,
                    flushCache,
                    useCache,
                    false,
                    keyGenerator,
                    keyProperty,
                    keyColumn,
                    null,
                    languageDriver,
                    null);
        }
    }

    private LanguageDriver getLanguageDriver(Method method) {
        Lang lang = method.getAnnotation(Lang.class);
        if (lang != null) {
            Class<?> languageDriverClass = lang.value();
            return configuration.getLanguageRegistry().getDriver(languageDriverClass);
        }
        return configuration.getLanguageRegistry().getDefaultDriver();
    }

    private Class<?> getParameterType(Method method) {
        Class<?> parameterType = null;
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!RowBounds.class.isAssignableFrom(parameterTypes[i]) && !ResultHandler.class.isAssignableFrom(parameterTypes[i])) {
                if (parameterType == null) {
                    parameterType = parameterTypes[i];
                } else {
                    parameterType = Map.class;
                }
            }
        }
        return parameterType;
    }

    private Class<?> getReturnType(Method method) {
        Class<?> returnType = method.getReturnType();
        if (void.class.equals(returnType)) { // issue #508
            ResultType rt = method.getAnnotation(ResultType.class);
            if (rt != null) {
                returnType = rt.value();
            }
        } else if (Collection.class.isAssignableFrom(returnType)) {
            Type returnTypeParameter = method.getGenericReturnType();
            if (returnTypeParameter instanceof ParameterizedType) {
                Type[] actualTypeArguments = ((ParameterizedType) returnTypeParameter).getActualTypeArguments();
                if (actualTypeArguments != null && actualTypeArguments.length == 1) {
                    returnTypeParameter = actualTypeArguments[0];
                    if (returnTypeParameter instanceof Class) {
                        returnType = (Class<?>) returnTypeParameter;
                    } else if (returnTypeParameter instanceof ParameterizedType) { // (issue #443) actual type can be a also a parametrized type
                        returnType = (Class<?>) ((ParameterizedType) returnTypeParameter).getRawType();
                    } else if (returnTypeParameter instanceof GenericArrayType) {
                        Class<?> componentType = (Class<?>) ((GenericArrayType) returnTypeParameter).getGenericComponentType();
                        returnType = Array.newInstance(componentType, 0).getClass(); // (issue #525) support List<byte[]>
                    }
                }
            }
        } else if (method.isAnnotationPresent(MapKey.class) && Map.class.isAssignableFrom(returnType)) {
            // (issue 504) Do not look into Maps if there is not MapKey annotation
            Type returnTypeParameter = method.getGenericReturnType();
            if (returnTypeParameter instanceof ParameterizedType) {
                Type[] actualTypeArguments = ((ParameterizedType) returnTypeParameter).getActualTypeArguments();
                if (actualTypeArguments != null && actualTypeArguments.length == 2) {
                    returnTypeParameter = actualTypeArguments[1];
                    if (returnTypeParameter instanceof Class) {
                        returnType = (Class<?>) returnTypeParameter;
                    } else if (returnTypeParameter instanceof ParameterizedType) { // (issue 443) actual type can be a also a parametrized type
                        returnType = (Class<?>) ((ParameterizedType) returnTypeParameter).getRawType();
                    }
                }
            }
        }

        return returnType;
    }

    private SqlSource getSqlSourceFromAnnotations(Method method, Class<?> parameterType, LanguageDriver languageDriver) {
        try {
            Class<? extends Annotation> sqlAnnotationType = getSqlAnnotationType(method);
            Class<? extends Annotation> sqlProviderAnnotationType = getSqlProviderAnnotationType(method);
            if (sqlAnnotationType != null) {
                if (sqlProviderAnnotationType != null) {
                    throw new BindingException("You cannot supply both a static SQL and SqlProvider to method named " + method.getName());
                }
                Annotation sqlAnnotation = method.getAnnotation(sqlAnnotationType);
                final String[] strings = (String[]) sqlAnnotation.getClass().getMethod("value").invoke(sqlAnnotation);
                return buildSqlSourceFromStrings(strings, parameterType, languageDriver);
            } else if (sqlProviderAnnotationType != null) {
                Annotation sqlProviderAnnotation = method.getAnnotation(sqlProviderAnnotationType);
                return new ProviderSqlSource(assistant.getConfiguration(), sqlProviderAnnotation);
            }
            return null;
        } catch (Exception e) {
            throw new BuilderException("Could not find value method on SQL annotation.  Cause: " + e, e);
        }
    }

    private SqlSource buildSqlSourceFromStrings(String[] strings, Class<?> parameterTypeClass, LanguageDriver languageDriver) {
        final StringBuilder sql = new StringBuilder();
        for (String fragment : strings) {
            sql.append(fragment);
            sql.append(" ");
        }
        return languageDriver.createSqlSource(configuration, sql.toString(), parameterTypeClass);
    }

    private SqlCommandType getSqlCommandType(Method method) {
        Class<? extends Annotation> type = getSqlAnnotationType(method);

        if (type == null) {
            type = getSqlProviderAnnotationType(method);

            if (type == null) {
                return SqlCommandType.UNKNOWN;
            }

            if (type == SelectProvider.class) {
                type = Select.class;
            } else if (type == InsertProvider.class) {
                type = Insert.class;
            } else if (type == UpdateProvider.class) {
                type = Update.class;
            } else if (type == DeleteProvider.class) {
                type = Delete.class;
            }
        }

        return SqlCommandType.valueOf(type.getSimpleName().toUpperCase(Locale.ENGLISH));
    }

    private Class<? extends Annotation> getSqlAnnotationType(Method method) {
        return chooseAnnotationType(method, sqlAnnotationTypes);
    }

    private Class<? extends Annotation> getSqlProviderAnnotationType(Method method) {
        return chooseAnnotationType(method, sqlProviderAnnotationTypes);
    }

    private Class<? extends Annotation> chooseAnnotationType(Method method, Set<Class<? extends Annotation>> types) {
        for (Class<? extends Annotation> type : types) {
            Annotation annotation = method.getAnnotation(type);
            if (annotation != null) {
                return type;
            }
        }
        return null;
    }

    private void applyResults(Result[] results, Class<?> resultType, List<ResultMapping> resultMappings) {
        for (Result result : results) {
            ArrayList<ResultFlag> flags = new ArrayList<ResultFlag>();
            if (result.id()) {
                flags.add(ResultFlag.ID);
            }
            ResultMapping resultMapping = assistant.buildResultMapping(
                    resultType,
                    nullOrEmpty(result.property()),
                    nullOrEmpty(result.column()),
                    result.javaType() == void.class ? null : result.javaType(),
                    result.jdbcType() == JdbcType.UNDEFINED ? null : result.jdbcType(),
                    hasNestedSelect(result) ? nestedSelectId(result) : null,
                    null,
                    null,
                    null,
                    result.typeHandler() == UnknownTypeHandler.class ? null : result.typeHandler(),
                    flags,
                    null,
                    null);
            resultMappings.add(resultMapping);
        }
    }

    private String nestedSelectId(Result result) {
        String nestedSelect = result.one().select();
        if (nestedSelect.length() < 1) {
            nestedSelect = result.many().select();
        }
        if (!nestedSelect.contains(".")) {
            nestedSelect = type.getName() + "." + nestedSelect;
        }
        return nestedSelect;
    }

    private boolean hasNestedSelect(Result result) {
        return result.one().select().length() > 0 || result.many().select().length() > 0;
    }

    private void applyConstructorArgs(Arg[] args, Class<?> resultType, List<ResultMapping> resultMappings) {
        for (Arg arg : args) {
            ArrayList<ResultFlag> flags = new ArrayList<ResultFlag>();
            flags.add(ResultFlag.CONSTRUCTOR);
            if (arg.id()) {
                flags.add(ResultFlag.ID);
            }
            ResultMapping resultMapping = assistant.buildResultMapping(
                    resultType,
                    null,
                    nullOrEmpty(arg.column()),
                    arg.javaType() == void.class ? null : arg.javaType(),
                    arg.jdbcType() == JdbcType.UNDEFINED ? null : arg.jdbcType(),
                    nullOrEmpty(arg.select()),
                    nullOrEmpty(arg.resultMap()),
                    null,
                    null,
                    arg.typeHandler() == UnknownTypeHandler.class ? null : arg.typeHandler(),
                    flags,
                    null,
                    null);
            resultMappings.add(resultMapping);
        }
    }

    private String nullOrEmpty(String value) {
        return value == null || value.trim().length() == 0 ? null : value;
    }

    private Result[] resultsIf(Results results) {
        return results == null ? new Result[0] : results.value();
    }

    private Arg[] argsIf(ConstructorArgs args) {
        return args == null ? new Arg[0] : args.value();
    }

    private KeyGenerator handleSelectKeyAnnotation(SelectKey selectKeyAnnotation, String baseStatementId, Class<?> parameterTypeClass, LanguageDriver languageDriver) {
        String id = baseStatementId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
        Class<?> resultTypeClass = selectKeyAnnotation.resultType();
        StatementType statementType = selectKeyAnnotation.statementType();
        String keyProperty = selectKeyAnnotation.keyProperty();
        boolean executeBefore = selectKeyAnnotation.before();

        // defaults
        boolean useCache = false;
        KeyGenerator keyGenerator = new NoKeyGenerator();
        Integer fetchSize = null;
        Integer timeout = null;
        boolean flushCache = false;
        String parameterMap = null;
        String resultMap = null;
        ResultSetType resultSetTypeEnum = null;

        SqlSource sqlSource = buildSqlSourceFromStrings(selectKeyAnnotation.statement(), parameterTypeClass, languageDriver);
        SqlCommandType sqlCommandType = SqlCommandType.SELECT;

        assistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass, resultSetTypeEnum,
                flushCache, useCache, false,
                keyGenerator, keyProperty, null, null, languageDriver, null);

        id = assistant.applyCurrentNamespace(id, false);

        MappedStatement keyStatement = configuration.getMappedStatement(id, false);
        SelectKeyGenerator answer = new SelectKeyGenerator(keyStatement, executeBefore);
        configuration.addKeyGenerator(id, answer);
        return answer;
    }

}
