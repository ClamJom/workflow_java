package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.NodeStates;
import com.example.demoworkflow.utils.workflow.states.WorkflowStates;
import com.example.demoworkflow.vo.ConfigVO;

import java.util.ArrayList;
import java.util.List;

public class HelloNode extends NodeImpl{

    HelloNode(GlobalPool globalPool) {
        super(globalPool);
        this.setNodeType(NodeType.HELLO);
    }

    HelloNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        this.setNodeType(NodeType.HELLO);
    }

    @Override
    public void run(){
        String msg = (String) configs.computeIfAbsent("message", k -> "Hello, world");
        nodePool.put("message", msg);
        putWorkflowResult(WorkflowResult.builder()
                .state(WorkflowStates.RUNNING)
                .msg(msg)
                .token(token)
                .build());
    }
}
