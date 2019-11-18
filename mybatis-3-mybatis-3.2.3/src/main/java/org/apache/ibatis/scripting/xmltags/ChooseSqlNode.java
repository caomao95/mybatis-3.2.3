package org.apache.ibatis.scripting.xmltags;

import java.util.List;

public class ChooseSqlNode implements SqlNode {
    private SqlNode defaultSqlNode;
    private List<SqlNode> ifSqlNodes;

    public ChooseSqlNode(List<SqlNode> ifSqlNodes, SqlNode defaultSqlNode) {
        this.ifSqlNodes = ifSqlNodes;
        this.defaultSqlNode = defaultSqlNode;
    }

    public boolean apply(DynamicContext context) {
        // 遍历所有when分支节点，只要遇到第一个为true就返回
        for (SqlNode sqlNode : ifSqlNodes) {
            if (sqlNode.apply(context)) {
                return true;
            }
        }
        // 全部when都为false时，走otherwise分支
        if (defaultSqlNode != null) {
            defaultSqlNode.apply(context);
            return true;
        }
        return false;
    }
}
