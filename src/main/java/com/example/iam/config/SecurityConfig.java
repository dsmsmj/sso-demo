package com.example.iam.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 核心配置 —— 把"标准 OAuth2"的所有片段拼起来：
 *
 * 1) 浏览器 SSO（OAuth2 Client）
 *    /oauth2/authorization/keycloak → 重定向到 Keycloak → 回调 → session 建立
 *    只有当 application.yml 里配了 client-id 时才启用，否则跳过避免启动失败。
 *
 * 2) API 认证（OAuth2 Resource Server + 自签 JWT）
 *    POST /api/auth/login {username, password} → 颁发 HS256 JWT
 *    后续请求带 Authorization: Bearer <token> → 自动验签 + 注入 Authentication
 *    JWT 里的 "roles" claim 直接转成 Spring Security 的 GrantedAuthority。
 *
 * 3) URL 授权
 *    /api/auth/login        permitAll （没登录的人才需要登录）
 *    /api/admin/**          需要 ROLE_ADMIN
 *    /api/**                需要登录（任意方式）
 */
@Configuration
public class SecurityConfig {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            ObjectProvider<OaLoginHintResolver> oaLoginHintResolverProvider) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/index.html", "/oa.html", "/dashboard.html",
                    "/sso-flow.html", "/target.html",
                    "/css/**", "/js/**", "/favicon.ico", "/error",
                    "/sso/launch", "/target/enter", "/api/target/me"
                ).permitAll()
                // OA 自管认证
                .requestMatchers("/api/oa/**").permitAll()
                // 公共 API + 健康检查 + H2 控制台
                .requestMatchers("/api/public/**", "/h2-console/**").permitAll()
                // 自家账号登录入口必须放行
                .requestMatchers("/api/auth/login").permitAll()
                // /api/me 自己处理 401（兼容 SSO session 与 JWT 两种情况）
                .requestMatchers("/api/me").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            .headers(headers -> headers.frameOptions(frame -> frame.disable()))

            // OAuth2 Resource Server：解析 + 校验 Bearer Token
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
            ))

            .logout(logout -> logout
                .logoutSuccessUrl("/index.html")
                .permitAll()
            )

            .sessionManagement(session -> session
                // SSO 浏览器流程要 session；纯 JWT 调用不会建 session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            );

        // 仅当 Keycloak 客户端注册存在时启用 OAuth2 Login。
        // OaLoginHintResolver 用 @ConditionalOnBean 注册，组件扫描早于自动配置，
        // 可能拿不到，因此单独判断，不阻塞 oauth2Login 的核心注册。
        if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
            http.oauth2Login(oauth -> {
                oauth.defaultSuccessUrl("/sso/launch", true);
                OaLoginHintResolver hintResolver = oaLoginHintResolverProvider.getIfAvailable();
                if (hintResolver != null) {
                    oauth.authorizationEndpoint(endpoint ->
                        endpoint.authorizationRequestResolver(hintResolver));
                }
            });
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /* ----------- 自签 JWT 的 Encoder / Decoder（HS256） ----------- */

    private SecretKey hmacKey() {
        // HS256 要求至少 256 bit（32 字节）密钥；secret 不够长会被 Nimbus 拒绝
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(hmacKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * 把 JWT 的 "roles" claim 转成 Spring Security 的 GrantedAuthority。
     * 我们的 token 里直接存全名（ROLE_ADMIN），所以 prefix 设空字符串避免被加成 ROLE_ROLE_ADMIN。
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        // sub claim 作为 principal name
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
