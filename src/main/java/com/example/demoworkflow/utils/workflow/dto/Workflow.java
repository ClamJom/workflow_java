package com.example.demoworkflow.utils.workflow.dto;

import com.example.demoworkflow.mapper.ConfigMapper;
import com.example.demoworkflow.pojo.Config;
import com.example.demoworkflow.utils.workflow.nodes.NodeImpl;
import com.example.demoworkflow.utils.workflow.nodes.NodeType;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.states.WorkflowStates;
import com.example.demoworkflow.vo.WorkflowVO;
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
                (globalPool.getWorkflowState(token) & WorkflowStates.ERROR) != 0;
    }

    public static Workflow castFromVO(WorkflowVO vo, GlobalPool globalPool){
        Workflow workflow = new Workflow(globalPool);
        List<NodeImpl> nodes = new ArrayList<>();
        Map<String, NodeImpl> nodeMap = new HashMap<>();
        vo.nodes.forEach(node->{
           NodeImpl nodeInstance = NodeType.createNodeInstanceWithNodeIdByCode(node.type, globalPool, node.id);
           if(nodeInstance == null){
               throw new NullPointerException("从JSON构造节点对象失败！");
           }
           nodeInstance.setToken(workflow.getToken());
//           List<Config> configs = ConfigMapper.INSTANCE.listConfigVOToListConfig(node.configs);
//           nodeInstance.parseConfig(configs);
            // 配置项的初始化应当在节点执行前进行
            nodeInstance.configList = ConfigMapper.INSTANCE.listConfigVOToListConfig(node.configs);
           nodes.add(nodeInstance);
           nodeMap.put(node.id, nodeInstance);
           if(nodeInstance.getNodeType() == NodeType.START){
               workflow.startNode = nodeInstance;
           }
        });
        vo.edges.forEach(edge -> {
            nodeMap.get(edge.to).relatedNodes.add(edge.from);
            // 注意自循环！
            nodeMap.get(edge.from).nextNodes.add(nodeMap.get(edge.to));
        });
        workflow.getNodes().addAll(nodes);
        return workflow;
    }
}
