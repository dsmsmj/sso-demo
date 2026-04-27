# IAM Starter — Spring Boot 组织同步 + SSO 骨架

给 TS 背景同学的 Spring Boot 入门项目。演示组织架构同步 + OIDC/OAuth2 单点登录两个核心场景。

> 📚 **学习文档**：看 [`docs/`](./docs/README.md) —— 按 IoC → Web → JPA → Security → 工程实践分 5 章，配合本项目源码食用。

## 目录结构解读

```
src/main/java/com/example/iam/
├── IamApplication.java          应用入口(相当于 index.ts)
├── config/
│   └── SecurityConfig.java      Spring Security 配置:URL 授权规则 + OAuth2/JWT
├── controller/
│   └── UserController.java      REST API(相当于 Express 的 router)
├── dto/                         数据传输对象(防腐层)
│   ├── ExternalUser.java
│   └── ExternalDepartment.java
├── entity/                      数据库实体(ORM 映射)
│   ├── User.java
│   └── Department.java
├── repository/                  数据访问层(Spring Data JPA 自动生成实现)
│   ├── UserRepository.java
│   └── DepartmentRepository.java
└── sync/
    ├── OrgSource.java           数据源接口(LDAP/钉钉等各家实现它)
    ├── MockOrgSource.java       演示用假数据源
    ├── OrgSyncService.java      ★ 核心同步逻辑
    └── OrgSyncScheduler.java    定时任务
```

## TS → Java 对照

| TS/Node                          | Java/Spring                          |
|----------------------------------|--------------------------------------|
| `package.json`                   | `pom.xml`                            |
| `npm install`                    | `mvn install`                        |
| `npm run start`                  | `mvn spring-boot:run`                |
| Express Router                   | `@RestController`                    |
| TypeORM Entity                   | `@Entity`                            |
| TypeORM Repository               | `extends JpaRepository`              |
| dotenv `.env`                    | `application.yml`                    |
| `new UserService(userRepo)`      | `@Service` + 构造器注入(自动)       |
| `passport.js`                    | `Spring Security`                    |
| `node-cron`                      | `@Scheduled`                         |

## 运行方式

```bash
# 需要 Java 17+
mvn spring-boot:run

# 手动触发一次同步
curl -X POST http://localhost:8000/api/admin/sync

# 查看同步后的用户
curl http://localhost:8000/api/users

# H2 数据库控制台(浏览器访问)
http://localhost:8000/h2-console
```

## 学习路线(按此顺序读代码)

1. **`IamApplication.java`** — 理解 `@SpringBootApplication` 做了什么
2. **`User.java` / `Department.java`** — 看 JPA 注解怎么把类变成数据库表
3. **`UserRepository.java`** — 感受"只写接口不写实现"的震撼
4. **`MockOrgSource.java` → `OrgSyncService.java`** — 看面向接口编程 + 依赖注入
5. **`OrgSyncScheduler.java`** — 定时任务怎么写
6. **`UserController.java`** — REST API 写法,对照 Express 理解
7. **`SecurityConfig.java`** — 最难的部分,SSO 的核心,建议配合官方文档读

## 下一步扩展方向

### 把 MockOrgSource 换成真实数据源

- **LDAP / Active Directory**:注入 `LdapTemplate`,用 `search()` 方法查用户和组
- **钉钉**:引入官方 SDK `com.aliyun:dingtalk`,调用"获取部门列表""获取部门用户"等 API
- **企业微信**:类似钉钉
- **SCIM**:写一个 HTTP 客户端调用 SCIM 端点

### SSO 深化

- **当客户端**(接入别人的 IdP):已经配好 `oauth2Login`,改 `application.yml` 里的 `client-id` 和 `client-secret` 就能跑
- **当服务端**(给别的系统提供 SSO):引入 `spring-boot-starter-oauth2-authorization-server`,自己做 IdP
- **SAML 协议**:引入 `spring-security-saml2-service-provider`,对接老派企业 SSO
- **JWT 自定义**:自签 Token、自定义 claims、支持 refresh token

### 工程化

- 把 H2 换成 PostgreSQL / MySQL
- 用 Flyway 管理数据库 schema 迁移
- 加 Redis 做缓存 + 分布式锁(集群部署必需)
- 接入 Prometheus + Grafana 监控
- 用 Testcontainers 写集成测试


🎉 全部跑通了！
整个流程成功了：浏览器访问 → 跳转 Keycloak 登录 → 登录成功 → 跳回 Spring Boot → 返回数据。SSO 已经正常工作。
接下来你可以试试：

http://localhost:8000/api/me — 看当前登录用户信息
http://localhost:8000/api/public/health — 公开接口无需登录
http://localhost:8000/h2-console — 看数据库里同步进来的组织架构数据