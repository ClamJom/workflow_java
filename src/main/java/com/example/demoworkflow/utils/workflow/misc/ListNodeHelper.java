package com.example.demoworkflow.utils.workflow.misc;

import com.alibaba.fastjson2.JSON;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流列表节点：从变量池读取列表、规范化与下标解析。
 */
@Slf4j
public class ListNodeHelper {

    private ListNodeHelper() {}

    @SuppressWarnings("unchecked")
    public static List<Object> readList(GlobalPool globalPool, String token, String listVarName) {
        Object raw = globalPool.get(token, listVarName);
        if (raw == null) {
            throw new IllegalArgumentException("列表变量不存在: " + listVarName);
        }
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException("变量不是列表类型: " + listVarName);
        }
        return (List<Object>) raw;
    }

    /**
     * 返回可变副本，写回变量池时应使用 {@link GlobalPool#put(String, String, Object)} 覆盖原变量。
     */
    public static List<Object> mutableCopy(List<Object> source) {
        return new ArrayList<>(source);
    }

    public static int parseIndex(Object indexObj) {
        return UnaryOperatorHelper.toInt(indexObj);
    }

    public static void requireIndexInRange(int index, int size, String listVarName) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException(
                    String.format("下标越界: index=%d, size=%d, 列表=%s", index, size, listVarName));
        }
    }

    /**
     * 将节点配置中的标量（多为 String）尽量还原为 JSON 数字/布尔/对象/数组，便于写入列表；
     * 无法解析时保留原字符串。
     */
    public static Object normalizeConfigValue(Object raw) {
        if (!(raw instanceof String s)) {
            return raw;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return s;
        }
        if ("true".equalsIgnoreCase(t)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(t)) {
            return Boolean.FALSE;
        }
        try {
            return JSON.parse(t);
        } catch (Exception ignored) {
            return s;
        }
    }
}
