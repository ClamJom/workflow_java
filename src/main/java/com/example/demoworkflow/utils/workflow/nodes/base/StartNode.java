package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.OutputVariableDes;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.vo.ConfigVO;

import java.util.List;
import java.util.Map;

/**
 * 起始节点
 * 绝对不要在单个流程图中添加多个起始节点，这将导致程序无法判断流程图入口，可能从随机入口执行流程图
 */
public class StartNode extends NodeImpl {
    public StartNode(GlobalPool globalPool){
        super(globalPool);
        this.setNodeType(NodeType.START);
    }

    public StartNode(GlobalPool globalPool, String uuid){
        super(globalPool, uuid);
        this.setNodeType(NodeType.START);
    }

    @Override
    public void run(){
        // 起始节点与其它节点不同，其没有任何默认配置项，如果有则将其作为起始节点本身的输出
        configs.keySet().forEach(key->{
            nodePool.put(key, configs.get(key));
        });
    }
}
