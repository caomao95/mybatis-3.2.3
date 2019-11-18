package org.apache.ibatis.mapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * SqlSource中包含的SQL处理动态内容之后的实际SQL语句，SQL中会包含?占位符，也就是最终给JDBC的SQL语句，以及他们的参数信息
 *
 * @author
 */
public class BoundSql {

    /**
     * sql文本
     */
    private String sql;

    /**
     * 静态参数说明
     */
    private List<ParameterMapping> parameterMappings;

    /**
     * 运行时参数对象
     */
    private Object parameterObject;

    /**
     * 额外参数，也就是for loops、bind生成的
     */
    private Map<String, Object> additionalParameters;

    /**
     * 额外参数的facade模式包装
     */
    private MetaObject metaParameters;

    public BoundSql(Configuration configuration, String sql, List<ParameterMapping> parameterMappings, Object parameterObject) {
        this.sql = sql;
        this.parameterMappings = parameterMappings;
        this.parameterObject = parameterObject;
        this.additionalParameters = new HashMap<String, Object>();
        this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public String getSql() {
        return sql;
    }

    public List<ParameterMapping> getParameterMappings() {
        return parameterMappings;
    }

    public Object getParameterObject() {
        return parameterObject;
    }

    public boolean hasAdditionalParameter(String name) {
        return metaParameters.hasGetter(name);
    }

    public void setAdditionalParameter(String name, Object value) {
        metaParameters.setValue(name, value);
    }

    public Object getAdditionalParameter(String name) {
        return metaParameters.getValue(name);
    }
}
