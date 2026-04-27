package com.example.iam.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 从外部系统(LDAP/钉钉/企业微信)拉下来的用户数据的中间表示。
 *
 * 为什么要有这个 DTO,不直接用 User 实体?
 *   - 实体是"我们系统里的样子",DTO 是"外部系统给我们的样子"
 *   - 数据源可能有好几个(LDAP + 钉钉),先统一成这个结构,再转成 User
 *   - 外部格式变了,只改 DTO + 转换逻辑,不影响数据库
 *
 * 这叫"防腐层"(Anti-Corruption Layer),DDD 的经典模式。
 */
@Data
@Builder
public class ExternalUser {
    private String externalId;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String departmentExternalId;
    private boolean active;
}
