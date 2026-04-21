package com.example.demoworkflow.utils.workflow.pool;

import com.alibaba.fastjson2.JSON;
import com.example.demoworkflow.pojo.Config;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.NodeStates;
import com.example.demoworkflow.utils.workflow.states.ResultHandlerStates;
import com.example.demoworkflow.utils.workflow.states.WorkflowStates;
import jakarta.annotation.Resource;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流全局变量池。绝对不要越过变量池直接修改状态！
 */
@Component
public class GlobalPool {
    @Value("${workflow.expire-time}")
    private long expireTime;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // 这个Redisson用作备用
    @Resource
    public RedissonClient redissonClient;

    private final Map<String, Queue<WorkflowResult>> results = new ConcurrentHashMap<>();

    /**
     * 向Redis变量池中写入一个值
     * @param token Workflow的Token，由Workflow的起始节点生成
     * @param key 变量名称
     * @param value 变量值，必须可以序列化（对象会被格式化为JSON字符串）
     */
    public void put(String token, String key, Object value){
        redisTemplate.opsForHash().put(token, key, value);
    }

    public void expire(String token){
        redisTemplate.expire(token, expireTime, TimeUnit.SECONDS);
    }

    public Object get(String token, String key){
        return redisTemplate.opsForHash().get(token, key);
    }

    public Map<Object, Object> getAll(String token){
        return redisTemplate.opsForHash().entries(token);
    }

    public void delete(String token, String key){
        redisTemplate.opsForHash().delete(token, key);
    }

    public void deleteAll(String token){
        redisTemplate.delete(token);
    }

    public void update(String token, String key, Object value){
        Object _val = this.get(token, key);
        if(_val != null){
            this.delete(token, key);
        }
        this.put(token, key, value);
    }

    public void merge(String token, Map<String, Object> map){
        for(String key : map.keySet()){
            this.put(token, key, map.get(key));
        }
    }

    public void initWorkflow(String token){
        put(token, "<|STATE|>", WorkflowStates.STAND_BY);
    }

    public void workflowError(String token){
        update(token, "<|STATE|>", WorkflowStates.ERROR);
    }

    public void workflowDone(String token){
        update(token, "<|STATE|>", WorkflowStates.DONE);
    }

    public void workflowRunning(String token){
        update(token, "<|STATE|>", WorkflowStates.RUNNING);
    }

    public int getWorkflowState(String token){
        Object state = get(token, "<|STATE|>");
        if(state == null) return WorkflowStates.NULL;
        return (int) state;
    }

    public void initResultHandler(String token){
        put(token, "<|RH_STATE|>", ResultHandlerStates.STAND_BY);
    }

    public void resultHandlerError(String token){
        put(token, "<|RH_STATE|>", ResultHandlerStates.ERROR);
    }

    public void resultHandlerDone(String token){
        put(token, "<|RH_STATE|>", ResultHandlerStates.DONE);
    }

    public void resultHandlerRunning(String token){
        put(token, "<|RH_STATE|>", ResultHandlerStates.RUNNING);
    }

    public int getResultHandlerState(String token){
        Object state = get(token, "<|RH_STATE|>");
        if(state == null) return ResultHandlerStates.NULL;
        return (int) state;
    }

    /**
     * 向结果队列中插入一份结果
     * @param result 节点运行结果
     */
    public void pushWorkflowResult(String token, WorkflowResult result){
        Queue<WorkflowResult> resultQueue = results.computeIfAbsent(token, k -> new ArrayDeque<>());
        resultQueue.add(result);
    }

    /**
     * 从结果队列中取出一份结果
     * @return 结果队列顶端的结果
     */
    public WorkflowResult pollWorkflowResult(String token){
        Queue<WorkflowResult> resultQueue = results.computeIfAbsent(token, k -> new ArrayDeque<>());
        return resultQueue.poll();
    }

    private String nodeStateKeyFactory(String uuid){
        return "node_state:"+uuid;
    }

    /**
     * 初始化节点状态
     * @param token 工作流Token
     * @param uuid  节点Uuid
     */
    public void initNodeState(String token, String uuid){
        put(token, nodeStateKeyFactory(uuid), NodeStates.STAND_BY);
    }

    private void updateNodeState(String token, String uuid, int nodeState){
        update(token, nodeStateKeyFactory(uuid), nodeState);
    }

    public void deleteNodeState(String token, String uuid){
        delete(token, nodeStateKeyFactory(uuid));
    }

    /**
     * 节点运行出错
     * @param token 工作流Token
     * @param uuid 节点Uuid
     */
    public void nodeError(String token, String uuid){
        updateNodeState(token, uuid, NodeStates.STAND_BY);
    }

    /**
     * 节点正在运行
     * @param token 工作流Token
     * @param uuid 节点Uuid
     */
    public void nodeRunning(String token, String uuid){
        updateNodeState(token, uuid, NodeStates.RUNNING);
    }

    /**
     * 节点运行完毕
     * @param token 工作流Token
     * @param uuid 节点Uuid
     */
    public void nodeDone(String token, String uuid){
        // 从运行至结束中间的代码作为预留状态（单数为某种状态，`| 0x1`表示在这个状态下出错）
        updateNodeState(token, uuid, NodeStates.DONE);
    }

    /**
     * 节点失能
     * 这代表着节点所在分支不会生效。这个状态对于多分支工作流十分重要
     * @param token 工作流Token
     * @param uuid  节点Uuid
     */
    public void nodeDisabled(String token, String uuid){
        updateNodeState(token, uuid, NodeStates.DISABLED);
    }

    public int getNodeState(String token, String uuid){
        Object oState = get(token, nodeStateKeyFactory(uuid));
        if (oState == null) return NodeStates.NULL;
        return (int) oState;
    }

    private String breakSignalFactory(String loopId){
        return String.format("%s:break", loopId);
    }

    private void putBreakSignal(String token, String loopId, boolean t){
        put(token, breakSignalFactory(loopId), t);
    }

    public void setBreakSignal(String token, String loopId){
        putBreakSignal(token, loopId, true);
    }

    public void resetBreakSignal(String token, String loopId){
        putBreakSignal(token, loopId, false);
    }

    public boolean getBreakSignal(String token, String loopId){
        Boolean s = (Boolean) get(token, breakSignalFactory(loopId));
        if (s == null) {
            resetBreakSignal(token, loopId);
            s = false;
        }
        return s;
    }

    /**
     * 向字符串注入变量池中对应变量的值
     * @param dst 目标字符串
     * @param token 当前工作流的Token
     * @return 注入之后的字符串
     */
    public String parseConfig(String dst, String token){
        Map<Object, Object> pool = this.getAll(token);
        Set<String> variables = new HashSet<>();
        Pattern pattern = Pattern.compile("\\{\\{(.*?)}}");
        Matcher matcher = pattern.matcher(dst);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        for(String item : variables){
            if(pool.get(item) == null) continue;
            Object cache  = get(token, item);
            if (cache instanceof String){
                dst = dst.replaceAll("\\{\\{"+item+ "}}", (String) cache);
                continue;
            }
            dst = dst.replaceAll("\\{\\{"+item+ "}}", JSON.toJSONString(get(token, item)));
        }
        return dst;
    }

    /**
     * 通过变量名直接获取对象
     * @param name  变量名
     * @param token 当前工作流的Token
     * @return  对象
     */
    public Object parseObject(String name, String token){
        name = name.replaceAll("\\{\\{", "");
        name = name.replaceAll("}}", "");
        Object ret = get(token, name);
        return ret != null ? ret : name;
    }
}
