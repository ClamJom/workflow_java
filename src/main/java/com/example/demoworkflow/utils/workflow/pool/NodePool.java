package com.example.demoworkflow.utils.workflow.pool;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NodePool {
    private final String nodeId;

    @Getter
    private final Map<String, Object> pool = new ConcurrentHashMap<>();

    /**
     * 不提供无参数构造，因为其中的值需要与全局变量池合并，没有NodeId无法区分所属的变量且容易导致变量冲突
     * @param nodeId NodeId
     */
    public NodePool(String nodeId){
        this.nodeId = nodeId;
    }

    private String keyFactory(String key){
        return this.nodeId + ":" + key;
    }

    public void put(String key, Object value){
        pool.put(keyFactory(key), value);
    }

    public void delete(String key){
        pool.remove(keyFactory(key));
    }

    public void update(String key, Object value){
        pool.put(keyFactory(key), value);
    }

    public Object get(String key){
        return pool.get(keyFactory(key));
    }
}
