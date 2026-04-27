package com.example.demoworkflow.utils.workflow.nodes;

import com.alibaba.fastjson2.JSON;
import com.example.demoworkflow.pojo.Config;
import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.OutputVariableDes;
import com.example.demoworkflow.utils.workflow.misc.UnaryOperatorHelper;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.vo.ConfigVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 单目运算符，处理数字、变量的自增、自减、位操作
 */
public class UnaryOperatorsNode extends NodeImpl{
    public UnaryOperatorsNode(GlobalPool globalPool) {
        super(globalPool);
        setNodeType(NodeType.UNARY_OPERATORS);
    }

    public UnaryOperatorsNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        setNodeType(NodeType.UNARY_OPERATORS);
    }

    @Override
    public List<ConfigVO> getNodeConfigs(){
        List<ConfigVO> configs = new ArrayList<>();
        configs.add(ConfigVO.builder()
                        .name("var")
                        .des("值或变量；k>1 时表示小数分母与精度（同 parseNumber）")
                        .k(1)
                        .quantize(0)
                        .type("Number")
                .build());
        String[] options = {"++", "--", "~"};
        configs.add(ConfigVO.builder()
                        .name("operator")
                        .des("操作符")
                        .value("++")
                        .type("Select")
                        .options(JSON.toJSONString(options))
                        .required(true)
                .build());
        return configs;
    }

    @Override
    public List<OutputVariableDes> getNodeOutputs(){
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
        Config varConfig = null;
        if (configList != null) {
            for (Config c : configList) {
                if ("var".equals(c.getName())) {
                    varConfig = c;
                    break;
                }
            }
        }
        if (varConfig == null) {
            onNodeError("缺少「var」配置");
            return;
        }
        String rawValue = varConfig.getValue();
        Number result;
        try {
            result = UnaryOperatorHelper.applyAsNumber(configs.get("var"), operator);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            onNodeError(msg != null && !msg.isEmpty() ? msg : "单目运算失败");
            return;
        }
        if ("++".equals(operator) || "--".equals(operator)) {
            UnaryOperatorHelper.singleVariableName(rawValue)
                    .ifPresent(name -> globalPool.put(token, name, result));
        }
        nodePool.put("output", result);
    }
}
