package com.example.demoworkflow.utils.workflow.dto;

import com.example.demoworkflow.mapper.ConfigMapper;
import com.example.demoworkflow.utils.workflow.nodes.NodeImpl;
import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.WorkflowStates;
import com.example.demoworkflow.vo.EdgeVO;
import com.example.demoworkflow.vo.NodeVO;
import com.example.demoworkflow.vo.WorkflowVO;
import jodd.util.StringUtil;
import lombok.Data;

import java.util.*;

@Data
public class Workflow {
    private String token;

    private GlobalPool globalPool;

    private List<NodeImpl> nodes;

    private NodeImpl startNode;

    public Workflow(GlobalPool globalPool){
        token = UUID.randomUUID().toString();
        this.globalPool = globalPool;
        nodes = new ArrayList<>();

        globalPool.initWorkflow(token);
    }

    public boolean isRunning(){
        return (globalPool.getWorkflowState(token) & WorkflowStates.RUNNING) != 0;
    }

    public boolean isEnded(){
        return (globalPool.getWorkflowState(token) & WorkflowStates.DONE) != 0 ||
                (globalPool.getWorkflowState(token) & WorkflowStates.ERROR) != 0 ||
                (globalPool.getWorkflowState(token) & WorkflowStates.ABORT) != 0;
    }

    /**
     * 拓扑排序判环
     * @param edges 有向边
     * @return  是否存在环
     */
    private static boolean hasCircle(List<EdgeVO> edges){
        Map<String, Set<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for(EdgeVO edge: edges){
            Set<String> outEdgeSet = graph.computeIfAbsent(edge.from, k -> new HashSet<>());
            graph.computeIfAbsent(edge.to, k -> new HashSet<>());
            inDegree.computeIfAbsent(edge.from, k -> 0);
            int inCount = inDegree.computeIfAbsent(edge.to, k -> 0);
            outEdgeSet.add(edge.to);
            inDegree.put(edge.to, inCount + 1);
        }
        Queue<String> zeroInDegree = new ArrayDeque<>();
        for(String key: inDegree.keySet()){
            if(inDegree.get(key) == 0) zeroInDegree.add(key);
        }
        int processNodes = 0;
        while(!zeroInDegree.isEmpty()){
            String node = zeroInDegree.poll();
            processNodes++;
            Set<String> neighborNodes = graph.get(node);
            if(neighborNodes == null) continue;
            for(String targetNode: neighborNodes){
                int inDegreeCount = inDegree.get(targetNode);
                inDegree.put(targetNode, inDegreeCount - 1);
                if(inDegreeCount - 1 == 0) zeroInDegree.add(targetNode);
            }
        }
        return processNodes != inDegree.size();
    }

    /**
     * 向对应的工作流结果队列中放入非法工作流信息
     * @param workflow  工作流对象
     * @param message   消息
     */
    public static void putInvalidMessage(Workflow workflow, String message){
        String token = workflow.getToken();
        GlobalPool globalPool = workflow.getGlobalPool();
        globalPool.pushWorkflowResult(token, WorkflowResult.builder()
                .token(token)
                .msg(message)
                .state(WorkflowStates.ERROR)
                .build());
        globalPool.workflowError(token);
    }

