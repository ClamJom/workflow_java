package com.example.demoworkflow.vo;

import lombok.*;
import jakarta.validation.constraints.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConfigVO {
    public long id;

    @NotBlank(message="配置名称不能为空")
    @Size(min=1, max=255, message="配置长度必须在1-255内")
    public String name;

    public String des;

    @NotBlank(message="配置类型不允许为空")
    public String type;

    public String value;

    public int min;

    public int max;

    @Min(value=1, message="不允许将浮点除数设置为小于1")
    // 如果该配置为数字且其有浮点区间限制，将该值作为除数得到相应的浮点值
    public int k;

    @Min(value=0, message="精度不能小于0")
    public int quantize;

    public boolean required;

    public long parent;
}
