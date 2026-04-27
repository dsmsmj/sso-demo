# 04. Spring Security / OAuth2

全项目最硬核的一章。对照 `SecurityConfig.java`。

## 心智模型：Filter 链

每个请求进来都会穿过一条 **Filter 链**：

```
HTTP Request
  ↓
[Filter 1] SecurityContextPersistenceFilter   (从 session 恢复认证信息)
  ↓
[Filter 2] CsrfFilter                          (校验 CSRF token)
  ↓
[Filter 3] OAuth2AuthorizationRequestRedirectFilter  (看是不是 /oauth2/authorization/xxx)
  ↓
[Filter 4] OAuth2LoginAuthenticationFilter    (处理 /login/oauth2/code/xxx 回调)
  ↓
[Filter 5] BearerTokenAuthenticationFilter    (拿 Authorization: Bearer 头验 JWT)
  ↓
[Filter 6] AuthorizationFilter                (检查是不是匹配 .authenticated() 等规则)
  ↓
Controller
```

**每个 Filter 只干一件事**：识别一种认证方式，或做一种校验。你在 `SecurityConfig` 里开启 `oauth2Login()` / `oauth2ResourceServer()` / `formLogin()`，就是把对应的 Filter 插到链里。

看当前项目实际加载了哪些 Filter：

```
mvn spring-boot:run -Dlogging.level.org.springframework.security=TRACE
```

启动日志里会打印完整的 Filter 链。

