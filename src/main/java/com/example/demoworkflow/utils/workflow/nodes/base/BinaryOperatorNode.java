package com.example.demoworkflow.utils.workflow.nodes;

import com.alibaba.fastjson2.JSON;
import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.OutputVariableDes;
import com.example.demoworkflow.utils.workflow.misc.BinaryOperatorHelper;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.vo.ConfigVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 双目运算符，处理两个数值、变量之间的算术与位运算
 */
public class BinaryOperatorNode extends NodeImpl{
    public BinaryOperatorNode(GlobalPool globalPool) {
        super(globalPool);
        setNodeType(NodeType.BINARY_OPERATORS);
    }

    public BinaryOperatorNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        setNodeType(NodeType.BINARY_OPERATORS);
    }

    @Override
    public List<ConfigVO> getNodeConfigs() {
        List<ConfigVO> list = new ArrayList<>();
        list.add(ConfigVO.builder()
                .name("left")
                .des("左操作数或变量")
                .k(1)
                .quantize(0)
                .type("Number")
                .build());
        list.add(ConfigVO.builder()
                .name("right")
                .des("右操作数或变量")
                .k(1)
                .quantize(0)
                .type("Number")
                .build());
        String[] options = {
                "+", "-", "*", "/", "%",
                "&", "|", "^", "<<", ">>", ">>>"
        };
        list.add(ConfigVO.builder()
                .name("operator")
                .des("操作符")
                .value("+")
                .type("Select")
                .options(JSON.toJSONString(options))
                .required(true)
                .build());
        return list;
    }

    @Override
    public List<OutputVariableDes> getNodeOutputs() {
        return List.of(OutputVariableDes.builder()
                .name("output")
                .des("运算结果")
                .type("Number")
                .build());
    }

    @Override
    public void run() {
        String operator = (String) configs.get("operator");
        if (operator == null) {
            onNodeError("未配置操作符");
            return;
        }
        Number result;
        try {
            result = BinaryOperatorHelper.applyAsNumber(
                    configs.get("left"), configs.get("right"), operator);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            onNodeError(msg != null && !msg.isEmpty() ? msg : "双目运算失败");
            return;
        }
        nodePool.put("output", result);
    }
}
