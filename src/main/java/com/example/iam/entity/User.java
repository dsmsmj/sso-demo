package com.example.iam.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

/**
 * 用户实体(对应数据库表 users)。
 *
 * TS 类比:
 *   interface User {
 *     id: number;
 *     username: string;
 *     email: string;
 *     ...
 *   }
 * 但这里不只是类型,它同时描述了数据库表结构 —— 这就是 ORM。
 *
 * Lombok 注解说明(省掉大量样板代码):
 *   @Data            自动生成 getter/setter/toString/equals/hashCode
 *   @NoArgsConstructor   无参构造器(JPA 必须)
 *   @AllArgsConstructor  全参构造器
 *   @Builder             生成构建器模式的 API,比如 User.builder().username("x").build()
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_external_id", columnList = "externalId"),
    @Index(name = "idx_username", columnList = "username", unique = true),
    @Index(name = "idx_keycloak_sub", columnList = "keycloakSub", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 外部系统的用户 ID(比如 LDAP 的 uid、钉钉的 userid)。
     * 同步时用这个字段判断"是同一个人还是新用户"。
     */
    @Column(nullable = false, length = 128)
    private String externalId;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(length = 128)
    private String displayName;

    @Column(length = 128)
    private String email;

    /** Keycloak 的 sub（subject）claim，首次 SSO 登录时自动绑定，之后作为稳定查找键。 */
    @Column(length = 64, unique = true)
    private String keycloakSub;

    @Column(length = 32)
    private String phone;

    /**
     * BCrypt 密码哈希。组织同步过来的用户为 null（只能走 SSO）；
     * 通过 IAM 自己的 /api/auth/login 登录的用户必须有这个值。
     */
    @Column(length = 200)
    private String password;

    /**
     * 角色列表，逗号分隔。例如 "ROLE_ADMIN,ROLE_USER"。
     * 简化处理；真实项目用多对多关联表。
     */
    @Column(length = 200)
    @Builder.Default
    private String roles = "ROLE_USER";

    /**
     * 所属部门的外部 ID(简化处理,真实场景可能是多对多 + 关联表)。
     */
    @Column(length = 128)
    private String departmentExternalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // JPA 生命周期回调:入库前自动设置时间戳
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

    public enum UserStatus {
        ACTIVE, DISABLED, DELETED
    }
}
