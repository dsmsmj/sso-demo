package com.example.iam.controller;

import com.example.iam.dto.TokenResponse;
import com.example.iam.entity.User;
import com.example.iam.repository.UserRepository;
import com.example.iam.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 标准的账号密码 → JWT 登录端点。
 *
 * 流程：
 *   1. 客户端 POST {username, password} 到 /api/auth/login
 *   2. 服务端校验 → 匹配成功颁发 JWT（HS256，TTL 在 application.yml 配）
 *   3. 客户端后续请求带 Authorization: Bearer <token>
 *   4. SecurityConfig 里的 Resource Server 会自动验签 + 把 roles claim 转成 Authority
 *
 * 注：注册接口暂不实现，演示账号见 DataInitializer。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        if (user.getPassword() == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已被停用");
        }
        return jwtService.issueToken(user);
    }

    /**
     * 用 Bearer Token 拿当前登录用户信息。
     * 这是 OAuth2 Resource Server 标准用法 —— @AuthenticationPrincipal Jwt 注入解析后的 token。
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "username", jwt.getSubject(),
                "uid", jwt.getClaimAsString("uid"),
                "email", jwt.getClaimAsString("email"),
                "name", jwt.getClaimAsString("name"),
                "roles", jwt.getClaim("roles"),
                "issuer", jwt.getClaimAsString("iss"),
                "expiresAt", jwt.getExpiresAt()
        ));
    }
}
