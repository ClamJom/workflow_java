package com.example.demoworkflow.utils.workflow.misc;

import com.example.demoworkflow.utils.workflow.pool.GlobalPool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流字典节点：变量池中的 Map 读取、键规范化与可变副本。
 */
public class MapNodeHelper {

    private MapNodeHelper() {}

    /**
     * 将配置中的键转为字符串（字典键一律使用字符串语义）。
     */
    public static String normalizeKey(Object keyObj) {
        if (keyObj == null) {
            throw new IllegalArgumentException("键不能为 null");
        }
        if (keyObj instanceof String s) {
            if (s.isBlank()) {
                throw new IllegalArgumentException("键不能为空");
            }
            return s.trim();
        }
        return String.valueOf(keyObj).trim();
    }

    public static Map<String, Object> readMap(GlobalPool globalPool, String token, String mapVarName) {
        Object raw = globalPool.get(token, mapVarName);
        if (raw == null) {
            throw new IllegalArgumentException("字典变量不存在: " + mapVarName);
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException("变量不是字典类型: " + mapVarName);
        }
        return mutableCopy((Map<?, ?>) raw);
    }

    /**
     * 将变量池中的 Map 转为可变副本；键统一为 {@link String}。
     */
    public static Map<String, Object> mutableCopy(Map<?, ?> source) {
        Map<String, Object> next = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : source.entrySet()) {
            next.put(String.valueOf(e.getKey()), e.getValue());
        }
        return next;
    }

    /**
     * 读取用于写入：变量不存在时抛出空指针异常；存在但非 Map 则抛错。
     */
    public static Map<String, Object> readMapForWrite(GlobalPool globalPool, String token, String mapVarName) {
        Object raw = globalPool.get(token, mapVarName);
        if (raw == null) {
            throw new NullPointerException("字典变量不存在: " + mapVarName);
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException("变量不是字典类型: " + mapVarName);
        }
        return mutableCopy((Map<?, ?>) raw);
    }
}
