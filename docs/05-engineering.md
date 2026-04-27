# 05. 工程实践

配置、日志、测试、打包。把前几章学到的东西真正用起来需要的东西。

## application.yml 分层

Spring Boot 按优先级合并多份配置，**高优先级覆盖低优先级**：

```
命令行 --server.port=9000
  ↓
SPRING_APPLICATION_JSON 环境变量
  ↓
OS 环境变量 (SERVER_PORT=9000)
  ↓
application-{profile}.yml
  ↓
application.yml
  ↓
@PropertySource 加载的文件
```

实战规则：
- **默认值放 `application.yml`**
- **环境差异放 `application-{dev,prod}.yml`**
- **敏感信息（密钥 / DB 密码）永远走环境变量**，不进 git

环境变量命名转换：`spring.datasource.url` → `SPRING_DATASOURCE_URL`（大写，点变下划线）。

## Profile

激活方式（任选）：

```bash
# 命令行
java -jar app.jar --spring.profiles.active=prod

# 环境变量
export SPRING_PROFILES_ACTIVE=prod

# IDE run configuration / application.yml 里
spring:
  profiles:
    active: dev
```

多个同时激活：`--spring.profiles.active=prod,audit`。后者覆盖前者。

## 读配置的两种姿势

单值 `@Value`：

```java
@Value("${server.port:8080}")   // 冒号后是默认值
private int port;
```

成组 `@ConfigurationProperties`（**强烈推荐成组用这个**）：

```java
@ConfigurationProperties(prefix = "ldap")
@Data
public class LdapProperties {
    private String url;
    private String baseDn;
    private String username;
    private String password;
    private String syncCron;
}
```

注册方式二选一：

```java
// 方式 A: 类上加 @Component
@Component
@ConfigurationProperties(prefix = "ldap")
public class LdapProperties { ... }

// 方式 B: 启动类或 @Configuration 上
@EnableConfigurationProperties(LdapProperties.class)
```

使用：像普通 Bean 注入。

```java
@Service
@RequiredArgsConstructor
public class LdapService {
    private final LdapProperties props;
}
```

优点：类型安全（String/int/List/嵌套对象都能绑）、IDE 补全、启动时校验（`@Validated` + JSR-303）。

## 日志

Spring Boot 默认集成 Logback。代码里：

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrgSyncService {
    private static final Logger log = LoggerFactory.getLogger(OrgSyncService.class);
    // 或者 Lombok: 在类上加 @Slf4j,然后直接用 log.xxx
}

log.debug("开始同步,总数={}", total);
log.info("同步完成");
log.warn("字段缺失: {}", field);
log.error("同步失败", e);   // 第二个参数是 Throwable
```

**关键实践**：

1. **用占位符 `{}`，不用字符串拼接**。日志级别不够时省掉拼接开销。
2. **异常日志把 `Throwable` 当参数传**，不要 `log.error(e.getMessage())` —— 丢栈。
3. **只在 DEBUG 里打 SQL/敏感字段**，INFO 给业务里程碑。

配置日志级别：

```yaml
logging:
  level:
    root: INFO
    com.example.iam: DEBUG                  # 自己代码详细
    org.springframework.security: TRACE     # 调 Security 时开
    org.hibernate.SQL: DEBUG                # 看 SQL
    org.hibernate.type.descriptor.sql: TRACE  # 看 SQL 的参数值
```

日志文件输出 + 滚动：

```yaml
logging:
  file:
    name: logs/iam.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 30
```

复杂需求（JSON 格式 / 多 appender）放 `src/main/resources/logback-spring.xml`，这个文件存在 Spring Boot 就不会用 yml 的日志配置。

## Actuator：生产监控端点

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

默认只开 `/actuator/health`。按需打开：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, mappings, env, loggers
  endpoint:
    health:
      show-details: when-authorized
```

常用端点：

| 端点 | 作用 |
|---|---|
| `/actuator/health` | 健康检查（K8s liveness/readiness） |
| `/actuator/info` | 应用信息（版本号等） |
| `/actuator/metrics` | 指标（JVM/HTTP/DB） |
| `/actuator/mappings` | 所有路由映射 |
| `/actuator/env` | 当前生效的所有配置 |
| `/actuator/loggers` | 运行时改日志级别（POST） |

**生产要用 Security 保护这些端点**，特别是 `env` 会暴露敏感值。

## 测试分层

Spring Boot 提供分层测试注解，**越往下启动越快**：

