package com.example.demoworkflow.utils.workflow.nodes.base;

import com.example.demoworkflow.pojo.Config;
import com.example.demoworkflow.utils.types.ConfigTypes;
import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.OutputVariableDes;
import com.example.demoworkflow.utils.workflow.handler.SubNodeHandler;
import com.example.demoworkflow.utils.workflow.nodes.NodeImpl;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.states.WorkflowStates;
import com.example.demoworkflow.vo.ConfigVO;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * 函数调用节点。
 * <p>
 * 通过配置引用的函数定义节点 ID，找到对应的 {@link FunctionDefNode}，
 * 将调用参数注入其内部 StartNode（覆盖默认值），然后执行函数子图。
 * 执行完成后收集输出并放入当前节点的变量池。
 */
@Slf4j
public class FunctionCallNode extends NodeImpl {

    /** 默认执行超时（毫秒） */
    private static final long DEFAULT_TIMEOUT = 60000;

    public FunctionCallNode(GlobalPool globalPool) {
        super(globalPool);
        this.setNodeType(NodeType.FUNCTION_CALL);
    }

    public FunctionCallNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        this.setNodeType(NodeType.FUNCTION_CALL);
    }

    @Override
    public List<ConfigVO> getNodeConfigs() {
        return List.of(
                ConfigVO.builder()
                        .name("functionDefName")
                        .des("函数定义节点名称")
                        .type(ConfigTypes.STRING)
                        .value("")
                        .required(true)
                        .build(),
                ConfigVO.builder()
                        .name("args")
                        .des("调用参数（键为参数名，值为实参，留空使用默认值）")
                        .type(ConfigTypes.MAP)
                        .value("{}")
                        .required(false)
                        .build(),
                ConfigVO.builder()
                        .name("timeout")
                        .des("执行超时（毫秒），0 表示永不超时")
                        .type(ConfigTypes.NUMBER)
                        .value("60000")
                        .max(Integer.MAX_VALUE)
                        .min(0)
                        .k(1)
                        .required(true)
                        .build()
        );
    }

    @Override
    public List<OutputVariableDes> getNodeOutputs() {
        return List.of(
                OutputVariableDes.builder()
                        .name("output")
                        .type("Map")
                        .des("函数返回值")
                        .build()
        );
    }

    @Override
    public void before() {
        String funcDefName = (String) configs.get("functionDefName");
        if (funcDefName == null || funcDefName.isEmpty()) {
            onNodeError("函数调用节点必须指定函数定义节点名称（functionDefName）！");
            return;
        }
        Map<String, NodeImpl> nodeMap = globalPool.getWorkflowNodeMap(token);
        NodeImpl funcDefNode = findFunctionDefNode(nodeMap, funcDefName);
        if (funcDefNode == null) {
            onNodeError("未找到名称为「" + funcDefName + "」的函数定义节点");
            return;
        }
        if (funcDefNode.subStartNode == null || funcDefNode.subEndNode == null) {
            onNodeError("函数定义子流程不完整（缺少起始或结束节点），名称：" + funcDefName);
        }
    }

    /**
     * 在节点映射表中按名称查找函数定义节点
     */
    private NodeImpl findFunctionDefNode(Map<String, NodeImpl> nodeMap, String name) {
        if (nodeMap == null) return null;
        for (NodeImpl node : nodeMap.values()) {
            if (node.getNodeType() == NodeType.FUNCTION_DEF && name.equals(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }

    /**
     * 清理函数定义子图中所有节点的状态记录，
     * 避免旧状态影响本轮执行（与 {@code LoopNode.clearNodeStates} 同理）。
     */
    private void clearFuncNodeStates(NodeImpl funcDefNode) {
        Set<String> nodeIds = new HashSet<>();
        Queue<NodeImpl> stack = new LinkedList<>();
        stack.add(funcDefNode.subStartNode);
        nodeIds.add(funcDefNode.subStartNode.nodeId);
        while (!stack.isEmpty()) {
            NodeImpl node = stack.poll();
            if (node.nextNodes != null) {
                node.nextNodes.forEach(n -> {
                    nodeIds.add(n.getNodeId());
                    stack.offer(n);
                });
            }
        }
        for (String nid : nodeIds) {
            globalPool.deleteNodeState(token, nid);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        String funcDefName = (String) configs.get("functionDefName");
        Map<String, NodeImpl> nodeMap = globalPool.getWorkflowNodeMap(token);
        NodeImpl funcDefNode = findFunctionDefNode(nodeMap, funcDefName);
        if (funcDefNode == null) {
            onNodeError("运行前未找到名称为「" + funcDefName + "」的函数定义节点");
            return;
        }

        // 读取调用参数（key=参数名, value=实参）
        Map<String, Object> args = (Map<String, Object>) configs.get("args");
        if (args == null) args = new HashMap<>();

        // 读取超时配置
        Integer timeout = (Integer) configs.get("timeout");
        if (timeout == null) timeout = (int) DEFAULT_TIMEOUT;

        // 获取函数内部的 StartNode（其 configList 定义了参数及默认值）
        NodeImpl startNode = funcDefNode.subStartNode;
        List<Config> originalConfigList = startNode.configList;

        // 加锁防止同一函数定义被并行调用时 configList 被并发修改
        Lock lock = globalPool.redissonClient.getLock("func_call:" + funcDefNode.nodeId);
        lock.lock();
        try {
            // 用实参覆盖 StartNode 的默认值
            if (originalConfigList != null && !args.isEmpty()) {
                for (Config paramConfig : originalConfigList) {
                    String argValue = (String) args.get(paramConfig.getName());
                    if (argValue != null) {
                        paramConfig.setValue(argValue);
                    }
                }
            }

            // 清理旧状态
            clearFuncNodeStates(funcDefNode);

            // 执行函数子图
            CountDownLatch latch = new CountDownLatch(1);
            SubNodeHandler handler = new SubNodeHandler(globalPool);
            handler.run(startNode, funcDefNode, latch);

            boolean completed;
            if (timeout != 0) {
                completed = latch.await(timeout, TimeUnit.MILLISECONDS);
            } else {
                latch.await();
                completed = true;
            }

            if (!completed) {
                onNodeError("函数执行超时：" + funcDefName);
                return;
            }

            if (globalPool.getWorkflowState(token) == WorkflowStates.ERROR) {
                onNodeError("函数执行出错：" + funcDefName);
                return;
            }

            // 收集函数输出（子 EndNode 的 nodePool 中的 output 即为返回值）
            Object output = funcDefNode.subEndNode.nodePool.get("output");
            if (output == null) return;
            nodePool.put("output", output);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onNodeError("函数执行被中断：" + funcDefName);
        } finally {
            lock.unlock();
        }
    }
}
