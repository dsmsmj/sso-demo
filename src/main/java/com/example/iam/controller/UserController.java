package com.example.iam.controller;

import com.example.iam.entity.User;
import com.example.iam.repository.UserRepository;
import com.example.iam.sync.OrgSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller 示例。
 *
 * 注解一览:
 *   @RestController         = @Controller + @ResponseBody,方法返回值自动转 JSON
 *   @RequestMapping("/api") 类级别路径前缀
 *   @GetMapping / @PostMapping  方法级别的路由
 *   @PathVariable           URL 路径参数,类似 Express 的 req.params
 *   @RequestParam           Query 参数,类似 req.query
 *   @RequestBody            请求体,类似 req.body
 *   @AuthenticationPrincipal  拿到当前登录用户(由 Spring Security 注入)
 *
 * TS/Express 类比:
 *   app.get('/api/users', (req, res) => res.json(users))
 * 在 Spring 里写成:
 *   @GetMapping("/users") public List<User> list() { return users; }
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final OrgSyncService orgSyncService;

    /**
     * 公开接口:不用登录就能访问。
     * 对应 SecurityConfig 里 /api/public/** 的放行规则。
     */
    @GetMapping("/public/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    /**
     * 获取当前登录用户信息。
     * 这个接口在 SecurityConfig 里被 permitAll,由前端调用判断登录状态:
     *   未登录返回 401;已登录返回 200 + 用户信息。
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal OidcUser principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }
        return ResponseEntity.ok(Map.of(
            "authenticated", true,
            "name", principal.getFullName() != null ? principal.getFullName() : principal.getSubject(),
            "email", principal.getEmail() != null ? principal.getEmail() : "",
            "claims", principal.getClaims()
        ));
    }

    /**
     * 列出所有用户(需要登录)。
     */
    @GetMapping("/users")
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("用户不存在: " + id));
    }

    /**
     * 手动触发同步(管理员功能)。
     * 需要 ADMIN 角色,由 SecurityConfig 的 /api/admin/** 规则保护。
     */
    /**
     * 按 email 查 IAM 本地用户档案。
     * dashboard 登录后调用此接口，用 Keycloak token 里的 email 来匹配 IAM 档案，
     * 直观展示"email 是跨系统唯一标识"这一事实。
     */
    @GetMapping("/users/by-email")
    public ResponseEntity<?> byEmail(@RequestParam String email) {
        return userRepository.findByEmail(email)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(
                        Map.of("message", "IAM 中未找到 email=" + email + " 的用户，请先触发组织同步")));
    }

    /**
     * 按 Keycloak sub 查 IAM 档案；未绑定时用 email 兜底并自动写入 sub。
     * 首次登录：findByKeycloakSub 未命中 → 按 email 找到 → 绑定 sub → 返回用户。
     * 后续登录：直接 findByKeycloakSub 命中，email 可以改变也不影响关联。
     */
    @GetMapping("/users/profile")
    public ResponseEntity<?> profile(
            @RequestParam String sub,
            @RequestParam(required = false) String email) {

        var bySub = userRepository.findByKeycloakSub(sub);
        if (bySub.isPresent()) {
            return ResponseEntity.ok(Map.of("user", bySub.get(), "linked", false));
        }

        if (email != null && !email.isBlank()) {
            var byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User user = byEmail.get();
                user.setKeycloakSub(sub);
                userRepository.save(user);
                return ResponseEntity.ok(Map.of("user", user, "linked", true));
            }
        }

        return ResponseEntity.status(404).body(Map.of(
                "message", "IAM 中未找到匹配用户（sub=" + sub + "），请先触发组织同步"));
    }

    @PostMapping("/admin/sync")
    public OrgSyncService.SyncResult triggerSync() {
        return orgSyncService.syncAll();
    }
}
