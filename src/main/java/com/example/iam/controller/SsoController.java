package com.example.iam.controller;

import com.example.iam.sso.TokenRefreshService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.time.Instant;

/**
 * OA → 目标系统 单点登录入口（持有凭证流程）。
 *
 * 已有 OIDC session：
 *   1. 从 OAuth2AuthorizedClientService 取出 access_token
 *   2. 若已过期：POST refresh_token → /token 换新 token（无感续期）
 *   3. 302 → /target/enter?token=xxx（携带 token 跳转目标系统）
 *
 * 无 OIDC session（未持有凭证流程）：
 *   → 发起 OAuth2 授权码流程，登录成功后回到此接口
 */
@Slf4j
@Controller
public class SsoController {

    @Autowired(required = false)
    private OAuth2AuthorizedClientService authorizedClientService;

    @Autowired(required = false)
    private TokenRefreshService tokenRefreshService;

    @GetMapping("/sso/launch")
    public void launch(
            @AuthenticationPrincipal OidcUser principal,
            HttpServletResponse response) throws IOException {

        if (authorizedClientService == null) {
            // Keycloak 未配置，跳到 dashboard 显示提示
            response.sendRedirect("/dashboard.html");
            return;
        }
        if (principal == null) {
            response.sendRedirect("/oauth2/authorization/keycloak");
            return;
        }

        OAuth2AuthorizedClient client =
                authorizedClientService.loadAuthorizedClient("keycloak", principal.getName());

        if (client == null) {
            log.warn("No authorized client found for {}, re-initiating OAuth2", principal.getName());
            response.sendRedirect("/oauth2/authorization/keycloak");
            return;
        }

        String accessToken;
        if (isExpired(client.getAccessToken())) {
            log.debug("access_token expired for {}, attempting silent refresh", principal.getName());
            accessToken = tokenRefreshService != null ? tokenRefreshService.refresh(client) : null;
            if (accessToken == null) {
                log.warn("Refresh failed for {}, re-initiating OAuth2", principal.getName());
                response.sendRedirect("/oauth2/authorization/keycloak");
                return;
            }
        } else {
            accessToken = client.getAccessToken().getTokenValue();
        }

        // 携带 access_token 跳转目标系统，由目标系统调 /userinfo 验证身份
        response.sendRedirect("/target/enter?token=" + accessToken);
    }

    private boolean isExpired(OAuth2AccessToken token) {
        return token.getExpiresAt() != null && Instant.now().isAfter(token.getExpiresAt());
    }
}
