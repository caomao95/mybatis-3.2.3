package org.apache.ibatis.mapping;

/**
 * 使用 statementType 来标记使用什么对象操作SQL语句
 *
 * <update id="update4" statementType="STATEMENT">
 * update tb_car set price=${price} where id=${id}
 * </update>
 * <update id="update5" statementType="PREPARED">
 * update tb_car set xh=#{xh} where id=#{id}
 * </update>
 *
 * @author
 */
public enum StatementType {

    /**
     * 直接操作SQL，不进行预编译， ${}
     */
    STATEMENT,
    /**
     * 预处理, #{} 默认
     */
    PREPARED,

    /**
     * 执行存储过程
     */
    CALLABLE
}
