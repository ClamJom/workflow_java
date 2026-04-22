package com.example.demoworkflow.utils.workflow.nodes;

import com.alibaba.fastjson2.JSON;
import com.example.demoworkflow.pojo.Config;
import com.example.demoworkflow.utils.types.ConfigTypes;
import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.ConditionConfig;
import com.example.demoworkflow.utils.workflow.dto.OutputVariableDes;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.pool.NodePool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.NodeStates;
import com.example.demoworkflow.utils.workflow.states.WorkflowStates;
import com.example.demoworkflow.vo.ConfigVO;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 所有节点都应该继承这个节点类型，这是基础空白节点，定义了一些基础通用节点方法
 */
@Data
public class NodeImpl implements Node {
    private NodeType nodeType = NodeType.EMPTY_NODE;

    public String nodeId;

    public NodePool nodePool;

    public GlobalPool globalPool;

    public String token = "";

    public List<Config> configList;

    public Map<String, Object> configs;

    /**
     * 关联节点，只有当其中的节点状态都为完成时，才能执行当前节点，保证流程执行的顺序性
     */
    public List<String> relatedNodes = new ArrayList<>();

    public List<NodeImpl> nextNodes = new ArrayList<>();

    /**
     * 指向父节点的ID，表明这个节点是某个可嵌套节点的子节点
     */
    public String parentNodeId = null;

    /**
     * 下面二者皆是为可嵌套节点做的准备
     */
    public NodeImpl subStartNode = null;
    public NodeImpl subEndNode = null;

    public NodeImpl(GlobalPool globalPool){
        nodeId = UUID.randomUUID().toString();
        nodePool = new NodePool(nodeId);
        this.globalPool = globalPool;
        configs = new HashMap<>();
    }

    public NodeImpl(GlobalPool globalPool, String nodeId){
        this.nodeId = nodeId;
        nodePool = new NodePool(nodeId);
        this.globalPool = globalPool;
        configs = new HashMap<>();
    }

    private void parseNumber(Config config){
        String raw = globalPool.parseConfig(config.getValue() != null ? config.getValue() : "", token).trim();
        int k = config.getK() <= 0 ? 1 : config.getK();
        if(k == 1) {
            configs.put(config.getName(), (int) Math.round(Double.parseDouble(raw)));
            return;
        }
        int val = (int) Math.round(Double.parseDouble(raw));
        int quantize = config.getQuantize();
        float rVal = (float) val / (float) k;
        BigDecimal decimal = new BigDecimal(rVal);
        decimal = decimal.setScale(quantize, RoundingMode.DOWN);
        configs.put(config.getName(), decimal.floatValue());
    }

    private void parseString(Config config){
        String value = globalPool.parseConfig(config.getValue(), token);
        configs.put(config.getName(), value);
    }

    private void parseList(Config config){
        // 不允许在Config中配置对象列表
        List<String> list = JSON.parseArray(config.getValue(), String.class);
        configs.put(config.getName(), list);
    }

    private void parseCondition(Config config){
        ConditionConfig conditionConfig = JSON.parseObject(config.getValue(), ConditionConfig.class);
        configs.put(config.getName(), conditionConfig);
        List<String> conditions = (List<String>) configs.computeIfAbsent("<|CONDITIONS|>", k->new ArrayList<>());
        conditions.add(config.getName());
    }

    private void parseMap(Config config){
        Map<String, String> map = JSON.parseObject(config.getValue(), HashMap.class);
        configs.put(config.getName(), map);
    }

    /**
     * 解析节点配置，需要在节点初始化后调用
     * @param configList  配置列表
     */
    public void parseConfig(List<Config> configList){
        // TODO: 容器配置类型的子类型配置解析
        // 在解析节点配置前一定要清空节点的全部配置，否则在循环节点中的节点将出现许多无意义的配置
        if (!configs.isEmpty()) configs.clear();
        List<String> conditions = (List<String>) configs.computeIfAbsent("<|CONDITIONS|>", k->new ArrayList<>());
        if (!conditions.isEmpty()) conditions.clear();
        if(configList == null) return;
        configList.forEach(config->{
            switch(config.getType()){
                case ConfigTypes.SLIDER:
                case ConfigTypes.NUMBER:
                    parseNumber(config);
                    break;
                case ConfigTypes.LIST:
                    parseList(config);
                    break;
                case ConfigTypes.CONDITION:
                    parseCondition(config);
                    break;
                case ConfigTypes.MAP:
                    parseMap(config);
                    break;
                case ConfigTypes.BOOLEAN:
                    configs.put(config.getName(), config.getValue().equalsIgnoreCase("true"));
                    break;
                default:
                    parseString(config);
            }
        });
    }

    /**
     * 这个函数提供给节点本身使用的，用于向结果处理器返回节点生命周期中出现的错误并停止流程
     * @param msg 消息
     */
    public void onNodeError(String msg){
        WorkflowResult result = WorkflowResult.builder()
                .token(token)
                .nodeId(nodeId)
                .state(NodeStates.ERROR)
                .msg(msg)
                .build();
        globalPool.pushWorkflowResult(token, result);
        globalPool.nodeError(token, nodeId);
        globalPool.workflowError(token);
    }

    /**
     * 这个函数是供节点本身设置失能的方法。若节点运行不满足条件，但不希望停止整个流程，需要将节点设置为失能。
     * 注意！若某一分支完全依赖本节点运行（即没有其它节点指向该分支），该分支将在当前节点失能后也随之失能。
     * 对于工作流中的分支，若其依赖的节点全部失能则当前分支失能。若当前分支有至少一个依赖节点成功运行且其它
     * 依赖节点都处于失能态，则当前分支正常运行。
     */
    public void onNodeDisabled(){
        globalPool.nodeDisabled(token, nodeId);
    }

    /**
     * 这个函数是供节点本身插入结果使用的
     * @param result 需要返回的结果
     */
    public void putWorkflowResult(WorkflowResult result){
        int workflowState = globalPool.getWorkflowState(token);
        // 重要！不要在流程结束后依然向结果列表中插入产生的结果
        if((workflowState & WorkflowStates.DONE) != 0 || (workflowState & WorkflowStates.ERROR) != 0) return;
        result.from = nodeId;
        globalPool.pushWorkflowResult(token, result);
    }

    /**
     * 获取当前节点所需的全部配置
     * @return 当前节点所需的配置名称
     */
    public List<ConfigVO> getNodeConfigs(){
        return new ArrayList<>();
    }

    public List<OutputVariableDes> getNodeOutputs(){
        return new ArrayList<>();
    }

    /**
     * 需要重写。
     * 节点前置Hook，用于初始化各项节点配置与所需上下文
     */
    @Override
    public void before(){}

    /**
     * 需要重写。
     * 节点运行核心Hook，用于实现节点的功能
     */
    @Override
    public void run(){}

    /**
     * 需要重写。
     * 节点后置Hook，用于处理节点运行结果或运行中产生的各项参数
     */
    @Override
    public void after(){}
}
