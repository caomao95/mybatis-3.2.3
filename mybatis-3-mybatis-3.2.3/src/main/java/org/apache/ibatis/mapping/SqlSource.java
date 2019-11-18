package org.apache.ibatis.mapping;

/**
 * This bean represets the content of a mapped statement read from an XML file
 * or an annotation. It creates the SQL that will be passed to the database out
 * of the input parameter received from the user.
 *
 * @author
 */
public interface SqlSource {

    BoundSql getBoundSql(Object parameterObject);
}
