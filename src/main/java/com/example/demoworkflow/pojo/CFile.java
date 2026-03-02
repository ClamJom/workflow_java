package com.example.demoworkflow.pojo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 为了防止与NIO冲突，此处命名为CFile（CustomFile）
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    // 文件名称，显示用，并非实际存储的文件名称
    @Column(name="name")
    private String name;

    // UUID确定文件实际的文件名称
    @Column(name="uuid")
    private String uuid;

    // 所属的工作空间，用于区分文件夹以及对应的文件系统管理对象
    @Column(name="workspace")
    private String workspace;

    // 文件的SHA-256哈希，用于验证文件完整性，暂时不用
    // @Column(name="signature")
    // private String signature;

    // 创建时间
    @Column(name="created")
    private Date created;
}
