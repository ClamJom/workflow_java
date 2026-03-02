package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.token.Token;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.pool.NodePool;

/**
 * 起始节点
 * 绝对不要在单个流程图中添加多个起始节点，这将导致程序无法判断流程图入口，可能从随机入口执行流程图
 */
public class StartNode extends NodeImpl {
    StartNode(GlobalPool globalPool){
        super(globalPool);
        this.setNodeType(NodeType.START);
    }

    StartNode(GlobalPool globalPool, String uuid){
        super(globalPool, uuid);
        this.setNodeType(NodeType.START);
    }

    @Override
    public void run(){
        configs.keySet().forEach(key->{
            nodePool.put(key, configs.get(key));
        });
    }
}
