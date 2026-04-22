package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.ConditionConfig;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.states.NodeStates;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件节点
 */
@Slf4j
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
    public void run() {
        List<String> conditions = (List<String>) configs.computeIfAbsent("<|CONDITIONS|>", k -> new ArrayList<>());
        conditions.forEach(condition->{
            ConditionConfig cc = (ConditionConfig) configs.get(condition);
            Object a = globalPool.parseObject(cc.a, token);
            Object b = globalPool.parseObject(cc.b, token);
            if(!cc.compareCore(a, b)){
                if (cc.nextNodes == null) return;
                // 将该条条件指向的节点置于失能态，后续完全依赖该节点的分支上所有的节点也将处于失能态
                // QA：为什么不跳过条件节点的非直接下游（不止与当前条件节点连接的节点）？这是因为每一个分支都是一个异步的线程。如果这么做会
                // 导致线程不安全
                nextNodes.forEach(node->{
                    // 跳过结束节点，该节点不应该被置于失能态
                    if (node.getNodeType() == NodeType.END) return;
                    if(cc.nextNodes.contains(node.nodeId)){
                        globalPool.nodeDisabled(token, node.nodeId);
                    }
                });
            }
        });
    }
}
