package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * 解析配置文件。
 * XMLConfigBuilder 以及解析 Mapper 文件的 XMLMapperBuilder 都必须继承与BaseBuilder。
 * 本身对于XML的加载和解析的交给了{@link XPathParser}，
 *
 * @author
 */
public class XMLConfigBuilder extends BaseBuilder {

    private boolean parsed;
    private XPathParser parser;

    /**
     * 环境
     */
    private String environment;

    public XMLConfigBuilder(Reader reader) {
        this(reader, null, null);
    }

    public XMLConfigBuilder(Reader reader, String environment) {
        this(reader, environment, null);
    }

    public XMLConfigBuilder(Reader reader, String environment, Properties props) {
        this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    public XMLConfigBuilder(InputStream inputStream) {
        this(inputStream, null, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment) {
        this(inputStream, environment, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
        this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
        super(new Configuration());
        ErrorContext.instance().resource("SQL Mapper Configuration");
        this.configuration.setVariables(props);
        this.parsed = false;
        this.environment = environment;
        this.parser = parser;
    }

    /**
     * 创建 Configuration
     *
     * @return
     */
    public Configuration parse() {
        // 判断是否已经解析过
        if (parsed) {
            throw new BuilderException("Each XMLConfigBuilder can only be used once.");
        }
        parsed = true;
        // mybatis 配置文件解析的主要流程
        // parser.evalNode 返回根节点的 org.apache.ibatis.parsing.XNode
        // <configuration></configuration> 标签
        parseConfiguration(parser.evalNode("/configuration"));
        return configuration;
    }

    /**
     * 解析配置文件
     *
     * @param root
     */
    private void parseConfiguration(XNode root) {
        try {
            propertiesElement(root.evalNode("properties"));
            typeAliasesElement(root.evalNode("typeAliases"));
            pluginElement(root.evalNode("plugins"));
            objectFactoryElement(root.evalNode("objectFactory"));
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
            // 得到settings之后，调用 settingsElement 将各值赋值给configuration，同时在这里重新设置了默认值，所以configuration中的默认值不一定是真正的默认值。
            settingsElement(root.evalNode("settings"));
            environmentsElement(root.evalNode("environments"));
            databaseIdProviderElement(root.evalNode("databaseIdProvider"));
            typeHandlerElement(root.evalNode("typeHandlers"));
            // 加载 mapper 文件
            mapperElement(root.evalNode("mappers"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }

    /**
     * 解析类型别名
     * <p>
     * mybatis主要提供了两种类型的别名设置，具体类的别名以及包的别名设置，类型别名是为java类型设置一个短的名字，用来减少类完全限定名的冗余。
     *
     * @param parent
     */
    private void typeAliasesElement(XNode parent) {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                // 第一种：package 的别名， 在这个package下的所有java bean，在没有注解的情况下，会使用Bean的首字母小写的非限定类名来作为它的别名。
                if ("package".equals(child.getName())) {
                    String typeAliasPackage = child.getStringAttribute("name");
                    // 注册
                    configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
                }
                // 第二种：类的别名
                else {
                    String alias = child.getStringAttribute("alias");
                    String type = child.getStringAttribute("type");
                    try {
                        Class<?> clazz = Resources.classForName(type);
                        if (alias == null) {
                            // 当 别名为空时，判断是否有 Alias 的注解，如果有注解，将注解的值设置到 alias 值中。
                            typeAliasRegistry.registerAlias(clazz);
                        } else {
                            typeAliasRegistry.registerAlias(alias, clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
                    }
                }
            }
        }
    }

    /**
     * 加载 mybatis 插件
     * <p>
     * 用于加载mybatis插件，最常用的有 PageHelper 了，再比如 druid 连接池提供的各种拦截、监控等。
     * <p>
     * 插件在具体实现的时候，采用的是拦截器模式，要注册为mybatis插件，必须要实现{@link Interceptor}接口。
     * interceptor 属性值可以是完整的类名，也可以是别名，只要别名在 typeAlias中存在即可。
     * 如果启动时无法解析，会抛出 ClassNotFoundException， {@link XMLConfigBuilder#typeAliasesElement(XNode)}。
     *
     * @param parent
     * @throws Exception
     */
    private void pluginElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                String interceptor = child.getStringAttribute("interceptor");
                Properties properties = child.getChildrenAsProperties();
                Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
                interceptorInstance.setProperties(properties);
                configuration.addInterceptor(interceptorInstance);
            }
        }
    }

    /**
     * 对象工厂解析器
     * <p>
     * 对象工厂：Mybatis 每次创建结果对象的新实例时，都会使用一个对象工厂（ObjectFactory）实例来完成。
     * 默认的对象工厂是 {@link org.apache.ibatis.reflection.factory.DefaultObjectFactory} ，仅仅是实例化目标类，
     *
     * @param context
     * @throws Exception
     */
    private void objectFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties properties = context.getChildrenAsProperties();
            ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
            factory.setProperties(properties);
            configuration.setObjectFactory(factory);
        }
    }

    /**
     * 对象包装器工厂
     * <p>
     * 主要用来包装返回 result 对象，比如可以用来设置某些字段脱敏或者加密等。
     * 默认对象包装器工厂是 {@link org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory}
     *
     * @param context
     * @throws Exception
     */
    private void objectWrapperFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
            configuration.setObjectWrapperFactory(factory);
        }
    }

    /**
     * 解析properties，这些属性都是可外部配置且可动态替换的，既可以在典型的java属性文件中配置，也可通过 properties
     * 元素的子元素来传递。
     *
     * @param context
     * @throws Exception
     */
    private void propertiesElement(XNode context) throws Exception {
        if (context != null) {
            Properties defaults = context.getChildrenAsProperties();
            String resource = context.getStringAttribute("resource");
            String url = context.getStringAttribute("url");
            // 两个属性不能同时为空
            if (resource != null && url != null) {
                throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
            }
            if (resource != null) {
                defaults.putAll(Resources.getResourceAsProperties(resource));
            } else if (url != null) {
                defaults.putAll(Resources.getUrlAsProperties(url));
            }
            Properties vars = configuration.getVariables();
            if (vars != null) {
                defaults.putAll(vars);
            }
            parser.setVariables(defaults);
            configuration.setVariables(defaults);
        }
    }

    /**
     * 实际的settings赋值，实际的默认值并非是在Configuration 中设置
     *
     * @param context
     * @throws Exception
     */
    private void settingsElement(XNode context) throws Exception {
        if (context != null) {
            // 获取所有的 settings 标签的子节点的属性
            Properties props = context.getChildrenAsProperties();

            // 遍历所有的字节点，判断配置的是否是 Configuration 类的属性。
            // 检查配置类是否知道所有设置
            MetaClass metaConfig = MetaClass.forClass(Configuration.class);
            for (Object key : props.keySet()) {
                if (!metaConfig.hasSetter(String.valueOf(key))) {
                    throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
                }
            }

            configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
            configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
            configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
            configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
            configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), true));
            configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
            configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
            configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
            configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
            configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
            configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
            configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
            configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
            configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
            configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
            configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
            configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
            configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
            configuration.setLogPrefix(props.getProperty("logPrefix"));
            configuration.setLogImpl(resolveClass(props.getProperty("logImpl")));
            configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
        }
    }

    /**
     * 环境配置
     * <p>
     * 环境可以说是 mybatis-config 配置文件中最重要的部分，它类似于 spring 和 maven 中的 profile，允许给开发、生产环境同时配置不同的 environment（环境）。
     * 根据不同的环境加载不同的配置。如果在 SqlSessionFactoryBuilder 调用期间没有传递使用哪个环境的话，默认会使用一个名为 "default" 的环境。
     * 找到环境之后就可以加载事物管理器和数据源了。事物管理器和数据源类型这里都用到了类型别名.
     * 在 Configuration 构造器执行期间注册到 TypeAliasRegister.
     * <p>
     * Mybatis内置提供了JDBC和MANAGED两种事物管理方式，前者主要用于简单 JDBC 模式
     *
     * @param context
     * @throws Exception
     */
    private void environmentsElement(XNode context) throws Exception {
        if (context != null) {
            if (environment == null) {
                environment = context.getStringAttribute("default");
            }
            for (XNode child : context.getChildren()) {
                String id = child.getStringAttribute("id");

                // 查找匹配的 environment
                if (isSpecifiedEnvironment(id)) {

                    // 事物配置并创建事物工厂
                    TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
                    // 数据源配置加载并实例化数据源，数据源是必备的
                    DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
                    DataSource dataSource = dsFactory.getDataSource();
                    Environment.Builder environmentBuilder = new Environment.Builder(id)
                            .transactionFactory(txFactory)
                            .dataSource(dataSource);
                    configuration.setEnvironment(environmentBuilder.build());
                }
            }
        }
    }

    /**
     * 数据库厂商标识加载
     * <p>
     * Mybatis 可以根据不同的数据库厂商执行不同的语句，这个多厂商的支持是基于映射语句中的 databaseId 属性。
     * Mybatis 会加载不带 databaseId 属性和带有匹配当前数据库 databaseId 属性的所有语句。
     * 如果同时找到带有 databaseId 和不带 databaseId 的相同语句，
     * 后者会被舍弃。
     *
     * @param context
     * @throws Exception
     */
    private void databaseIdProviderElement(XNode context) throws Exception {
        DatabaseIdProvider databaseIdProvider = null;
        if (context != null) {
            String type = context.getStringAttribute("type");

            // 保持向后兼容
            if ("VENDOR".equals(type)) {
                //DB_VENDOR  数据库供应商
                type = "DB_VENDOR";
            }
            Properties properties = context.getChildrenAsProperties();
            databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
            databaseIdProvider.setProperties(properties);
        }
        Environment environment = configuration.getEnvironment();
        if (environment != null && databaseIdProvider != null) {
            String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
            configuration.setDatabaseId(databaseId);
        }
    }

    /**
     * 事物管理器解析，前者主要用于简单的JDBC模式，后者主要用于容器管理事物，一般使用JDBC事物管理方式。
     *
     * @param context
     * @return
     * @throws Exception
     */
    private TransactionFactory transactionManagerElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a TransactionFactory.");
    }

    /**
     * 数据源解析器
     *
     * @param context
     * @return
     * @throws Exception
     */
    private DataSourceFactory dataSourceElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a DataSourceFactory.");
    }

    /**
     * 类型处理器
     * <p>
     * 无论是向 Mybatis 的预处理语句中设置一个参数时，还是从结果集中取一个值时，都会用到类型处理器将获取的值以合适的方式转换为java类型。
     * Mybatis 提供了两种方式注册处理器，package 自动检索方式和显示定义方式，使用自动检索（autodiscovery）功能的时候，只能通过注解方式来指定JDBC的类型。
     * 为了简化使用， Mybatis 在初始化 {@link TypeHandlerRegistry} 期间，自动注册了大部分常用的类型处理器，比如字符串、数据、日期等。
     * 对于非标准的类型，用户可以自定义类型处理器来处理，要实现一个自定义类型处理器，
     * 只需要实现 {@link org.apache.ibatis.type.TypeHandler} 或 继承一个 {@link org.apache.ibatis.type.BaseTypeHandler}
     * 并将它映射到一个JDBC类型上。一般继承 BaseTypeHandler
     *
     * <typeHandlers>
     * <typeHandler handler="org.mybatis.internal.example.MobileTypeHandler" />
     * </typeHandlers>
     *
     * @param parent
     * @throws Exception
     */
    private void typeHandlerElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    String typeHandlerPackage = child.getStringAttribute("name");
                    // 注册自定义类型处理器
                    typeHandlerRegistry.register(typeHandlerPackage);
                } else {
                    String javaTypeName = child.getStringAttribute("javaType");
                    String jdbcTypeName = child.getStringAttribute("jdbcType");
                    String handlerTypeName = child.getStringAttribute("handler");
                    Class<?> javaTypeClass = resolveClass(javaTypeName);
                    JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
                    Class<?> typeHandlerClass = resolveClass(handlerTypeName);
                    if (javaTypeClass != null) {
                        if (jdbcType == null) {
                            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
                        } else {
                            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
                        }
                    } else {
                        typeHandlerRegistry.register(typeHandlerClass);
                    }
                }
            }
        }
    }

    /**
     * 加载mapper文件
     * <p>
     * mybatis 提供了两类配置 mapper 的方法，
     * 第一类是使用 package 自动搜索的模式，这样指定package下所有接口都会注册为mapper。例如:
     * <mappers>
     * <package name="org.mybatis.builder"/>
     * </mappers>
     * <p>
     * 第二类是明确指定 mapper ，这又可以通过 resource、url、或者class进行细分。例如:
     * <mappers>
     * <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
     * <mapper resource="org/mybatis/builder/BlogMapper.xml"/>
     * <mapper resource="org/mybatis/builder/PostMapper.xml"/>
     * </mappers>
     *
     * <mappers>
     * <mapper url="file:///var/mappers/AuthorMapper.xml"/>
     * <mapper url="file:///var/mappers/BlogMapper.xml"/>
     * <mapper url="file:///var/mappers/PostMapper.xml"/>
     * </mappers>
     *
     * <mappers>
     * <mapper class="org.mybatis.builder.AuthorMapper"/>
     * <mapper class="org.mybatis.builder.BlogMapper"/>
     * <mapper class="org.mybatis.builder.PostMapper"/>
     * </mappers>
     * <p>
     * 注意：如果要同时使用package自动扫描和通过mapper明确指定要加载的mapper，则必须先声明mapper，然后声明package，否则DTD校验失败。
     * 同时一定要确保package 自动扫描的返回不包含明确指定的 mapper，否则在通过package扫描interface时，
     * 会判断出错,{@link org.apache.ibatis.binding.MapperRegistry#hasMapper(Class)}.
     *
     * @param parent
     * @throws Exception
     */
    private void mapperElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {

                // 在 addMappers方法内部，不允许重复，负责报错
                if ("package".equals(child.getName())) {
                    String mapperPackage = child.getStringAttribute("name");
                    configuration.addMappers(mapperPackage);
                } else {

                    // 有三种配置方式 resource/url/class
                    String resource = child.getStringAttribute("resource");
                    String url = child.getStringAttribute("url");
                    String mapperClass = child.getStringAttribute("class");
                    if (resource != null && url == null && mapperClass == null) {
                        ErrorContext.instance().resource(resource);
                        InputStream inputStream = Resources.getResourceAsStream(resource);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
                        mapperParser.parse();
                    } else if (resource == null && url != null && mapperClass == null) {
                        ErrorContext.instance().resource(url);
                        InputStream inputStream = Resources.getUrlAsStream(url);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
                        mapperParser.parse();
                    } else if (resource == null && url == null && mapperClass != null) {
                        Class<?> mapperInterface = Resources.classForName(mapperClass);
                        configuration.addMapper(mapperInterface);
                    } else {
                        throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
                    }
                }
            }
        }
    }

    private boolean isSpecifiedEnvironment(String id) {
        if (environment == null) {
            throw new BuilderException("No environment specified.");
        } else if (id == null) {
            throw new BuilderException("Environment requires an id attribute.");
        } else if (environment.equals(id)) {
            return true;
        }
        return false;
    }

}
