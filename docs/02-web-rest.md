# 02. Web / REST

对照本项目的 `UserController.java`。

## @RestController 是什么

```java
@RestController      // = @Controller + @ResponseBody
@RequestMapping("/api")
public class UserController { ... }
```

- `@Controller`：返回值当视图名（Thymeleaf/JSP 时代）
- `@ResponseBody`：返回值序列化成 JSON 写入 body
- **`@RestController` 就是两个合体**，现代 REST API 一律用这个

## 路由映射

```java
@GetMapping("/users")            // GET /api/users
@PostMapping("/users")           // POST
@PutMapping("/users/{id}")       // PUT
@DeleteMapping("/users/{id}")    // DELETE
@PatchMapping("/users/{id}")
@RequestMapping(value="/x", method=RequestMethod.GET)  // 通用写法
```

Express 对照：

```typescript
app.get('/api/users', (req, res) => ...)
```

```java
@GetMapping("/api/users")
public List<User> list() { return ... }
```

## 参数绑定一览

| 注解 | Express 对照 | 例子 |
|---|---|---|
| `@PathVariable Long id` | `req.params.id` | `/users/{id}` |
| `@RequestParam String q` | `req.query.q` | `/users?q=xx` |
| `@RequestBody UserDto body` | `req.body` | POST body → DTO |
| `@RequestHeader("X-Foo") String foo` | `req.headers['x-foo']` | |
| `@CookieValue("sid") String sid` | `req.cookies.sid` | |
| `HttpServletRequest req` | `req` 原始对象 | 最后手段 |
| `@AuthenticationPrincipal OidcUser user` | 自己解析 session | Spring Security 注入 |

注意：
- `@PathVariable` 默认必填；`@RequestParam(required=false)` 才是可选。
- `@RequestBody` 不能用在 GET 上（GET 没有 body）。
- 参数名匹配：Spring 默认用 **形参名**。如果编译没带 `-parameters`，会失败。用 `@RequestParam("q") String query` 显式指定更保险。

## 返回值

### 最简单：直接返回对象

```java
@GetMapping("/users")
public List<User> list() {
    return userRepository.findAll();  // 自动 JSON + 200
}
```

### 想要控制状态码：`ResponseEntity`

```java
@GetMapping("/users/{id}")
public ResponseEntity<User> get(@PathVariable Long id) {
    return userRepository.findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}
```

本项目的 `UserController.me(...)` 就用了这个模式返回 401：

```java
return ResponseEntity.status(401).body(Map.of("authenticated", false));
```

### 想返回固定 HTTP 状态

```java
@PostMapping("/users")
@ResponseStatus(HttpStatus.CREATED)   // 201
public User create(...) { ... }
```

## 异常处理

**不要在 Controller 里写 try/catch 做错误响应**，那是 Express 心智。在 Spring 里：

```java
// 1. 定义业务异常
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("用户不存在: " + id);
    }
}

// 2. Controller 直接 throw
@GetMapping("/users/{id}")
public User get(@PathVariable Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));
}

// 3. 全局处理器统一处理
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handle(UserNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }
}
```

**一个 `@RestControllerAdvice` 类管所有异常**，比散在各 controller 好维护。

## 参数校验

引入 `spring-boot-starter-validation`（本项目已加）后：

```java
public record CreateUserDto(
    @NotBlank String username,
    @Email String email,
    @Size(min = 8, max = 64) String password,
    @Min(18) Integer age
) {}

@PostMapping("/users")
public User create(@Valid @RequestBody CreateUserDto dto) {
    // @Valid 不过会抛 MethodArgumentNotValidException
}
```

常用校验注解：

| 注解 | 作用 |
|---|---|
| `@NotNull` | 不能 null（允许空串） |
| `@NotBlank` | 不能 null / 空串 / 纯空格（仅 String） |
| `@NotEmpty` | 不能 null / 空（Collection/String/Array） |
| `@Size(min=, max=)` | 长度 / 大小 |
| `@Min(n) / @Max(n)` | 数字范围 |
| `@Email` | 邮箱格式 |
| `@Pattern(regexp=)` | 正则 |

嵌套对象需要 `@Valid`：

```java
public class Order {
    @Valid
    private Address address;   // 这个加了才会深入校验 Address 的字段
}
```

## 跨域（CORS）

本项目前端是 Spring Boot 同源提供的，不需要 CORS。但真实项目中前后端分离：

```java
// 推荐:全局配置
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:3000")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowCredentials(true);
            }
        };
    }
}
```

**用了 Spring Security 的话**，还需要在 `SecurityConfig` 里 `.cors(withDefaults())` 激活。顺序：Security 的 CORS 过滤器必须在 auth 之前。

## Filter vs Interceptor vs AOP

三个都能"在请求前后做事"，选哪个看位置：

| 机制 | 位置 | 能访问什么 | 何时用 |
|---|---|---|---|
| Servlet Filter | Servlet 容器最外层 | `ServletRequest` | 日志 / CORS / 字符编码 |
| HandlerInterceptor | DispatcherServlet 之后 | handler 方法信息 | 认证检查、MDC 打标 |
| `@Around` AOP | Service 层 | 方法参数、返回值 | 业务级横切（审计、重试） |

Spring Security 是一堆 Filter。你自己加的话 99% 场景用 Interceptor 就够。

## 踩坑清单

- **JSON 字段名和 Java 字段名不一致**：用 `@JsonProperty("user_name")`。驼峰/下划线统一：`spring.jackson.property-naming-strategy=SNAKE_CASE`。
- **返回 `Map.of()` 且 value 可能为 null**：`Map.of` 不允许 null value，会 NPE。用 `HashMap` 或改造 DTO。
- **`@RequestBody` 报 400 但看不到细节**：默认 Jackson 解析失败信息很简陋。装 `@RestControllerAdvice` 捕获 `HttpMessageNotReadableException` 输出具体原因。
- **大对象返回很慢**：JPA 实体里有懒加载关系直接返回会触发 N+1。返回 DTO 不返回 Entity，或者用 `@JsonIgnore` 切断。详见数据层章节。
- **GET 请求收不到复杂对象**：GET 没 body。参数要用 `@ModelAttribute` 或拆成多个 `@RequestParam`。
