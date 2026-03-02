package com.example.demoworkflow.utils.workflow.dto;

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
     * 这个条件满足时指向的下一个分支（或多个分支）
     */
    public List<String> nextNodes;
}
