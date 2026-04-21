package com.example.demoworkflow.utils.workflow.dto;

import com.example.demoworkflow.utils.workflow.misc.NumberComparator;
import lombok.Data;

import java.util.List;

/**
 * 条件配置，提供双目运算，专用于条件节点，配置类型为`Condition`
 */
@Data
public class ConditionConfig {
    /**
     * 运算符，有以下可选项：
     * 1. `eq`或`==`，等于；
     * 2. `ne`或`!=`，不等于；
     * 3. `lt`或`<`，小于；
     * 4. `gt`或`>`，大于；
     * 5. `le`或`<=`，小于等于；
     * 6. `ge`或`>=`，大于等于
     */
    public String operator;

    /**
     * 左值
     */
    public String a;

    /**
     * 右值
     */
    public String b;

    /**
     * 这个条件满足时指向的下一个分支（或多个分支）。
     * 该属性是为条件节点准备的。
     */
    public List<String> nextNodes;

    private boolean eq(Object l, Object r){
        if(l instanceof String || r instanceof String) return l.equals(r);
        NumberComparator nc = new NumberComparator();
        try{
            return nc.eq(l, r);
        }catch(Exception e){
            return false;
        }
    }

    private boolean ne(Object l, Object r){
        return !eq(l, r);
    }

    private boolean lt(Object l, Object r){
        if(l instanceof String || r instanceof String) return false;
        NumberComparator nc = new NumberComparator();
        try{
            return nc.lt(l, r);
        }catch(Exception e){
            return false;
        }
    }

    private boolean gt(Object l, Object r){
        if(l instanceof String || r instanceof String) return false;
        NumberComparator nc = new NumberComparator();
        try{
            return nc.gt(l, r);
        }catch(Exception e){
            return false;
        }
    }

    private boolean le(Object l, Object r){
        if(l instanceof String || r instanceof String) return false;
        NumberComparator nc = new NumberComparator();
        try{
            return nc.le(l, r);
        }catch(Exception e){
            return false;
        }
    }

    private boolean ge(Object l, Object r){
        if(l instanceof String || r instanceof String) return false;
        NumberComparator nc = new NumberComparator();
        try{
            return nc.ge(l, r);
        }catch(Exception e){
            return false;
        }
    }

    public boolean compareCore(Object l, Object r){
        return switch (operator) {
            case "==", "eq" -> eq(l, r);
            case "!=", "ne" -> ne(l, r);
            case "<", "lt" -> lt(l, r);
            case ">", "gt" -> gt(l, r);
            case "<=", "le" -> le(l, r);
            case ">=", "ge" -> ge(l, r);
            default -> false;
        };
    }
}
