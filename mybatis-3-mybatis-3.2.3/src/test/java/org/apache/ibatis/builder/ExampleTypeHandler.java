package org.apache.ibatis.builder;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 使用这个 ExampleTypeHandler 类型处理器将会覆盖已经存在的处理 java 的 String 类型属性和 VARCHAR 参数及结构的类型处理。
 *
 * @author
 */
public class ExampleTypeHandler implements TypeHandler<String> {

    public void setParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter);
    }

    public String getResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    public String getResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    public String getResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }

}
