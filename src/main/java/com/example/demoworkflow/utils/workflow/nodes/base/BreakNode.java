package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.NodeStates;
import io.micrometer.common.util.StringUtils;

/**
 * 循环中断节点：必须挂在循环/条件循环容器下；图结构上需保留入边与出边（出边仅指向同容器内结束节点），
 * 以便与多线程下沿 next 链推进的执行模型一致。运行时仍只负责向 GlobalPool 写入跳出信号。
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
