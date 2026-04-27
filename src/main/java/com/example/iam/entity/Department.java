package com.example.iam.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 部门实体 —— 组织架构的核心对象。
 *
 * 自关联(parentExternalId)形成树形结构,
 * 绝大多数组织架构系统都是这个模型。
 */
@Entity
@Table(name = "departments", indexes = {
    @Index(name = "idx_dept_external_id", columnList = "externalId", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String externalId;

    @Column(nullable = false, length = 128)
    private String name;

    /**
     * 父部门外部 ID;根部门此字段为 null。
     * 同步时先建父,再建子,保证关系能建立起来。
     */
    @Column(length = 128)
    private String parentExternalId;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
