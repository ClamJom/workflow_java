package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.ConditionConfig;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件节点
 */
public class ConditionNode extends NodeImpl{
    public ConditionNode(GlobalPool globalPool) {
        super(globalPool);
        setNodeType(NodeType.CONDITION);
    }

    public ConditionNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        setNodeType(NodeType.CONDITION);
    }

    @Override
    public void run(){
        List<String> conditions = (List<String>) configs.computeIfAbsent("<|CONDITIONS|>", k -> new ArrayList<>());
        conditions.forEach(condition->{
            ConditionConfig cc = (ConditionConfig) configs.get(condition);
            Object a = globalPool.parseObject(cc.a, token);
            Object b = globalPool.parseObject(cc.b, token);
            if(!cc.compareCore(a, b)){
                if(cc.nextNodes == null) return;
                /*
                绝对不要在其它可能正常执行的分支前方连接条件节点指向的下一个节点，因为当条件节点认为下一个节点不应当执行时会将节点置为失能，
                这将导致节点不再执行其功能。由于执行是并发的，也有可能节点被置于失能态之前就已经被执行。如果需要相同的功能，需要另外生成新的
                节点。
                */
                nextNodes.forEach(node->{
                    if(cc.nextNodes.contains(node.nodeId)){
                        globalPool.nodeDisabled(token, node.nodeId);
                    }
                });
            }
        });
    }
}
