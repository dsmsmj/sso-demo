package com.example.iam.init;

import com.example.iam.entity.OaUser;
import com.example.iam.entity.User;
import com.example.iam.repository.OaUserRepository;
import com.example.iam.repository.UserRepository;
import com.example.iam.sync.OrgSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 应用启动时自动初始化演示数据。
 *
 * CommandLineRunner：Spring Boot 提供的钩子，run() 在所有 Bean 初始化完成后执行。
 * TS 类比：相当于 main() 里 await app.listen() 之后紧跟的初始化逻辑。
 *
 * 初始化顺序：
 *   1. 写入 OA 用户（oa_users 表）
 *   2. 触发一次 IAM 组织同步（users 表）
 * 保证两个系统的用户数据在应用启动后就都存在，不用手动 curl 触发。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final OaUserRepository oaUserRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrgSyncService orgSyncService;

    @Override
    public void run(String... args) {
        seedOaUsers();
        syncIamUsers();
        // 注意：seedAppUsers 必须放在 syncIamUsers 之后，
        // 因为同步可能创建 admin（u004），我们要给它补上密码 + ADMIN 角色
        seedAppUsers();
    }

    private void seedOaUsers() {
        if (oaUserRepository.count() > 0) return;

        String pw = passwordEncoder.encode("pass123");
        List<OaUser> users = List.of(
            OaUser.builder().username("zhangsan").passwordHash(pw)
                .displayName("张三").email("zhangsan@example.com").department("技术部").externalId("u001").build(),
            OaUser.builder().username("lisi").passwordHash(pw)
                .displayName("李四").email("lisi@example.com").department("产品部").externalId("u002").build(),
            OaUser.builder().username("wangwu").passwordHash(pw)
                .displayName("王五").email("wangwu@example.com").department("人事部").externalId("u003").build()
        );
        oaUserRepository.saveAll(users);
        log.info("OA 用户初始化完成：zhangsan / lisi / wangwu，密码统一为 pass123");
    }

    private void syncIamUsers() {
        OrgSyncService.SyncResult result = orgSyncService.syncAll();
        log.info("IAM 用户初始同步完成: {}", result);
    }

    /**
     * 给 IAM 用户表里的部分账号补上密码 + 角色，使其能走 /api/auth/login 登录。
     * 三个默认账号：
     *   admin / admin123  → ROLE_ADMIN, ROLE_USER
     *   user  / user123   → ROLE_USER  （username = zhangsan，演示用）
     *   demo  / demo123   → ROLE_USER  （独立创建，不在组织同步数据中）
     */
    private void seedAppUsers() {
        Map<String, AppUserSeed> seeds = Map.of(
            "admin", new AppUserSeed("admin123", "ROLE_ADMIN,ROLE_USER", null),
            "zhangsan", new AppUserSeed("user123", "ROLE_USER", null),
            "demo", new AppUserSeed("demo123", "ROLE_USER", "demo@example.com")
        );

        seeds.forEach((username, seed) -> {
            User u = userRepository.findByUsername(username).orElseGet(() ->
                User.builder()
                    .externalId("local-" + username)
                    .username(username)
                    .displayName(username)
                    .email(seed.email)
                    .status(User.UserStatus.ACTIVE)
                    .build()
            );
            // 已经有密码就别覆盖（避免每次重启把用户改过的密码冲掉，虽然 H2 内存库里其实无所谓）
            if (u.getPassword() == null) {
                u.setPassword(passwordEncoder.encode(seed.password));
            }
            u.setRoles(seed.roles);
            userRepository.save(u);
        });
        log.info("IAM 默认登录账号就绪：admin/admin123、zhangsan/user123、demo/demo123");
    }

    private record AppUserSeed(String password, String roles, String email) {}
}
