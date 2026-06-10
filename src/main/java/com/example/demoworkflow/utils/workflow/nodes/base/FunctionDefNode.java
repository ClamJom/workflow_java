package com.example.demoworkflow.utils.workflow.nodes.base;

import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.nodes.NodeImpl;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;

/**
 * 函数定义节点（可嵌套容器）。
 * <p>
 * 作为函数定义容器，其内部包含一个完整的子流程图：
 * <ul>
 *   <li>内部的 StartNode 用于定义函数参数（变量配置即为参数，其值为默认值）</li>
 *   <li>内部的 EndNode 以及其他节点构成函数体</li>
 * </ul>
 * 该节点本身不参与主线执行（允许入度与出度都为空），
 * 仅当被 {@link FunctionCallNode} 显式调用时才会运行内部子图。
 */
public class FunctionDefNode extends NodeImpl {

    public FunctionDefNode(GlobalPool globalPool) {
        super(globalPool);
        this.setNodeType(NodeType.FUNCTION_DEF);
    }

    public FunctionDefNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        this.setNodeType(NodeType.FUNCTION_DEF);
    }

    @Override
    public void before() {
        if (subStartNode == null || subEndNode == null) {
            onNodeError("函数定义节点必须包含起始节点和结束节点！");
        }
    }

    /**
     * 函数定义节点本身不执行任何逻辑。
     * 当它被意外连接到主线时，不做任何处理（仅作为空操作节点通过）。
     */
    @Override
    public void run() {
        // 函数定义节点本身不执行逻辑
    }
}
