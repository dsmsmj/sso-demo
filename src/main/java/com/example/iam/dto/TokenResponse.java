package com.example.iam.dto;

/**
 * OAuth2 Token Endpoint 响应体（RFC 6749 §5.1）。
 *
 * 字段含义：
 *   access_token   要求方下次请求带上 Authorization: Bearer <token>
 *   token_type     固定 "Bearer"
 *   expires_in     access_token 有效期（秒）
 *   scope          授权范围（这里简化只回显 roles）
 *   username       便于前端展示，非标准字段
 */
public record TokenResponse(
        String access_token,
        String token_type,
        long expires_in,
        String scope,
        String username
) {}
