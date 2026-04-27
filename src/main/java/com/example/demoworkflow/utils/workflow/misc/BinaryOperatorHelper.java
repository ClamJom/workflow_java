package com.example.demoworkflow.utils.workflow.misc;

/**
 * 双目算术与位运算：与 {@code parseNumber} 一致，两操作数均为 {@link Integer}（k=1）时走整型算术（除法为整除）；
 * 任一为浮点或其它标量时按 {@code double} 计算，结果以 {@link Float} 表示以与配置中的浮点存储一致。
 * 位运算始终按 {@code int} 语义（操作数先经 {@link UnaryOperatorHelper#toInt} 归一化）。
 * 来自Cursor-Composer 2
 */
public class BinaryOperatorHelper {

    private static final int INT_SHIFT_MASK = 0x1f;

    private BinaryOperatorHelper() {}

    /**
     * 根据操作数类型与运算符求值，返回 {@link Integer} 或 {@link Float}。
     */
    public static Number applyAsNumber(Object left, Object right, String operator) {
        if (operator == null) {
            throw new IllegalArgumentException("运算符不能为 null");
        }
        if (isBitwise(operator)) {
            int l = UnaryOperatorHelper.toInt(left);
            int r = UnaryOperatorHelper.toInt(right);
            return applyIntBitwise(l, r, operator);
        }
        if (left instanceof Integer li && right instanceof Integer ri) {
            return applyIntArithmeticPrimitive(li, ri, operator);
        }
        double a = UnaryOperatorHelper.toDouble(left);
        double b = UnaryOperatorHelper.toDouble(right);
        return applyDoubleArithmetic(a, b, operator);
    }

    private static boolean isBitwise(String operator) {
        return switch (operator) {
            case "&", "|", "^", "<<", ">>", ">>>" -> true;
            default -> false;
        };
    }

    private static int applyIntBitwise(int left, int right, String operator) {
        return switch (operator) {
            case "&" -> left & right;
            case "|" -> left | right;
            case "^" -> left ^ right;
            case "<<" -> left << (right & INT_SHIFT_MASK);
            case ">>" -> left >> (right & INT_SHIFT_MASK);
            case ">>>" -> left >>> (right & INT_SHIFT_MASK);
            default -> throw new IllegalArgumentException("不支持的位运算符: " + operator);
        };
    }

    private static int applyIntArithmeticPrimitive(int left, int right, String operator) {
        return switch (operator) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> {
                if (right == 0) {
                    throw new IllegalArgumentException("除数不能为 0");
                }
                yield left / right;
            }
            case "%" -> {
                if (right == 0) {
                    throw new IllegalArgumentException("取模的右操作数不能为 0");
                }
                yield left % right;
            }
            default -> throw new IllegalArgumentException("不支持的算术运算符: " + operator);
        };
    }

    private static Float applyDoubleArithmetic(double left, double right, String operator) {
        return switch (operator) {
            case "+" -> (float) (left + right);
            case "-" -> (float) (left - right);
            case "*" -> (float) (left * right);
            case "/" -> {
                if (right == 0.0d) {
                    throw new IllegalArgumentException("除数不能为 0");
                }
                yield (float) (left / right);
            }
            case "%" -> {
                if (right == 0.0d) {
                    throw new IllegalArgumentException("取模的右操作数不能为 0");
                }
                yield (float) (left % right);
            }
            default -> throw new IllegalArgumentException("不支持的算术运算符: " + operator);
        };
    }

    /**
     * 两 int 路径（供测试或显式整型调用）。
     */
    public static int apply(int left, int right, String operator) {
        if (isBitwise(operator)) {
            return applyIntBitwise(left, right, operator);
        }
        return applyIntArithmeticPrimitive(left, right, operator);
    }
}
