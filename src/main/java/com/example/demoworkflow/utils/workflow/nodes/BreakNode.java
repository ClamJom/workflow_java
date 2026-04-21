package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.NodeStates;
import io.micrometer.common.util.StringUtils;

/**
 * 循环中断节点，必须指定父节点，且父节点必须是循环节点。
 * Break本身什么都不做，他只提供一个信号向NodeHandler通知本次运行应当提前结束。
 */
public class BreakNode extends NodeImpl{
    public BreakNode(GlobalPool globalPool) {
        super(globalPool);
        setNodeType(NodeType.BREAK);
    }

    public BreakNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        setNodeType(NodeType.BREAK);
    }

    @Override
    public void before(){
        if (!StringUtils.isEmpty(parentNodeId)) return;
        putWorkflowResult(WorkflowResult.builder()
                .state(NodeStates.ERROR)
                .nodeId(nodeId)
                .msg("Break信号不应当置于嵌套节点外！")
                .build());
        globalPool.workflowError(token);
    }

    @Override
    public void run(){
        globalPool.setBreakSignal(token, parentNodeId);
    }
}
