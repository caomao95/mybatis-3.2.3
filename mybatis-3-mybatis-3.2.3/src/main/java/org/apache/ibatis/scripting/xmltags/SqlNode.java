package org.apache.ibatis.scripting.xmltags;

/**
 * SqlNode接口主要用来处理CRUD节点下的各类动态标签比如、
 * 对每个动态标签，mybatis都提供了对应的SqlNode实现
 *
 * @author
 */
public interface SqlNode {


    boolean apply(DynamicContext context);
}
