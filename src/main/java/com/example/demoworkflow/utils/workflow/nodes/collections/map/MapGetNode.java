package com.example.demoworkflow.utils.workflow.nodes.collections.map;

import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.OutputVariableDes;
import com.example.demoworkflow.utils.workflow.misc.MapNodeHelper;
import com.example.demoworkflow.utils.workflow.misc.PoolVariableRefResolver;
import com.example.demoworkflow.utils.workflow.nodes.NodeImpl;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.vo.ConfigVO;

import java.util.List;
import java.util.Map;

/**
 * 字典查询：按键取值，结果写入节点输出 {@code output}；键不存在时 {@code output} 为 null。
 */
public class MapGetNode extends NodeImpl {

    public MapGetNode(GlobalPool globalPool) {
        super(globalPool);
        setNodeType(NodeType.MAP_GET);
    }

    public MapGetNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        setNodeType(NodeType.MAP_GET);
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
    public List<OutputVariableDes> getNodeOutputs() {
        return List.of(OutputVariableDes.builder()
                .name("output")
                .des("该键对应的值；键不存在时为 null")
                .type("Object")
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
            Map<String, Object> map = MapNodeHelper.readMap(globalPool, token, mapVar);
            nodePool.put("output", map.get(key));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            onNodeError(msg != null && !msg.isEmpty() ? msg : "字典查询失败");
        }
    }
}
