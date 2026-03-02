package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.NodeStates;
import com.example.demoworkflow.utils.workflow.states.WorkflowStates;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 结束节点。
 * 绝对，绝对，绝对不要在单个流程图中同级添加多个结束节点，这可能导致流程图提前结束！
 */
public class EndNode extends NodeImpl{

    EndNode(GlobalPool globalPool) {
        super(globalPool);
        this.setNodeType(NodeType.END);
    }

    EndNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        this.setNodeType(NodeType.END);
    }

    @Override
    public void run() {
        // 读取配置中需要输出的变量
        Object outputObj = configs.get("output");
        if(outputObj == null) return;
        List<String> outputs = (List<String>) outputObj;
        Map<String, Object> outputMap = new HashMap<>();
        outputs.forEach(output->{
            outputMap.put(output, globalPool.get(token, output));
        });
        WorkflowResult outputResult = WorkflowResult.builder()
                .token(token)
                .msg("output")
                .state(NodeStates.DONE)
                .extData(outputMap)
                .build();
        putWorkflowResult(outputResult);
    }

    @Override
    public void after(){
        globalPool.workflowDone(token);
    }
}
