package com.example.demoworkflow.utils.workflow.handler;

import com.example.demoworkflow.utils.workflow.nodes.NodeImpl;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.NodeStates;
import com.example.demoworkflow.utils.workflow.states.WorkflowStates;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.Lock;

@Service
@Slf4j
public class NodeHandler {
    @Resource
    private GlobalPool globalPool;

    @Autowired
    private RedissonClient redissonClient;

    private void logNodeProcessInfo(NodeImpl node, String message){
        log.info("节点类型:{} 节点ID：{} 消息：{}", node.getNodeType(), node.nodeId, message);
    }

    private void putNodeState(NodeImpl node, int stateCode, String message){
        WorkflowResult result = WorkflowResult.builder()
                .token(node.token)
                .state(stateCode)
                .msg(message)
                .build();
        node.putWorkflowResult(result);
    }

    /**
     * 处理运行节点核心运行逻辑前的逻辑，更新节点状态
     * @param node 节点
     */
    private void nodeBefore(NodeImpl node){
        if(globalPool.getWorkflowState(node.getToken()) == WorkflowStates.ERROR) return;
        if(globalPool.getNodeState(node.getToken(), node.nodeId) == NodeStates.DISABLED) return;
        if(!node.relatedNodes.isEmpty() && node.relatedNodes.stream().allMatch(pNode ->
                globalPool.getNodeState(node.token, pNode) == NodeStates.DISABLED)){
            globalPool.nodeDisabled(node.token, node.nodeId);
            return;
        }
        globalPool.initNodeState(node.token, node.nodeId);
        putNodeState(node, NodeStates.STAND_BY, "加载节点: "+node.nodeId);
        // 如果前置节点没有运行完成，认为当前节点没有就绪
        while(true){
            if(node.relatedNodes.isEmpty()) break;
            if(node.relatedNodes.stream().allMatch(item->{
                Lock lock = redissonClient.getLock(item);
                try {
                    lock.lock();
                    // 这是为循环节点预留的方法，他可能指向自己
                    if (item.equals(node.nodeId)) return true;
                    int pNodeState = globalPool.getNodeState(node.token, item);
                    if (pNodeState == NodeStates.NULL) return false;
                    return (pNodeState & NodeStates.DONE) != 0 || (pNodeState & NodeStates.DISABLED) != 0;
                }finally {
                    lock.unlock();
                }
            })) break;
        }
        node.parseConfig(node.configList);
        node.before();
    }

    /**
     * 处理节点核心运行逻辑
     * @param node 节点
     */
    private void nodeRun(NodeImpl node){
        if(globalPool.getWorkflowState(node.getToken()) == WorkflowStates.ERROR) return;
        if(globalPool.getNodeState(node.token, node.nodeId) == NodeStates.DISABLED) return;
        globalPool.nodeRunning(node.token, node.nodeId);
        putNodeState(node, NodeStates.RUNNING, "运行节点: "+node.nodeId);
        node.run();
    }

    /**
     * 处理下一个节点的运行逻辑，加锁是为了防止多分支指向这个节点时被调度两次
     * @param node  节点
     */
    private void handlerNextNode(NodeImpl node){
        Lock lock = redissonClient.getLock(node.nodeId);
        try{
            lock.lock();
            if(globalPool.getNodeState(node.token, node.nodeId) != NodeStates.NULL) return;
            Thread.ofVirtual().start(()->run(node));
        }finally {
            lock.unlock();
        }
    }

    /**
     * 处理节点运行完毕之后的逻辑
     * @param node  节点
     */
    private void nodeAfter(NodeImpl node){
        if(globalPool.getWorkflowState(node.getToken()) == WorkflowStates.ERROR) return;
        if(globalPool.getNodeState(node.token, node.nodeId) != NodeStates.DISABLED) {
            globalPool.nodeDone(node.token, node.nodeId);
            putNodeState(node, NodeStates.DONE, "节点运行完毕："+node.nodeId);
            node.after();
            globalPool.merge(node.token, node.nodePool.getPool());
        }
        for(NodeImpl nextNode: node.nextNodes){
            handlerNextNode(nextNode);
        }
    }

    /**
     * 处理节点运行出错时的逻辑
     * @param node  节点
     * @param e 错误信息
     */
    private void nodeError(NodeImpl node, Exception e){
        putNodeState(node, NodeStates.ERROR, e.getMessage());
        globalPool.nodeError(node.token, node.nodeId);
        globalPool.pushWorkflowResult(node.token,
                WorkflowResult.builder()
                        .token(node.token)
                        .state(WorkflowStates.ERROR)
                        .msg(e.getMessage())
                        .build());
        globalPool.workflowError(node.token);
        log.error("节点运行出错", e);
    }

    @Async("workflow")
    public void run(NodeImpl node){
        try{
            nodeBefore(node);
            nodeRun(node);
            nodeAfter(node);
        }catch(Exception e){
            nodeError(node, e);
        }
    }
}