## SecurityFilterChain 核心配置

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/index.html", "/css/**", "/js/**").permitAll()
            .requestMatchers("/api/public/**").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .oauth2Login(o -> o.defaultSuccessUrl("/dashboard.html", false))
        .oauth2ResourceServer(o -> o.jwt(j -> {}))
        .logout(l -> l.logoutSuccessUrl("/index.html"))
        .build();
}
```

### 规则顺序至关重要

`authorizeHttpRequests` 里的规则是 **按顺序匹配，第一个命中就终止**。所以：

```java
.requestMatchers("/api/**").authenticated()
.requestMatchers("/api/public/**").permitAll()   // ❌ 永远到不了,上面已经拦截
```

正确做法：**具体规则在前，通用规则在后**：

```java
.requestMatchers("/api/public/**").permitAll()   // 先放公开
.requestMatchers("/api/**").authenticated()      // 再拦其余
```

### 常用规则 API

```java
.permitAll()                    // 不需要认证
.authenticated()                // 需要登录
.anonymous()                    // 只允许未登录访问
.denyAll()                      // 全部拒绝
.hasRole("ADMIN")               // 需要 ROLE_ADMIN（自动加 ROLE_ 前缀）
.hasAuthority("read:users")     // 需要具体权限（不加前缀）
.hasAnyRole("ADMIN", "OPS")
.access(new WebExpressionAuthorizationManager("hasRole('ADMIN') and @myService.check(request)"))
```

## 三种"登录"模式

Spring Security 可以同时开多种。本项目开了两种：

| 配置项 | 场景 | 这条链里谁在工作 |
|---|---|---|
| `formLogin()` | 经典表单登录（用户名密码） | 自己的用户表 |
| `oauth2Login()` | 浏览器 SSO（跳到 IdP 登录） | OAuth2 Client Filter |
| `oauth2ResourceServer().jwt()` | 收 `Authorization: Bearer xxx` 的 API 调用 | Resource Server Filter |

浏览器访问：走 `oauth2Login`，跳到 Keycloak，拿 cookie session。
其他系统带 JWT 调我们的 API：走 `oauth2ResourceServer`，验签，不建 session。

## OAuth2 授权码流程（本项目在做的事）

```
1. 用户访问 http://localhost:8000/dashboard.html
2. 未登录,跳到 http://localhost:8000/oauth2/authorization/keycloak
3. Spring 生成 state 参数,重定向到 Keycloak:
      https://keycloak/.../auth?client_id=xxx&redirect_uri=http://localhost:8000/login/oauth2/code/keycloak&state=...
4. Keycloak 展示登录页,用户输密码
5. Keycloak 回调: http://localhost:8000/login/oauth2/code/keycloak?code=AUTH_CODE&state=...
6. Spring Boot 用 code + client_secret 向 Keycloak 换 access_token + id_token
7. Spring 解析 id_token (OIDC JWT),创建 OidcUser,存 session
8. 重定向到原始请求或 defaultSuccessUrl
```

关键点：
- **state** 防 CSRF，Spring 自动处理
- **id_token** 包含用户身份 claims（sub/email/name 等）
- **access_token** 用于调用 IdP 或其他资源服务器
- **session 里存的是 `OAuth2AuthenticationToken`**，不是 id_token 本身

## 获取当前登录用户

三种方式：

### 1. 方法参数注入（推荐）

```java
@GetMapping("/me")
public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
    return Map.of("email", user.getEmail());
}
```

类型根据认证方式不同：
- OIDC 登录 → `OidcUser`
- OAuth2 登录（非 OIDC） → `OAuth2User`
- JWT Resource Server → `Jwt`
- 表单登录 → `UserDetails`

### 2. 从 SecurityContext 静态拿（任意位置）

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
if (auth != null && auth.isAuthenticated()) {
    Object principal = auth.getPrincipal();
}
```

缺点：测试不友好。能用方法参数就用方法参数。

### 3. ReactiveSecurityContextHolder（仅 WebFlux）

本项目不用。

## CSRF 什么时候关

**纯 REST API（无浏览器）**：可以关。
**浏览器会存 cookie 的场景**：必须开。

本项目为了演示方便关了 CSRF。真实项目：

- 前后端分离 SPA：开 CSRF + `CookieCsrfTokenRepository.withHttpOnlyFalse()`，前端把 cookie 里的 `XSRF-TOKEN` 读出来塞 `X-XSRF-TOKEN` 请求头。
- 纯 JWT API：可以关。JWT 不依赖 cookie，天然没 CSRF 风险。
- 传统表单：默认开就好。

## 多 FilterChain 并存

一个项目经常同时有：**浏览器页面 + JWT API**。用两条 FilterChain：

```java
@Bean
@Order(1)
public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**")                         // 这条链只管 /api/**
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> {}));
    return http.build();
}

@Bean
@Order(2)
public SecurityFilterChain webChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a -> a
            .requestMatchers("/login").permitAll()
            .anyRequest().authenticated())
        .formLogin(Customizer.withDefaults());
    return http.build();
}
```

`@Order` 数字越小优先级越高。第一个 `securityMatcher` 匹配成功就走那条链，不会再试下一条。

## 自签 JWT（做服务端）

本项目目前是接入别人的 IdP。如果想自己签 JWT 给前端用：

1. 加依赖 `spring-boot-starter-oauth2-authorization-server`
2. 配 `OAuth2AuthorizationServerConfiguration`
3. 提供 `JWKSource`（RSA 公私钥对）
4. 客户端拿 `/oauth2/token` 换 token

这块比较大，留到实际要做的时候再学。

## 排查问题的标准动作

1. **开 TRACE 日志**：`logging.level.org.springframework.security=TRACE`。会打印每个请求经过哪些 Filter、为什么放行/拦截。
2. **访问 `/actuator/mappings`**（需要 actuator）：看所有路由和它们的安全配置。
3. **Postman/curl 带完整头**：`Authorization: Bearer xxx` 空格别少、别多。
4. **检查 issuer-uri 能否访问**：`curl $ISSUER_URI/.well-known/openid-configuration`。

## 踩坑清单

- **`.authenticated()` 之后又写 `.permitAll()`**：顺序错，永远不生效。
- **`hasRole("ADMIN")` 和 JWT claim 对不上**：Spring 自动加 `ROLE_` 前缀。你的 JWT 里是 `admin`，对应 `.hasAuthority("admin")`。
- **登录成功老是跳根路径**：`oauth2Login().defaultSuccessUrl(url, true)` 的第二个参数 `alwaysUse=true` 才会忽略保存的请求。本项目用 `false` 所以优先跳原本想去的页面。
- **POST 接口莫名 403**：CSRF 没关也没配 token。要么关，要么让前端带 token。
- **登出后仍然能访问受保护资源**：`/logout` 默认只接受 POST。GET 登出要显式 `.logoutRequestMatcher(...)`。
- **`@AuthenticationPrincipal` 拿到 null**：要么没登录，要么实际类型和你声明的不同（比如 OAuth2 用户却声明 `OidcUser`，某些 IdP 的确只给 OAuth2 不给 OIDC）。先声明为 `Object` debug 看看。
- **CORS 和 Security 冲突**：`.cors(withDefaults())` 必须在 Security 里激活，且 `CorsConfigurationSource` Bean 要配好。单独 `@CrossOrigin` 在 Security 启用时不够。
