package com.example.demoworkflow.utils.workflow.nodes;

import com.alibaba.fastjson2.JSON;
import com.example.demoworkflow.utils.types.ConfigTypes;
import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.OutputVariableDes;
import com.example.demoworkflow.utils.workflow.handler.SubNodeHandler;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.vo.ConfigVO;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class LoopNode extends NodeImpl{
    private final List<Object> outputs = new ArrayList<>();
    public LoopNode(GlobalPool globalPool) {
        super(globalPool);
        this.setNodeType(NodeType.LOOP);
    }

    public LoopNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        this.setNodeType(NodeType.LOOP);
    }

    @Override
    public List<ConfigVO> getNodeConfigs(){
        return List.of(ConfigVO.builder()
                .des("循环次数")
                .name("loop")
                .value("1")
                .max(Integer.MAX_VALUE)
                .min(1)
                .k(1)
                .type(ConfigTypes.NUMBER)
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

    @Override
    public List<OutputVariableDes> getNodeOutputs(){
        return List.of(OutputVariableDes.builder()
                .name("output")
                .type("List")
                .des("输出列表")
                .build());
    }

    /**
     * 清理子节点的状态记录。由于节点运行时需要通过上一节点的状态是否为空来判断上一节点是否已经运行，
     * 因此如果不清理节点状态将会导致节点进入无限等待。
     */
    protected void clearNodeStates(){
        Set<String> nodeIds = new HashSet<>();
        Queue<NodeImpl> stack = new LinkedList<>();
        stack.add(subStartNode);
        while (!stack.isEmpty()){
            NodeImpl node = stack.poll();
            node.nextNodes.forEach(n -> {
                nodeIds.add(n.getNodeId());
                stack.offer(n);
            });
        }
        for (String nodeId : nodeIds){
            globalPool.deleteNodeState(token, nodeId);
        }
    }

    protected void putLoopIIntoPool(int i){
        globalPool.put(token, nodeId+":"+"loop_i", i);
    }

    protected boolean runCore(Integer timeout, int i){
        putLoopIIntoPool(i);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        SubNodeHandler handler = new SubNodeHandler(globalPool);
        handler.run(subStartNode, this, countDownLatch);
        AtomicBoolean waitTrue = new AtomicBoolean(true);
        try {
            if (timeout != 0)
                waitTrue.set(countDownLatch.await(timeout, TimeUnit.MILLISECONDS));

            else
                countDownLatch.await();
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }
        if (!waitTrue.get()){
            onNodeError("等待循环执行完成超时");
            return true;
        }
        if (globalPool.getBreakSignal(token, nodeId)) return true;
        // 序列化与反序列化深拷贝
        Object output = subEndNode.nodePool.get("output");
        String strCopy = JSON.toJSONString(output);
        outputs.add(JSON.parse(strCopy));
        clearNodeStates();
        return false;
    }

    @Override
    public void before(){
        globalPool.deleteBreakSignal(token, nodeId);
        clearNodeStates();
    }

    @Override
    public void run(){
        assert subStartNode != null;
        assert subEndNode != null;
        Integer loop = (Integer) configs.get("loop");
        Integer timeout = (Integer) configs.get("timeout");
        if (timeout == null) timeout = 0;
        if (loop == null || loop < 1) return;
        for (int i = 0; i < loop; i++) {
            if(runCore(timeout, i)) break;
        }
        nodePool.put("output", outputs);
    }
}
