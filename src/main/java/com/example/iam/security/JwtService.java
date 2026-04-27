package com.example.iam.security;

import com.example.iam.dto.TokenResponse;
import com.example.iam.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * 颁发自签 JWT（HS256）。
 *
 * Claims 设计参照 RFC 7519：
 *   iss   "iam-starter"  签发者
 *   sub   username       主体（标准 OAuth2 把 user_id/username 放这里）
 *   iat   now            签发时间
 *   exp   now+ttl        过期
 *   roles ["ROLE_..."]   自定义 claim，资源服务器用它判断权限
 *   uid / email / name   便于客户端展示，非鉴权关键
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Value("${app.jwt.expiration-minutes}")
    private long expirationMinutes;

    public TokenResponse issueToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        List<String> roles = parseRoles(user.getRoles());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("email", user.getEmail() == null ? "" : user.getEmail())
                .claim("name", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName())
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new TokenResponse(
                token,
                "Bearer",
                expirationMinutes * 60,
                String.join(" ", roles),
                user.getUsername()
        );
    }

    private List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) return List.of("ROLE_USER");
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
