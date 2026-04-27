package com.example.iam.config;

import com.example.iam.controller.OaController;
import com.example.iam.dto.OaUserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * 在发起 Keycloak 授权请求时注入 login_hint。
 * Keycloak 收到 login_hint 后会预填邮箱，引导用户登录对应账号。
 * 这样 OA 登录的 lisi 跳 IAM 时，Keycloak 登录框默认指向 lisi@example.com。
 *
 * 仅当 Keycloak（或其他 OAuth2 Client 注册）存在时才注册此 Bean，
 * 避免在仅启用 JWT 登录的场景下因找不到 ClientRegistrationRepository 而启动失败。
 */
@Component
@ConditionalOnBean(ClientRegistrationRepository.class)
public class OaLoginHintResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public OaLoginHintResolver(ClientRegistrationRepository repo) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return addLoginHint(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return addLoginHint(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest addLoginHint(OAuth2AuthorizationRequest base, HttpServletRequest request) {
        if (base == null) return null;

        HttpSession session = request.getSession(false);
        if (session == null) return base;

        OaUserDto oaUser = (OaUserDto) session.getAttribute(OaController.SESSION_KEY);
        if (oaUser == null || oaUser.getEmail() == null) return base;

        return OAuth2AuthorizationRequest.from(base)
                .additionalParameters(params -> params.put("login_hint", oaUser.getEmail()))
                .build();
    }
}
