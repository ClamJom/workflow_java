package com.example.demoworkflow.pojo;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Config中的值实际上在本Demo中不允许更改（即Value即为默认值），
 * 流程的具体配置应当保存至文件中
 */
@Entity
@Data
public class Config {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name="name")
    private String name;

    @Column(name="des")
    // 配置描述
    private String des;

    @Column(name="c_type")
    private String type;

    @Column(name="value")
    private String value;

    @Column(name="options")
    // 如果配置项为下拉框，则在此提供下拉选项
    private String options;

    @Column(name="c_min", columnDefinition = "INT DEFAULT 0")
    private int min;

    @Column(name="c_max", columnDefinition = "INT DEFAULT 0")
    private int max;

    @Column(name="k", columnDefinition = "INT DEFAULT 1")
    // 如果该配置为数字且其有浮点区间限制，将该值作为除数得到相应的浮点值
    private int k;

    @Column(name="quantize", columnDefinition = "INT DEFAULT 0")
    private int quantize;

    @Column(name="required", columnDefinition = "SMALLINT DEFAULT 0")
    private boolean required;

    // 子配置的配置类型，当这个值为容器类型配置时，需要有一个配置为当前配置的子配置
    @Column(name="item_type", columnDefinition = "VARCHAR(255) DEFAULT 'String'")
    private String itemType;

    @Column(name="parent", columnDefinition = "INT DEFAULT 0")
    private long parent;
}
