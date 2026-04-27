# 01. Spring 核心：IoC / DI / 自动配置

## 为什么要有 IoC

在 TS/Node 里你可能这样写：

```typescript
const userRepo = new UserRepository(db);
const userService = new UserService(userRepo);
const controller = new UserController(userService);
```

每增加一层依赖，你都要手动 `new`。项目一大，组装代码本身成了麻烦。

Spring 的答案：**把"对象怎么创建、谁依赖谁"交给容器**。你只声明"我需要谁"，容器负责把它塞进来。这叫 **IoC（控制反转）+ DI（依赖注入）**。

本项目里的体现：

```java
// UserController.java
@RestController
@RequiredArgsConstructor            // Lombok 生成构造器
public class UserController {
    private final UserRepository userRepository;  // 我需要这个
    private final OrgSyncService orgSyncService;   // 我还需要这个
}
```

你没有任何地方 `new UserRepository()`，是 Spring 启动时自动创建、并通过构造器塞进 `UserController`。

## Bean：容器里的对象

被容器管理的对象叫 **Bean**。让一个类变成 Bean 的方式：

| 注解 | 语义 | 用在哪 |
|---|---|---|
| `@Component` | 通用 Bean | 工具类 |
| `@Service` | 业务层 | `OrgSyncService` |
| `@Repository` | 数据访问层 | `UserRepository` 继承 `JpaRepository` 自动生成 |
| `@Controller` / `@RestController` | Web 层 | `UserController` |
| `@Configuration` | 配置类 | `SecurityConfig` |

**这些注解在功能上几乎等价**，都是 `@Component` 的变体。差别在于语义 + 少数特殊处理（比如 `@Repository` 会做异常转换，`@Configuration` 的 `@Bean` 方法会被代理）。**选最贴切语义的就行**。

## 构造器注入 vs 字段注入

你会在老教程里看到：

```java
@Autowired
private UserRepository userRepository;   // 字段注入,别写!
```

**现代 Spring 项目一律用构造器注入**（本项目也是）：

```java
private final UserRepository userRepository;  // final 保证不可变

public UserController(UserRepository userRepository) {
    this.userRepository = userRepository;
}
```

为什么：
- 字段可以是 `final`，编译期保证依赖齐全；
- 单元测试不用反射/Mockito hack，直接 `new UserController(mockRepo)`；
- 循环依赖会在启动期就报错，而不是运行期悄悄失败。

Lombok 的 `@RequiredArgsConstructor` 把所有 `final` 字段自动生成为构造器参数 —— 这是本项目的统一写法。

## 依赖注入发生在什么时候

应用启动时，Spring 做这几件事（简化版）：

1. **扫描**：从 `IamApplication` 所在包开始，找所有带注解的类。
2. **实例化**：按依赖顺序创建 Bean。
3. **注入**：调用构造器把依赖塞进去。
4. **初始化回调**：触发 `@PostConstruct` 方法。
5. **就绪**：容器 ready，开始接收 HTTP 请求。

结果：**你永远不会在自己的代码里 new 一个 Service/Controller**。如果你这么做了，那个对象就不是 Bean，它的 `@Autowired` 依赖不会被注入。

## @Configuration + @Bean：手动注册

不是所有 Bean 都能靠注解自动装配。比如一个第三方库的类（你改不了源码），或者需要根据条件选择实现。这时候：

```java
@Configuration
public class AppConfig {

    @Bean
    public OrgSource orgSource() {
        return new MockOrgSource();   // 这个对象从此归 Spring 管
    }
}
```

项目里 `SecurityConfig.securityFilterChain(...)` 就是这种模式：**方法返回值 = Bean，方法参数 = 它依赖的 Bean**。

## 自动配置：Spring Boot 的魔法

Spring Boot 在 Spring 上面加了一层 **auto-configuration**。你只要在 `pom.xml` 加一个 starter：

```xml
<dependency>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

启动时 Spring Boot 发现 classpath 里有 JPA 相关类 + 你配置了 `datasource`，就自动帮你：
- 创建 `DataSource` Bean（连接池）
- 创建 `EntityManagerFactory`
- 扫描 `@Entity` 建表
- 创建 `JpaTransactionManager`
- 生成 `JpaRepository` 的代理实现

**一行依赖 = 几十个 Bean 自动就位**。这是 Spring Boot 让人"简单"的核心原理。

想看它到底装了什么：

```bash
mvn spring-boot:run -Ddebug
# 启动日志里会打印 AUTO-CONFIGURATION REPORT
```

## @Value / @ConfigurationProperties：读配置

读 `application.yml` 里的值有两种方式。单个值用 `@Value`：

```java
@Value("${ldap.url}")
private String ldapUrl;
```

成组的用 `@ConfigurationProperties`（类型安全）：

```java
@ConfigurationProperties(prefix = "ldap")
@Data
public class LdapProps {
    private String url;
    private String baseDn;
    private String username;
    private String password;
    private String syncCron;
}
```

再在某个 `@Configuration` 上加 `@EnableConfigurationProperties(LdapProps.class)`。后面想用就注入这个 `LdapProps`，像普通 Bean 一样。

**建议**：超过 2 个配置项就用 `@ConfigurationProperties`，别拼 `@Value`。

## Profile：按环境切换

```yaml
# application.yml
spring:
  profiles:
    active: dev

---
spring:
  config:
    activate:
      on-profile: dev
datasource:
  url: jdbc:h2:mem:iamdb

---
spring:
  config:
    activate:
      on-profile: prod
datasource:
  url: jdbc:postgresql://...
```

运行时用 `SPRING_PROFILES_ACTIVE=prod` 切换。

类级别也能按 profile 挑实现：

```java
@Service
@Profile("dev")
public class MockOrgSource implements OrgSource { ... }

@Service
@Profile("prod")
public class DingTalkOrgSource implements OrgSource { ... }
```

## 踩坑清单

- **两个 Bean 实现同一接口**：Spring 不知道注入哪个，启动失败。解决：加 `@Primary`，或者用 `@Qualifier("name")` 指定。
- **循环依赖**：A 构造器需要 B，B 构造器需要 A。改造代码（抽一层）或改用 setter 注入。构造器注入会在启动期就暴露这个问题 —— 这是好事。
- **Bean 不生效**：最常见原因是 Bean 所在的包不在 `@SpringBootApplication` 所在包的下面，扫描不到。
- **测试里拿不到 Bean**：测试类没加 `@SpringBootTest`，或者在单元测试里直接 `new`，都不会触发依赖注入。
