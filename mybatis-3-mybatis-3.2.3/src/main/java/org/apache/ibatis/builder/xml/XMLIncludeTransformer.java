package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * <insert id="batchInsertYwdlxx" parameterType="java.util.List">
 * INSERT INTO KHGL_KHYWDLXX
 * <trim prefix="(" suffix=")" suffixOverrides=",">
 * <include refid="allColumn"/>
 * </trim>
 * <foreach collection="list" item="item" index="index" separator="union all">
 * select
 * #{item.khId} khId,
 * #{item.khlx} khlx,
 * #{item.ywdlDm} ywdlDm,
 * #{item.sswdDm} sswdDm,
 * #{item.wdhffs} wdhffs,
 * #{item.sskhjlDm} sskhjlDm,
 * #{item.khjlhffs} khjlhffs,
 * #{item.fzgsDm} fzgsDm,
 * sysdate zhxgsj,
 * sysdate syncTime
 * from dual
 * </foreach>
 * </insert>
 * mapper xml文件中的 include 标签转换器
 *
 * @author
 */
public class XMLIncludeTransformer {

    private final Configuration configuration;
    private final MapperBuilderAssistant builderAssistant;

    public XMLIncludeTransformer(Configuration configuration, MapperBuilderAssistant builderAssistant) {
        this.configuration = configuration;
        this.builderAssistant = builderAssistant;
    }

    /**
     * 将节点分为：文本节点、include、非include三类进行处理。
     * <p>
     * 文本节点：根据入参变量上下文将变量设置替换进去；
     * 对于其他节点：首先判断是否为根节点，如果是非根且变量上下文不为空，则先解析属性值上的占位符。然后对于子节点，递归进行调用直到所有节点都为文本节点为止。
     * <p>
     * 解析包含 <include />标签的处理，由于传入的是 一个完整的SQL 语句配置，所以第一次进入 else if ，然后在这里开始遍历所有的子节点。
     * 对于include节点：根据属性refid调用findSqlFragment找到sql片段，对节点中包含的占位符进行替换解析，然后调用自身进行递归解析，
     * 解析到文本节点返回之后。判断下include的sql片段是否和包含它的节点是同一个文档，如果不是，则把它从原来的文档包含进来。
     * 然后使用 include 指向的 sql 节点替换 include 节点，最后剥掉sql节点本身，也就是把sql下的节点上移一层，这样就合法了。
     * <p>
     * <p>
     * 通过该方法后CRUD就没有嵌套的sql片段了，这样就可以进行直接解析了。
     *
     * @param source
     */
    public void applyIncludes(Node source) {
        if (source.getNodeName().equals("include")) {
            Node toInclude = findSqlFragment(getStringAttribute(source, "refid"));
            applyIncludes(toInclude);
            if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
                toInclude = source.getOwnerDocument().importNode(toInclude, true);
            }
            source.getParentNode().replaceChild(toInclude, source);
            while (toInclude.hasChildNodes()) {
                toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
            }
            toInclude.getParentNode().removeChild(toInclude);
        } else if (source.getNodeType() == Node.ELEMENT_NODE) {
            // 递归解析 include 标签
            NodeList children = source.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                applyIncludes(children.item(i));
            }
        }
    }

    /**
     * 根据 refid 获取对应的SQL片段，
     *
     * @param refid
     * @return
     */
    private Node findSqlFragment(String refid) {

        // 对于节点中包含的占位符进行替换解析
        // 1
        refid = PropertyParser.parse(refid, configuration.getVariables());
        //refid 当前的 namespace ，唯一值
        refid = builderAssistant.applyCurrentNamespace(refid, true);
        try {
            XNode nodeToInclude = configuration.getSqlFragments().get(refid);
            Node result = nodeToInclude.getNode().cloneNode(true);
            return result;
        } catch (IllegalArgumentException e) {
            throw new IncompleteElementException("Could not find SQL statement to include with refid '" + refid + "'", e);
        }
    }

    private String getStringAttribute(Node node, String name) {
        return node.getAttributes().getNamedItem(name).getNodeValue();
    }
}
