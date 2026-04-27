package com.example.iam.controller;

import com.example.iam.dto.OaUserDto;
import com.example.iam.entity.OaUser;
import com.example.iam.repository.OaUserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * OA 平台独立的认证接口。
 *
 * 注意：这里不走 Spring Security 的认证流程，而是手动查库 + 写 HttpSession。
 * 这是为了演示"OA 自己管用户，和 Keycloak 是两套独立系统"这一架构事实。
 *
 * 会话键 OA_USER 和 Spring Security 写入的 SPRING_SECURITY_CONTEXT 完全独立，
 * OA 登出不会影响 OIDC session，反之亦然。
 */
@RestController
@RequestMapping("/api/oa")
@RequiredArgsConstructor
public class OaController {

    public static final String SESSION_KEY = "OA_USER";

    private final OaUserRepository oaUserRepository;
    private final PasswordEncoder passwordEncoder;

    record LoginRequest(String username, String password) {}

    /**
     * OA 表单登录：验证账号密码，成功则写入 session。
     * 不依赖 Keycloak，OA 用自己的用户库。
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpSession session) {
        Optional<OaUser> found = oaUserRepository.findByUsername(req.username());
        if (found.isEmpty() || !passwordEncoder.matches(req.password(), found.get().getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        OaUserDto dto = OaUserDto.from(found.get());
        session.setAttribute(SESSION_KEY, dto);
        return ResponseEntity.ok(dto);
    }

    /**
     * 获取当前 OA session 用户。
     * 未登录 OA 返回 401（和 Spring Security 的 /api/me 完全独立）。
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        OaUserDto user = (OaUserDto) session.getAttribute(SESSION_KEY);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }
        return ResponseEntity.ok(user);
    }

    /** OA 登出：只清除 OA session，不影响 Keycloak OIDC session。 */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 返回所有 OA 用户（演示用，管理员功能）。 */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        return ResponseEntity.ok(
            oaUserRepository.findAll().stream().map(OaUserDto::from).toList()
        );
    }
}
