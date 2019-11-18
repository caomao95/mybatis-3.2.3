package org.apache.ibatis.scripting.xmltags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author
 */
public class XMLScriptBuilder extends BaseBuilder {

    private XNode context;

    public XMLScriptBuilder(Configuration configuration, XNode context) {
        super(configuration);
        this.context = context;
    }

    public XMLScriptBuilder(Configuration configuration, String context) {
        super(configuration);
        XPathParser parser = new XPathParser(context, false, configuration.getVariables(), new XMLMapperEntityResolver());
        this.context = parser.evalNode("/script");
    }

    /**
     * @return
     */
    public SqlSource parseScriptNode() {
        // 解析动态标签
        List<SqlNode> contents = parseDynamicTags(context);
        MixedSqlNode rootSqlNode = new MixedSqlNode(contents);
        SqlSource sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
        return sqlSource;
    }

    /**
     * 解析动态标签，Mybatis 根据 动态标签的解析来解决传统 JDBC 的SQL拼接。
     *
     * @param node
     * @return
     */
    private List<SqlNode> parseDynamicTags(XNode node) {
        List<SqlNode> contents = new ArrayList<SqlNode>();
        NodeList children = node.getNode().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            XNode child = node.newXNode(children.item(i));
            String nodeName = child.getNode().getNodeName();
            // Node.CDATA_SECTION_NODE :代表文档中的 CDATA 部（不会由解析器解析的文本）
            // Node.TEXT_NODE :代表元素或属性中的文本内容
            if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE
                    || child.getNode().getNodeType() == Node.TEXT_NODE) {
                String data = child.getStringBody("");
                contents.add(new TextSqlNode(data));
            }
            // Node.ELEMENT_NODE :代表元素
            else if (child.getNode().getNodeType() == Node.ELEMENT_NODE && !"selectKey".equals(nodeName)) {
                // nodeHandlers 存放所有的动态标签
                NodeHandler handler = nodeHandlers.get(nodeName);
                // 不认识的标签
                if (handler == null) {
                    throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
                }
                // 对应标签的解析，比如 ForEach 标签的解析，
                handler.handleNode(child, contents);
            }
        }
        return contents;
    }

    /**
     * 在 高版本中使用了 init 方法初始化
     */
    private Map<String, NodeHandler> nodeHandlers = new HashMap<String, NodeHandler>() {
        private static final long serialVersionUID = 7123056019193266281L;

        {
            put("trim", new TrimHandler());
            put("where", new WhereHandler());
            put("set", new SetHandler());
            put("foreach", new ForEachHandler());
            put("if", new IfHandler());
            put("choose", new ChooseHandler());
            put("when", new IfHandler());
            put("otherwise", new OtherwiseHandler());
            put("bind", new BindHandler());
        }
    };

    private interface NodeHandler {
        void handleNode(XNode nodeToHandle, List<SqlNode> targetContents);
    }

    /**
     * bind 元素可以使用 OGNL 表达式创建一个变量并将其绑定到当前SQL节点的上下文，bind还可以用来预防 SQL 注入。
     */
    private class BindHandler implements NodeHandler {
        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            final String name = nodeToHandle.getStringAttribute("name");
            final String expression = nodeToHandle.getStringAttribute("value");
            final VarDeclSqlNode node = new VarDeclSqlNode(name, expression);
            targetContents.add(node);
        }
    }

    private class TrimHandler implements NodeHandler {
        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            List<SqlNode> contents = parseDynamicTags(nodeToHandle);
            MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
            String prefix = nodeToHandle.getStringAttribute("prefix");
            String prefixOverrides = nodeToHandle.getStringAttribute("prefixOverrides");
            String suffix = nodeToHandle.getStringAttribute("suffix");
            String suffixOverrides = nodeToHandle.getStringAttribute("suffixOverrides");
            TrimSqlNode trim = new TrimSqlNode(configuration, mixedSqlNode, prefix, prefixOverrides, suffix, suffixOverrides);
            targetContents.add(trim);
        }
    }

    private class WhereHandler implements NodeHandler {
        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            List<SqlNode> contents = parseDynamicTags(nodeToHandle);
            MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
            WhereSqlNode where = new WhereSqlNode(configuration, mixedSqlNode);
            targetContents.add(where);
        }
    }

    private class SetHandler implements NodeHandler {
        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            List<SqlNode> contents = parseDynamicTags(nodeToHandle);
            MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
            SetSqlNode set = new SetSqlNode(configuration, mixedSqlNode);
            targetContents.add(set);
        }
    }

    private class ForEachHandler implements NodeHandler {
        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            List<SqlNode> contents = parseDynamicTags(nodeToHandle);
            MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
            String collection = nodeToHandle.getStringAttribute("collection");
            String item = nodeToHandle.getStringAttribute("item");
            String index = nodeToHandle.getStringAttribute("index");
            String open = nodeToHandle.getStringAttribute("open");
            String close = nodeToHandle.getStringAttribute("close");
            String separator = nodeToHandle.getStringAttribute("separator");
            // 将其解析为 ForEachSqlNode ，便于在 运行时解析
            ForEachSqlNode forEachSqlNode = new ForEachSqlNode(configuration, mixedSqlNode, collection, index, item, open, close, separator);
            targetContents.add(forEachSqlNode);
        }
    }

    private class IfHandler implements NodeHandler {
        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            List<SqlNode> contents = parseDynamicTags(nodeToHandle);
            MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
            String test = nodeToHandle.getStringAttribute("test");
            // 获取if属性的值，将值设置为IfSqlNode的属性，便于运行时解析
            IfSqlNode ifSqlNode = new IfSqlNode(mixedSqlNode, test);
            targetContents.add(ifSqlNode);
        }
    }

    private class OtherwiseHandler implements NodeHandler {
        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            List<SqlNode> contents = parseDynamicTags(nodeToHandle);
            MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
            targetContents.add(mixedSqlNode);
        }
    }

    private class ChooseHandler implements NodeHandler {
        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            List<SqlNode> whenSqlNodes = new ArrayList<SqlNode>();
            List<SqlNode> otherwiseSqlNodes = new ArrayList<SqlNode>();
            handleWhenOtherwiseNodes(nodeToHandle, whenSqlNodes, otherwiseSqlNodes);
            SqlNode defaultSqlNode = getDefaultSqlNode(otherwiseSqlNodes);
            ChooseSqlNode chooseSqlNode = new ChooseSqlNode(whenSqlNodes, defaultSqlNode);
            targetContents.add(chooseSqlNode);
        }

        private void handleWhenOtherwiseNodes(XNode chooseSqlNode, List<SqlNode> ifSqlNodes, List<SqlNode> defaultSqlNodes) {
            List<XNode> children = chooseSqlNode.getChildren();
            for (XNode child : children) {
                String nodeName = child.getNode().getNodeName();
                NodeHandler handler = nodeHandlers.get(nodeName);
                if (handler instanceof IfHandler) {
                    handler.handleNode(child, ifSqlNodes);
                } else if (handler instanceof OtherwiseHandler) {
                    handler.handleNode(child, defaultSqlNodes);
                }
            }
        }

        private SqlNode getDefaultSqlNode(List<SqlNode> defaultSqlNodes) {
            SqlNode defaultSqlNode = null;
            if (defaultSqlNodes.size() == 1) {
                defaultSqlNode = defaultSqlNodes.get(0);
            } else if (defaultSqlNodes.size() > 1) {
                throw new BuilderException("Too many default (otherwise) elements in choose statement.");
            }
            return defaultSqlNode;
        }
    }

}
