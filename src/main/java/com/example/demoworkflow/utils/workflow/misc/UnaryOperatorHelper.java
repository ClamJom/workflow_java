package com.example.demoworkflow.utils.workflow.misc;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 单目运算：单变量名解析、操作数整型化/浮点化，以及 ++/--/~ 求值（与 {@code parseNumber} 的 Integer/Float 一致）。
 * 来自Cursor-Composer 2
 */
public class UnaryOperatorHelper {

    private static final Pattern SINGLE_VAR = Pattern.compile("^\\s*\\{\\{\\s*([^}]+?)\\s*}}\\s*$");

    private UnaryOperatorHelper() {}

    /**
     * 当且仅当整段配置为单个 {@code {{name}}} 时返回去空白后的变量名。
     */
    public static Optional<String> singleVariableName(String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }
        var m = SINGLE_VAR.matcher(rawValue);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(m.group(1).trim());
    }

    /**
     * 将已解析的数值配置转为 int（与 {@code parseNumber} 的 int 语义一致）。
     */
    public static int toInt(Object parsedVar) {
        if (parsedVar == null) {
            throw new IllegalArgumentException("操作数不能为 null");
        }
        if (parsedVar instanceof Integer i) {
            return i;
        }
        if (parsedVar instanceof Number n) {
            return (int) Math.round(n.doubleValue());
        }
        throw new IllegalArgumentException("操作数不是数字: " + parsedVar.getClass());
    }

    /**
     * 与 {@code parseNumber} 中浮点配置一致，用于非整型 k 时参与算术的标量值。
     */
    public static double toDouble(Object parsedVar) {
        if (parsedVar == null) {
            throw new IllegalArgumentException("操作数不能为 null");
        }
        if (parsedVar instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("操作数不是数字: " + parsedVar.getClass());
    }

    /**
     * 按位非必须用整型；{@code ++} / {@code --} 在整型上产出 {@link Integer}，在 {@link Float}（k&gt;1 的 parse 结果）上产出 {@link Float}。
     */
    public static Number applyAsNumber(Object operand, String operator) {
        if (operator == null) {
            throw new IllegalArgumentException("运算符不能为 null");
        }
        if ("~".equals(operator)) {
            return ~toInt(operand);
        }
        if ("++".equals(operator) || "--".equals(operator)) {
            int d = ("++".equals(operator)) ? 1 : -1;
            if (operand instanceof Integer i) {
                return i + d;
            }
            if (operand instanceof Float f) {
                return f + d;
            }
            double v = toDouble(operand);
            return (float) (v + d);
        }
        throw new IllegalArgumentException("不支持的运算符: " + operator);
    }

    /**
     * 前缀自增/自减或按位非（整型路径，位运算/兼容旧逻辑用）。
     */
    public static int apply(int operand, String operator) {
        if (operator == null) {
            throw new IllegalArgumentException("运算符不能为 null");
        }
        return switch (operator) {
            case "++" -> operand + 1;
            case "--" -> operand - 1;
            case "~" -> ~operand;
            default -> throw new IllegalArgumentException("不支持的运算符: " + operator);
        };
    }
}