| 注解 | 启动什么 | 什么时候用 |
|---|---|---|
| `@SpringBootTest` | 完整容器 | 集成测试，端到端流程 |
| `@WebMvcTest(UserController.class)` | 只加载 Web 层 | Controller 单独测 |
| `@DataJpaTest` | 只加载 JPA + 内存 DB | Repository 测试 |
| `@JsonTest` | 只加载 Jackson | 序列化/反序列化测试 |
| 纯 JUnit + Mockito | 什么都不启动 | Service 的业务逻辑 |

### Controller 测试（MockMvc）

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserRepository userRepository;
    @MockBean OrgSyncService orgSyncService;

    @Test
    @WithMockUser   // 模拟已登录
    void listUsers() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of(
            User.builder().id(1L).username("alice").build()
        ));

        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    void listUsersUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().is3xxRedirection());  // 跳登录
    }
}
```

### Repository 测试

```java
@DataJpaTest
class UserRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired TestEntityManager em;

    @Test
    void findByUsername() {
        em.persist(User.builder().username("alice").externalId("x1").build());

        Optional<User> found = userRepository.findByUsername("alice");

        assertThat(found).isPresent();
    }
}
```

`@DataJpaTest` 默认用 H2 内存库 + 每个测试事务回滚，互相不影响。

### 集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IamApplicationIT {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void healthCheck() {
        ResponseEntity<String> res = rest.getForEntity(
            "http://localhost:" + port + "/api/public/health", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

`RANDOM_PORT` 避免端口冲突。

### 真实 DB 集成：Testcontainers

单元测试用 H2 很快，但 H2 和生产 DB 行为会有差异（尤其 PostgreSQL 独有特性）。关键路径的集成测试用 Testcontainers：

```java
@Testcontainers
@SpringBootTest
class PostgresIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

启动会拉镜像 + 跑容器，慢，但真实。

## 打包和运行

```bash
mvn clean package              # 打 fat jar,在 target/
java -jar target/iam-starter-0.0.1-SNAPSHOT.jar

mvn spring-boot:run            # 开发时直接跑,自带重启
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

覆盖配置：

```bash
# 命令行
java -jar app.jar --server.port=9000 --spring.profiles.active=prod

# 环境变量
SERVER_PORT=9000 java -jar app.jar

# 外部 config 文件
java -jar app.jar --spring.config.location=file:/etc/iam/application.yml
```

## Docker

最简 Dockerfile：

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/iam-starter-*.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
```

更好的方案：**分层构建 + 镜像缓存**：

```dockerfile
# 构建阶段
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline   # 这层可以缓存
COPY src ./src
RUN mvn package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Spring Boot 3 的原生镜像方案：`mvn spring-boot:build-image`（需要 Docker 运行中）。

## 数据库迁移：Flyway

`ddl-auto: update` 只能演示。生产必须显式管理 schema：

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

```
src/main/resources/db/migration/
  V1__init_schema.sql
  V2__add_phone_column.sql
  V3__seed_departments.sql
```

Spring Boot 启动时自动运行未执行的迁移。切记：
- 版本号（V1/V2）**只加，不改**
- 改已发布的 migration 会导致生产校验失败
- 回滚用新的 migration 正向撤销，不删旧文件

## 常见工程检查清单

部署前过一遍：

- [ ] 所有敏感配置走环境变量，`application.yml` 里只留默认/引用 `${FOO}`
- [ ] 生产 profile 的 `ddl-auto: validate` 或 `none`，迁移走 Flyway
- [ ] `server.tomcat.max-threads` / 连接池大小按压测结果调
- [ ] 日志级别 INFO，敏感字段不打日志
- [ ] Actuator 端点用 Security 保护，`health` 可公开，`env`/`loggers` 必须鉴权
- [ ] 健康检查暴露给 K8s / 负载均衡
- [ ] `Dockerfile` 多阶段构建，最终镜像不含 Maven / 源码
- [ ] JVM 参数：`-XX:+UseG1GC -XX:MaxRAMPercentage=75` 之类按容器内存调
- [ ] 有监控（Prometheus + Grafana）和告警

## 调试技巧

- **看启动做了什么**：`mvn spring-boot:run -Ddebug` 打印自动配置报告
- **看配置最终值**：`/actuator/env` 或启动加 `--debug`
- **看所有 Bean**：`ConfigurableApplicationContext ctx` 注入后 `ctx.getBeanDefinitionNames()`
- **看所有路由**：`/actuator/mappings`
- **运行时改日志级别**：`POST /actuator/loggers/com.example.iam {"configuredLevel":"TRACE"}`
- **看 SQL + 参数值**：`logging.level.org.hibernate.SQL=DEBUG` + `org.hibernate.type.descriptor.sql=TRACE`
