package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.types.ConfigTypes;
import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.ConditionConfig;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.vo.ConfigVO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class WhileLoopNode extends LoopNode{
    private final List<Object> outputs = new ArrayList<>();
    public WhileLoopNode(GlobalPool globalPool) {
        super(globalPool);
        setNodeType(NodeType.WHILE);
    }

    public WhileLoopNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        setNodeType(NodeType.WHILE);
    }

    @Override
    public List<ConfigVO> getNodeConfigs(){
        return List.of(ConfigVO.builder()
                .des("循环条件")
                .name("condition")
                .value("1")
                .max(Integer.MAX_VALUE)
                .min(1)
                .k(1)
                .type(ConfigTypes.CONDITION)
                .required(true)
                .build(),
        ConfigVO.builder()
                .des("单次循环超时限制（毫秒），0表示永不超时")
                .name("timeout")
                .value("60000")
                .max(Integer.MAX_VALUE)
                .min(0)
                .k(1)
                .type(ConfigTypes.NUMBER)
                .required(true)
                .build());
    }

    /**
     * 由于ConditionConfig的性质，While节点可以添加多个条件。但条件之间只能通过关系“与”连接
     * @return 所有条件是否满足
     */
    private boolean checkConditions(){
        List<String> conditions = (List<String>) configs.computeIfAbsent("<|CONDITIONS|>", k -> new ArrayList<>());
        return conditions.stream().allMatch(condition -> {
           ConditionConfig cc = (ConditionConfig) configs.get(condition);
           Object a = globalPool.parseObject(cc.a, token);
           Object b = globalPool.parseObject(cc.b, token);
           return cc.compareCore(a, b);
        });
    }

    @Override
    public void run(){
        assert subStartNode != null;
        assert subEndNode != null;
        Integer timeout = (Integer) configs.get("timeout");
        if (timeout == null) timeout = 0;
        int i = 0;
        while(checkConditions()){
            if(runCore(timeout, i)) break;
            i++;
        }
        nodePool.put("output", outputs);
    }
}
