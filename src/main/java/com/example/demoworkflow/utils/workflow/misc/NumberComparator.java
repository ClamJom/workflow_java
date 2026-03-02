package com.example.demoworkflow.utils.workflow.misc;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 由Deepseek生成，懒得写了
 * 比较两个对象的大小，要求它们必须是可比较的数字类型。
 * 支持所有数字类型（包括原始类型包装类、BigInteger、BigDecimal等），
 * 并正确处理 NaN 和无穷大（遵循 Double.compare 的规则）。
 */
public class NumberComparator{

    public boolean eq(Object a, Object b) {
        return compare(a, b) == 0;
    }

    public boolean ne(Object a, Object b) {
        return compare(a, b) != 0;
    }

    public boolean lt(Object a, Object b) {
        return compare(a, b) < 0;
    }

    public boolean gt(Object a, Object b) {
        return compare(a, b) > 0;
    }

    public boolean le(Object a, Object b) {
        return compare(a, b) <= 0;
    }

    public boolean ge(Object a, Object b) {
        return compare(a, b) >= 0;
    }

    /**
     * 核心比较方法，返回负值、零或正值。
     */
    private int compare(Object a, Object b) {
        // 处理 null
        if (a == null || b == null) {
            throw new NullPointerException("比较对象不能为 null");
        }
        // 检查是否为数字类型
        if (!isNumber(a) || !isNumber(b)) {
            throw new IllegalArgumentException("对象必须是数字类型");
        }
        Num numA = toNum(a);
        Num numB = toNum(b);
        return numA.compareTo(numB);
    }

    /** 判断对象是否为数字类型 */
    private boolean isNumber(Object obj) {
        return obj instanceof Number || obj instanceof Character;
    }

    /** 将对象转换为内部 Num 类型，便于统一比较 */
    private Num toNum(Object obj) {
        if (obj instanceof Double || obj instanceof Float) {
            double val = ((Number) obj).doubleValue();
            if (Double.isNaN(val)) {
                return Num.NAN;
            } else if (val == Double.POSITIVE_INFINITY) {
                return Num.POSITIVE_INFINITY;
            } else if (val == Double.NEGATIVE_INFINITY) {
                return Num.NEGATIVE_INFINITY;
            } else {
                return Num.normal(BigDecimal.valueOf(val));
            }
        } else if (obj instanceof Number) {
            Number num = (Number) obj;
            // 对于精确整数类型，使用 long 构造 BigDecimal 避免精度损失
            if (obj instanceof Byte || obj instanceof Short || obj instanceof Integer || obj instanceof Long) {
                return Num.normal(BigDecimal.valueOf(num.longValue()));
            } else if (obj instanceof BigInteger) {
                return Num.normal(new BigDecimal((BigInteger) obj));
            } else if (obj instanceof BigDecimal) {
                return Num.normal((BigDecimal) obj);
            } else {
                // 其他 Number 子类（如 AtomicInteger）通过 doubleValue 转换
                return Num.normal(BigDecimal.valueOf(num.doubleValue()));
            }
        } else if (obj instanceof Character) {
            char ch = (Character) obj;
            return Num.normal(BigDecimal.valueOf(ch));
        } else {
            throw new IllegalArgumentException("不支持的类型: " + obj.getClass());
        }
    }

    /**
     * 内部类，表示一个可比较的数值，支持 NaN 和无穷大。
     * 比较规则遵循 Double.compare：
     * 负无穷 < 有限数 < 正无穷 < NaN
     */
    private static final class Num implements Comparable<Num> {
        private enum Type { NEG_INFINITY, NORMAL, POS_INFINITY, NAN }
        private final Type type;
        private final BigDecimal value;  // 仅当 type == NORMAL 时有意义
        private final int sign;          // 仅当 type == INFINITY 时，1 为正，-1 为负

        private Num(Type type, BigDecimal value, int sign) {
            this.type = type;
            this.value = value;
            this.sign = sign;
        }

        static final Num NEGATIVE_INFINITY = new Num(Type.NEG_INFINITY, null, -1);
        static final Num POSITIVE_INFINITY = new Num(Type.POS_INFINITY, null, 1);
        static final Num NAN = new Num(Type.NAN, null, 0);

        static Num normal(BigDecimal val) {
            return new Num(Type.NORMAL, val, 0);
        }

        @Override
        public int compareTo(Num other) {
            if (this.type == other.type) {
                switch (this.type) {
                    case NAN:
                        return 0;  // NaN 等于自身
                    case NEG_INFINITY:
                    case POS_INFINITY:
                        // 同符号无穷大相等，否则负无穷小于正无穷
                        return Integer.compare(this.sign, other.sign);
                    case NORMAL:
                        return this.value.compareTo(other.value);
                    default:
                        throw new AssertionError();
                }
            } else {
                // 不同类型按顺序：NEG_INFINITY < NORMAL < POS_INFINITY < NAN
                return Integer.compare(this.type.ordinal(), other.type.ordinal());
            }
        }
    }
}
