package org.apache.ibatis.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * xml 文件对应的实体
 *
 * @author
 */
public class XNode {

    /**
     * 保存mapper文件内容,如下：
     *
     * <mapper namespace="cn.com.lfan.dao.CustomerDivideInfoDao">
     * <sql id="insertAllColumn">
     *
     * </sql>
     * <insert id="insertNewDivideInfo">
     * <foreach item="item" separator="," collection="list">
     * (#{item.customerId},#{item.fzgsDm},#{item.saleAreaId},
     * #{item.saleAreaDivideWay},#{item.customerManager},
     * #{item.customerManagerDivideWay},#{item.flag},
     * #{item.operator},sysdate(), sysdate())
     * </foreach>
     * </insert>
     * <insert id="batchInsert">
     * <foreach item="item" separator="," collection="list">
     * (#{item.customerId,jdbcType=VARCHAR},
     * #{item.fzgsDm,jdbcType=VARCHAR},
     * #{item.saleAreaId,jdbcType=VARCHAR},
     * #{item.saleAreaDivideWay},
     * #{item.customerManager},
     * #{item.operator})
     * </foreach>
     * </insert>
     * <update id="updateFlagByCustomerId">
     * update md_customer_divide_info
     * set flag = '0',update_time = sysdate()
     * where customer_id = #{customerId,jdbcType=VARCHAR}
     * </update>
     * </mapper>
     */
    private Node node;

    /**
     * 解析mapper文件时 name = "mapper"
     */
    private String name;
    private String body;

    /**
     * 属性，解析mapper文件时 attributes 为 namespace = "cn.com......Dao"
     */
    private Properties attributes;
    private Properties variables;

    /**
     * 文档解析器
     */
    private XPathParser xpathParser;

    public XNode(XPathParser xpathParser, Node node, Properties variables) {
        this.xpathParser = xpathParser;
        this.node = node;
        this.name = node.getNodeName();
        this.variables = variables;
        this.attributes = parseAttributes(node);
        this.body = parseBody(node);
    }

    public XNode newXNode(Node node) {
        return new XNode(xpathParser, node, variables);
    }

    public XNode getParent() {
        Node parent = node.getParentNode();
        if (parent == null || !(parent instanceof Element)) {
            return null;
        } else {
            return new XNode(xpathParser, parent, variables);
        }
    }

    public String getPath() {
        StringBuilder builder = new StringBuilder();
        Node current = node;
        while (current != null && current instanceof Element) {
            if (current != node) {
                builder.insert(0, "/");
            }
            builder.insert(0, current.getNodeName());
            current = current.getParentNode();
        }
        return builder.toString();
    }

    public String getValueBasedIdentifier() {
        StringBuilder builder = new StringBuilder();
        XNode current = this;
        while (current != null) {
            if (current != this) {
                builder.insert(0, "_");
            }
            String value = current.getStringAttribute("id",
                    current.getStringAttribute("value",
                            current.getStringAttribute("property", null)));
            if (value != null) {
                value = value.replace('.', '_');
                builder.insert(0, "]");
                builder.insert(0,
                        value);
                builder.insert(0, "[");
            }
            builder.insert(0, current.getName());
            current = current.getParent();
        }
        return builder.toString();
    }

    public String evalString(String expression) {
        return xpathParser.evalString(node, expression);
    }

    public Boolean evalBoolean(String expression) {
        return xpathParser.evalBoolean(node, expression);
    }

    public Double evalDouble(String expression) {
        return xpathParser.evalDouble(node, expression);
    }

    public List<XNode> evalNodes(String expression) {
        return xpathParser.evalNodes(node, expression);
    }

    public XNode evalNode(String expression) {
        return xpathParser.evalNode(node, expression);
    }

    public Node getNode() {
        return node;
    }

    public String getName() {
        return name;
    }

    public String getStringBody() {
        return getStringBody(null);
    }

    public String getStringBody(String def) {
        if (body == null) {
            return def;
        } else {
            return body;
        }
    }

    public Boolean getBooleanBody() {
        return getBooleanBody(null);
    }

    public Boolean getBooleanBody(Boolean def) {
        if (body == null) {
            return def;
        } else {
            return Boolean.valueOf(body);
        }
    }

    public Integer getIntBody() {
        return getIntBody(null);
    }

    public Integer getIntBody(Integer def) {
        if (body == null) {
            return def;
        } else {
            return Integer.parseInt(body);
        }
    }

    public Long getLongBody() {
        return getLongBody(null);
    }

