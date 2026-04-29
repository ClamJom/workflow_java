package com.example.demoworkflow.utils.workflow.nodes.collections.map;

import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.misc.ListNodeHelper;
import com.example.demoworkflow.utils.workflow.misc.MapNodeHelper;
import com.example.demoworkflow.utils.workflow.misc.PoolVariableRefResolver;
import com.example.demoworkflow.utils.workflow.nodes.NodeImpl;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.vo.ConfigVO;

import java.util.List;
import java.util.Map;

/**
 * 字典写入：新增或覆盖键值；若字典变量尚不存在则创建空字典后再写入。
 */
public class MapPutNode extends NodeImpl {

    public MapPutNode(GlobalPool globalPool) {
        super(globalPool);
        setNodeType(NodeType.MAP_PUT);
    }

    public MapPutNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        setNodeType(NodeType.MAP_PUT);
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
                        .build(),
                ConfigVO.builder()
                        .name("value")
                        .des("值（可与模板或其它节点输出配合）")
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
            Map<String, Object> next = MapNodeHelper.readMapForWrite(globalPool, token, mapVar);
            next.put(key, ListNodeHelper.normalizeConfigValue(configs.get("value")));
            globalPool.put(token, mapVar, next);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            onNodeError(msg != null && !msg.isEmpty() ? msg : "字典写入失败");
        }
    }
}
