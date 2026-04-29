package com.example.demoworkflow.utils.workflow.misc;

import com.example.demoworkflow.pojo.Config;
import com.example.demoworkflow.utils.workflow.nodes.NodeImpl;

import java.util.Optional;

/**
 * 将配置中的「变量池键」解析为实际 hash 字段名：单独使用 {@code {{varName}}} 时表示引用名为 {@code varName}
 * 的变量槽位（避免被 {@link com.example.demoworkflow.utils.workflow.pool.GlobalPool#parseConfig(String, String)}
 * 展开成列表/字典的 JSON）；其它写法仍按模板展开或字面量处理。
 */
public class PoolVariableRefResolver {

    private PoolVariableRefResolver() {}

    /**
     * @param node          当前节点（读取 {@code configList} 与 {@code token}）
     * @param configField   配置项名称，如 {@code list}、{@code map}
     * @return 变量池中的键名；无法解析时返回 null
     */
    public static String resolvePoolKey(NodeImpl node, String configField) {
        String raw = findRawConfigValue(node, configField);
        if (raw != null) {
            raw = raw.trim();
            if (!raw.isEmpty()) {
                Optional<String> single = UnaryOperatorHelper.singleVariableName(raw);
                if (single.isPresent()) {
                    String name = single.get().trim();
                    return name.isEmpty() ? null : name;
                }
                String expanded = node.getGlobalPool().parseConfig(raw, node.getToken()).trim();
                if (!expanded.isEmpty()) {
                    return expanded;
                }
            }
        }
        return fallbackFromParsedConfig(node, configField);
    }

    private static String findRawConfigValue(NodeImpl node, String configField) {
        if (node.getConfigList() == null) {
            return null;
        }
        for (Config c : node.getConfigList()) {
            if (configField.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private static String fallbackFromParsedConfig(NodeImpl node, String configField) {
        Object v = node.getConfigs().get(configField);
        if (v instanceof String s) {
            String t = s.trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }
}
