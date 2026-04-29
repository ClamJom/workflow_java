package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.types.ConfigTypes;
import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.OutputVariableDes;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.WorkflowStates;
import com.example.demoworkflow.vo.ConfigVO;

import java.util.List;

public class HelloNode extends NodeImpl{

    public HelloNode(GlobalPool globalPool) {
        super(globalPool);
        this.setNodeType(NodeType.HELLO);
    }

    public HelloNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        this.setNodeType(NodeType.HELLO);
    }

    @Override
    public List<ConfigVO> getNodeConfigs(){
        return List.of(ConfigVO.builder()
                        .des("消息")
                        .name("message")
                        .value("Hello, world")
                        .type(ConfigTypes.STRING)
                        .required(false)
                .build());
    }

    @Override
    public List<OutputVariableDes> getNodeOutputs(){
        return List.of(OutputVariableDes.builder()
                        .name("message")
                        .type("String")
                        .des("消息")
                .build());
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
