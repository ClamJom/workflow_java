package com.example.demoworkflow.vo;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class NodeVO {
    @NotBlank
    @NotNull
    @Size(max=255, min=1, message="节点ID不允许为空")
    public String id;

    @Size(max=255, message="节点名称不允许超过255个字符")
    public String name;

    @Min(value=0, message="节点类型不允许为空")
    public int type;

    public List<ConfigVO> configs;

    public String parent;

    /** 画布节点坐标（与 Vue Flow position 一致），持久化后加载可还原布局 */
    public Position position;

    /** 前端画布节点样式（如循环容器宽高），可选 */
    public Map<String, Object> style;

    @Data
    public static class Position {
        public Double x;
        public Double y;
    }
}