    /**
     * 无论转换是否成功，都将返回一个工作流对象。此外，这里应当进行工作流预检
     * @param vo    工作流VO
     * @param globalPool    全局变量池
     * @return  工作流对象
     */
    public static Workflow castFromVO(WorkflowVO vo, GlobalPool globalPool){
        Workflow workflow = new Workflow(globalPool);
        List<NodeImpl> nodes = new ArrayList<>();
        Map<String, NodeImpl> nodeMap = new HashMap<>();
        // 入度统计
        Map<String, Integer> inDegree = new HashMap<>();
        // 出度统计
        Map<String, Integer> outDegree = new HashMap<>();
        boolean withEndNode = false;
        for(NodeVO node: vo.nodes){
           NodeImpl nodeInstance = NodeType.createNodeInstanceWithNodeIdByCode(node.type, globalPool, node.id);
           if(nodeInstance == null){
               putInvalidMessage(workflow, String.format("从JSON构造节点对象失败！节点ID:%s", node.id));
               return workflow;
           }
           nodeInstance.setToken(workflow.getToken());
           if (!StringUtil.isEmpty(node.parent)){
               nodeInstance.parentNodeId = node.parent;
           }
           // 配置项的初始化应当在节点执行前进行
           nodeInstance.configList = ConfigMapper.INSTANCE.listConfigVOToListConfig(node.configs);
           nodes.add(nodeInstance);
           nodeMap.put(node.id, nodeInstance);
           // 对起始节点和结束节点的父节点判空已保证其属于最外层工作流
           if(nodeInstance.getNodeType() == NodeType.START && StringUtil.isEmpty(nodeInstance.parentNodeId)){
               workflow.startNode = nodeInstance;
           }
           if(nodeInstance.getNodeType() == NodeType.END && StringUtil.isEmpty(nodeInstance.parentNodeId)){
               withEndNode = true;
           }
        }
        if (!withEndNode){
            putInvalidMessage(workflow, "不存在结束节点");
            return workflow;
        }
        if(workflow.startNode == null) {
            putInvalidMessage(workflow,"流程不存在起始节点");
            return workflow;
        }
        if(hasCircle(vo.edges)) {
            putInvalidMessage(workflow, "不允许存在环结构");
            return workflow;
        }
        for(EdgeVO edge: vo.edges) {
            nodeMap.get(edge.to).relatedNodes.add(edge.from);
            nodeMap.get(edge.from).nextNodes.add(nodeMap.get(edge.to));
            inDegree.computeIfAbsent(edge.to, k -> 1);
            inDegree.computeIfPresent(edge.to, (k, v) -> v + 1);
            outDegree.computeIfAbsent(edge.from, k -> 1);
            outDegree.computeIfPresent(edge.from, (k, v) -> v + 1);
        }
        for (NodeImpl node: nodes){
            if (node.getNodeType() == NodeType.BREAK) {
                if (StringUtil.isEmpty(node.parentNodeId)) {
                    putInvalidMessage(workflow, String.format("Break节点必须置于循环容器内！节点ID:%s", node.nodeId));
                    return workflow;
                }
                NodeImpl breakParent = nodeMap.get(node.parentNodeId);
                if (breakParent == null
                        || (breakParent.getNodeType() != NodeType.LOOP && breakParent.getNodeType() != NodeType.WHILE)) {
                    putInvalidMessage(workflow, String.format("Break节点只能置于循环或条件循环容器内！节点ID:%s", node.nodeId));
                    return workflow;
                }
                for (NodeImpl next : node.nextNodes) {
                    if (next.getNodeType() != NodeType.END) {
                        putInvalidMessage(workflow, String.format("Break节点的出边只能指向结束节点！节点ID:%s", node.nodeId));
                        return workflow;
                    }
                    if (!Objects.equals(next.parentNodeId, node.parentNodeId)) {
                        putInvalidMessage(workflow, String.format("Break节点的出边只能指向同容器内的结束节点！节点ID:%s", node.nodeId));
                        return workflow;
                    }
                }
            }
            if (node.getNodeType() != NodeType.END && outDegree.computeIfAbsent(node.nodeId, k -> 0) == 0){
                putInvalidMessage(workflow, String.format("不允许没有出边的节点！节点ID:%s", node.nodeId));
                return workflow;
            }
            if (node.getNodeType() != NodeType.START && inDegree.computeIfAbsent(node.nodeId, k -> 0) == 0){
                putInvalidMessage(workflow, String.format("不允许没有入边的节点！节点ID:%s", node.nodeId));
                return workflow;
            }
            if (StringUtil.isEmpty(node.parentNodeId)) continue;
            NodeImpl parentNode = nodeMap.get(node.parentNodeId);
            if (parentNode == null){
                putInvalidMessage(workflow, String.format("不允许节点父节点指向空节点！节点ID:%s", node.nodeId));
                return workflow;
            }
            if ((parentNode.getNodeType().getCode() & NodeType.NESTABLE_FLAG) == 0){
                putInvalidMessage(workflow, String.format("不允许父节点为不可嵌套节点！节点ID:%s", node.nodeId));
                return workflow;
            }
            if (node.getNodeType() == NodeType.START){
                parentNode.subStartNode = node;
            }
            if (node.getNodeType() == NodeType.END){
                parentNode.subEndNode = node;
            }
        }
        workflow.getNodes().addAll(nodes);
        return workflow;
    }
}
