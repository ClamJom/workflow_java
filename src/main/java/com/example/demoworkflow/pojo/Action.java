package com.example.demoworkflow.pojo;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 这个实体不为前端服务，因此无需VO
 */
@Entity
@Data
public class Action {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /**
     * 指向一个配置（空配置，类型为String），将该配置作为父配置以搜索所有的子配置
     */
    @Column(name="option_id", columnDefinition = "BIGINT DEFAULT 0")
    private long optionId;

    /**
     * 这个字段是为后续自定义节点类型预留的，允许用户基于已有节点新增自定义配置以更灵活的配置节点
     */
    @Column(name="node_type", columnDefinition = "INT DEFAULT 0")
    private int nodeType;
}
