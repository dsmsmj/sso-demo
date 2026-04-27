package com.example.iam.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * OA 平台的本地用户表。
 *
 * OA 是组织数据源，IAM 从这里同步用户。externalId 是两张表的稳定关联键。
 * 认证方式：OA 自己做表单登录，不依赖 Keycloak。
 */
@Entity
@Table(name = "oa_users", indexes = {
    @Index(name = "idx_oa_username", columnList = "username", unique = true),
    @Index(name = "idx_oa_email", columnList = "email")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OaUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 128)
    private String displayName;

    @Column(length = 128)
    private String email;

    /**
     * 对应 IAM users.externalId，由 OA 作为组织数据源时分配。
     * 通过此字段可直接定位 IAM 档案，无需依赖 email 或 Keycloak sub。
     */
    @Column(length = 128, unique = true)
    private String externalId;

    @Column(length = 64)
    private String department;
}