    public Long getLongBody(Long def) {
        if (body == null) {
            return def;
        } else {
            return Long.parseLong(body);
        }
    }

    public Double getDoubleBody() {
        return getDoubleBody(null);
    }

    public Double getDoubleBody(Double def) {
        if (body == null) {
            return def;
        } else {
            return Double.parseDouble(body);
        }
    }

    public Float getFloatBody() {
        return getFloatBody(null);
    }

    public Float getFloatBody(Float def) {
        if (body == null) {
            return def;
        } else {
            return Float.parseFloat(body);
        }
    }

    public <T extends Enum<T>> T getEnumAttribute(Class<T> enumType, String name) {
        return getEnumAttribute(enumType, name, null);
    }

    public <T extends Enum<T>> T getEnumAttribute(Class<T> enumType, String name, T def) {
        String value = getStringAttribute(name);
        if (value == null) {
            return def;
        } else {
            return Enum.valueOf(enumType, value);
        }
    }

    public String getStringAttribute(String name) {
        return getStringAttribute(name, null);
    }

    public String getStringAttribute(String name, String def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return value;
        }
    }

    public Boolean getBooleanAttribute(String name) {
        return getBooleanAttribute(name, null);
    }

    public Boolean getBooleanAttribute(String name, Boolean def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return Boolean.valueOf(value);
        }
    }

    public Integer getIntAttribute(String name) {
        return getIntAttribute(name, null);
    }

    public Integer getIntAttribute(String name, Integer def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return Integer.parseInt(value);
        }
    }

    public Long getLongAttribute(String name) {
        return getLongAttribute(name, null);
    }

    public Long getLongAttribute(String name, Long def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return Long.parseLong(value);
        }
    }

    public Double getDoubleAttribute(String name) {
        return getDoubleAttribute(name, null);
    }

    public Double getDoubleAttribute(String name, Double def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return Double.parseDouble(value);
        }
    }

    public Float getFloatAttribute(String name) {
        return getFloatAttribute(name, null);
    }

    public Float getFloatAttribute(String name, Float def) {
        String value = attributes.getProperty(name);
        if (value == null) {
            return def;
        } else {
            return Float.parseFloat(value);
        }
    }

    public List<XNode> getChildren() {
        List<XNode> children = new ArrayList<XNode>();
        NodeList nodeList = node.getChildNodes();
        if (nodeList != null) {
            for (int i = 0, n = nodeList.getLength(); i < n; i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    children.add(new XNode(xpathParser, node, variables));
                }
            }
        }
        return children;
    }

    public Properties getChildrenAsProperties() {
        Properties properties = new Properties();
        for (XNode child : getChildren()) {
            String name = child.getStringAttribute("name");
            String value = child.getStringAttribute("value");
            if (name != null && value != null) {
                properties.setProperty(name, value);
            }
        }
        return properties;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<");
        builder.append(name);
        for (Map.Entry<Object, Object> entry : attributes.entrySet()) {
            builder.append(" ");
            builder.append(entry.getKey());
            builder.append("=\"");
            builder.append(entry.getValue());
            builder.append("\"");
        }
        List<XNode> children = getChildren();
        if (children.size() > 0) {
            builder.append(">\n");
            for (XNode node : children) {
                builder.append(node.toString());
            }
            builder.append("</");
            builder.append(name);
            builder.append(">");
        } else if (body != null) {
            builder.append(">");
            builder.append(body);
            builder.append("</");
            builder.append(name);
            builder.append(">");
        } else {
            builder.append("/>");
        }
        builder.append("\n");
        return builder.toString();
    }

    private Properties parseAttributes(Node n) {
        Properties attributes = new Properties();
        NamedNodeMap attributeNodes = n.getAttributes();
        if (attributeNodes != null) {
            for (int i = 0; i < attributeNodes.getLength(); i++) {
                Node attribute = attributeNodes.item(i);
                String value = PropertyParser.parse(attribute.getNodeValue(), variables);
                attributes.put(attribute.getNodeName(), value);
            }
        }
        return attributes;
    }

    private String parseBody(Node node) {
        String data = getBodyData(node);
        if (data == null) {
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                data = getBodyData(child);
                if (data != null) break;
            }
        }
        return data;
    }

    private String getBodyData(Node child) {
        if (child.getNodeType() == Node.CDATA_SECTION_NODE
                || child.getNodeType() == Node.TEXT_NODE) {
            String data = ((CharacterData) child).getData();
            data = PropertyParser.parse(data, variables);
            return data;
        }
        return null;
    }

}