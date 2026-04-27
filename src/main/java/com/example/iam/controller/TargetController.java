package com.example.iam.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

/**
 * 目标系统（资源服务器）入口，演示持有凭证流程的资源服务器端。
 *
 * 流程：
 *   GET /target/enter?token={access_token}
 *     → 调 Keycloak GET /userinfo 验证 token
 *     → 验证通过：将用户信息存入 session，302 → /target.html
 *     → 验证失败：回 /sso/launch 重新授权
 */
@Slf4j
@Controller
public class TargetController {

    private static final String SESSION_KEY = "target_user";

    @Autowired(required = false)
    private ClientRegistrationRepository clientRegistrationRepository;

    @GetMapping("/target/enter")
    public void enter(
            @RequestParam String token,
            HttpSession session,
            HttpServletResponse response) throws IOException {

        Map<String, Object> userInfo = callUserInfo(token);
        if (userInfo != null) {
            session.setAttribute(SESSION_KEY, userInfo);
            log.debug("Target system session established for sub={}", userInfo.get("sub"));
            response.sendRedirect("/target.html");
        } else {
            log.warn("GET /userinfo failed or Keycloak not configured, redirecting to SSO");
            response.sendRedirect("/sso/launch");
        }
    }

    /**
     * 目标系统自己的"当前用户"接口，凭证来自 target_user session（非 Spring Security session）。
     * 展示目标系统独立建立会话的事实。
     */
    @GetMapping("/api/target/me")
    @ResponseBody
    public ResponseEntity<?> me(HttpSession session) {
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) session.getAttribute(SESSION_KEY);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未通过目标系统验证，请从 OA 门户进入"));
        }
        return ResponseEntity.ok(user);
    }

    /**
     * 用 access_token 调 Keycloak /userinfo，验证 token 合法性并拿到用户信息。
     * 对应流程图：目标系统 → GET /userinfo（验证 token）→ 授权服务器返回用户信息。
     */
    private Map<String, Object> callUserInfo(String token) {
        if (clientRegistrationRepository == null) {
            return null;
        }
        ClientRegistration reg = clientRegistrationRepository.findByRegistrationId("keycloak");
        if (reg == null) {
            return null;
        }
        String userinfoUri = reg.getProviderDetails().getUserInfoEndpoint().getUri();
        if (userinfoUri == null || userinfoUri.isBlank()) {
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        try {
            ResponseEntity<Map<String, Object>> res = new RestTemplate().exchange(
                    userinfoUri,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );
            if (res.getStatusCode().is2xxSuccessful()) {
                return res.getBody();
            }
        } catch (Exception e) {
            log.warn("GET /userinfo error: {}", e.getMessage());
        }
        return null;
    }
}
