package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.workflow.pool.GlobalPool;

public class OperatorNode extends NodeImpl{
    public OperatorNode(GlobalPool globalPool) {
        super(globalPool);
    }

    public OperatorNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
    }
}
