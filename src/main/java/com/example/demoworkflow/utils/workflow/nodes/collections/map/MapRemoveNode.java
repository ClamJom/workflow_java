package com.example.demoworkflow.utils.workflow.nodes.collections.map;

import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.misc.MapNodeHelper;
import com.example.demoworkflow.utils.workflow.misc.PoolVariableRefResolver;
import com.example.demoworkflow.utils.workflow.nodes.NodeImpl;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.vo.ConfigVO;

import java.util.List;
import java.util.Map;

/**
 * 字典删键：从字典中移除指定键；键不存在时不报错（幂等）。
 */
public class MapRemoveNode extends NodeImpl {

    public MapRemoveNode(GlobalPool globalPool) {
        super(globalPool);
        setNodeType(NodeType.MAP_REMOVE);
    }

    public MapRemoveNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        setNodeType(NodeType.MAP_REMOVE);
    }

    @Override
    public List<ConfigVO> getNodeConfigs() {
        return List.of(
                ConfigVO.builder()
                        .name("map")
                        .des("字典变量：推荐 {{变量名}}（与快捷插入一致）；也可填字面量池键名")
                        .type("String")
                        .required(true)
                        .build(),
                ConfigVO.builder()
                        .name("key")
                        .des("键：支持模板（如 {{键变量}}）或字面量")
                        .type("String")
                        .required(true)
                        .build());
    }

    @Override
    public void run() {
        String mapVar = PoolVariableRefResolver.resolvePoolKey(this, "map");
        if (mapVar == null) {
            onNodeError("请填写有效的字典变量引用（如 {{变量名}}）或池键名");
            return;
        }
        try {
            String key = MapNodeHelper.normalizeKey(configs.get("key"));
            Map<String, Object> next = MapNodeHelper.readMap(globalPool, token, mapVar);
            if (!next.containsKey(key)) {
                return;
            }
            next.remove(key);
            globalPool.put(token, mapVar, next);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            onNodeError(msg != null && !msg.isEmpty() ? msg : "字典删键失败");
        }
    }
}
