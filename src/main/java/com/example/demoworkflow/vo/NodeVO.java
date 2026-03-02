package com.example.demoworkflow.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class NodeVO {
    @NotBlank
    @NotNull
    @Size(max=255, min=1, message="节点ID不允许为空")
    public String id;

    @Min(value=0, message="节点类型不允许为空")
    public int type;

    public List<ConfigVO> configs;
}
