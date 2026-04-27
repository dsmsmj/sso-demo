# 03. 数据层：JPA / Spring Data

对照本项目的 `entity/User.java`、`entity/Department.java`、`repository/*`。

## 三层关系

```
        +-----------+
        | Hibernate |   ORM 实现(JPA 规范的默认实现)
        +-----------+
              |
        +-----------+
        |    JPA    |   Jakarta Persistence API(规范,一套注解)
        +-----------+
              |
        +-----------+
        | Spring Data |  在 JPA 上加了 Repository 自动实现等糖
        +-----------+
```

你写的是 **JPA 注解** + **Spring Data 接口**，底层是 Hibernate 在跑 SQL。

## @Entity：类 ↔ 表

```java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username", unique = true)
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;
}
```

关键点：
- 类 → 表（`@Table` 改名，不写就用类名小写）
- 字段 → 列（`@Column` 改名/约束，不写就按字段名驼峰转下划线）
- **必须有无参构造器**（JPA 用反射实例化）—— Lombok `@NoArgsConstructor` 解决
- **必须有 `@Id`**

### 主键策略

| 策略 | 含义 | 适用 |
|---|---|---|
| `IDENTITY` | DB 自增（MySQL/H2） | 默认选择 |
| `SEQUENCE` | 序列（PostgreSQL/Oracle） | PG 推荐 |
| `AUTO` | JPA 自己挑 | 不推荐,行为不确定 |
| `UUID` | 字符串 UUID | 分布式系统 |

### 时间戳回调

本项目用了 JPA 的生命周期钩子：

```java
@PrePersist void onCreate() { this.createdAt = Instant.now(); }
@PreUpdate  void onUpdate() { this.updatedAt = Instant.now(); }
```

也可以用 Spring 的 `@CreatedDate` / `@LastModifiedDate` + `@EntityListeners(AuditingEntityListener.class)`，但需要 `@EnableJpaAuditing`。本项目直接用钩子更简单。

## 关联关系

本项目为了简化，只用 `departmentExternalId` 存字符串，没建真正的外键关系。真实场景：

```java
@Entity
public class User {
    @ManyToOne(fetch = FetchType.LAZY)    // 多个 User 属于一个 Department
    @JoinColumn(name = "department_id")
    private Department department;
}

@Entity
public class Department {
    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private List<User> users = new ArrayList<>();
}
```

**一定记住 `FetchType.LAZY`**：默认 `@ManyToOne` 是 EAGER，查一个 User 就把 Department 查出来，一串下去会灾难。`@OneToMany` 默认已经是 LAZY。

**级联（cascade）慎用**：`CascadeType.ALL` 删一个 Department 会把它下面所有 User 都删掉。通常只用 `PERSIST`。

## Repository：不用写实现

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByDepartmentExternalId(String deptId);
    boolean existsByEmail(String email);
    long countByStatus(UserStatus status);
    void deleteByExternalId(String externalId);
}
```

`JpaRepository<User, Long>` 已经白送了：
- `findAll()` / `findById(id)` / `existsById(id)`
- `save(entity)` / `saveAll(entities)` / `deleteById(id)`
- `count()` / `findAll(Pageable)` / `findAll(Sort)`

**方法名规则**：

| 前缀 | 含义 |
|---|---|
| `findBy` / `getBy` / `readBy` | 查 |
| `existsBy` | 是否存在，返回 boolean |
| `countBy` | 计数 |
| `deleteBy` / `removeBy` | 删（需要 `@Transactional`） |

**条件拼接**：

```java
findByUsernameAndStatus(String, UserStatus)
findByEmailLike(String)                    // SQL LIKE
findByCreatedAtBetween(Instant, Instant)
findByStatusIn(List<UserStatus>)
findByStatusOrderByCreatedAtDesc(UserStatus)
findTop10ByOrderByCreatedAtDesc()
```

## 写不出来的查询：@Query

方法名太长或逻辑复杂，直接写 JPQL：

```java
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where u.status = 'ACTIVE' and u.email like %:domain")
    List<User> findActiveByEmailDomain(@Param("domain") String domain);

    // 原生 SQL
    @Query(value = "select * from users where created_at > now() - interval '7 days'",
           nativeQuery = true)
    List<User> findRecent();

    // 修改型
    @Modifying
    @Transactional
    @Query("update User u set u.status = 'DISABLED' where u.id = :id")
    int disable(@Param("id") Long id);
}
```

**JPQL vs SQL**：JPQL 查的是**实体和字段名**（Java 世界），不是表和列名。`User u` 是 Entity 名，不是表名。

## 分页和排序

```java
Page<User> findByStatus(UserStatus status, Pageable pageable);

// 使用
Pageable page = PageRequest.of(0, 20, Sort.by("createdAt").descending());
Page<User> result = userRepository.findByStatus(ACTIVE, page);
result.getContent();       // List<User>
result.getTotalElements(); // long
result.getTotalPages();    // int
```

Controller 里可以直接收 `Pageable`：

```java
@GetMapping("/users")
public Page<User> list(Pageable pageable) {  // ?page=0&size=20&sort=createdAt,desc
    return userRepository.findAll(pageable);
}
```

## 事务

**Spring Data 的方法默认就在事务里**（只读）。自己的 Service 方法要改数据时加 `@Transactional`：

```java
@Service
@RequiredArgsConstructor
public class OrgSyncService {

    @Transactional
    public SyncResult syncAll() {
        // 整个方法一个事务,任何地方抛异常全部回滚
    }

    @Transactional(readOnly = true)   // 只读优化
    public List<User> listActive() { ... }
}
```

### 事务陷阱（⚠️ 最容易踩的一个）

**同类内部调用 `@Transactional` 方法无效**：

```java
@Service
public class UserService {

    public void a() {
        this.b();   // ❌ b() 的 @Transactional 不生效!
    }

    @Transactional
    public void b() { ... }
}
```

原因：`@Transactional` 靠 Spring 的代理对象拦截。`this.b()` 是直接调用，绕开了代理。

解决：
1. 把 `b()` 挪到另一个 Service，外部调用
2. 注入自己：`@Autowired private UserService self;` 然后 `self.b()`
3. 用 `AopContext.currentProxy()`（不推荐）

### 传播行为

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
```

| 值 | 含义 |
|---|---|
| `REQUIRED`（默认） | 有事务就加入，没有就新建 |
| `REQUIRES_NEW` | 永远新建；调用方的事务挂起 |
| `NESTED` | 嵌套事务（支持部分回滚） |
| `SUPPORTS` | 有事务就用，没有就非事务执行 |
| `NOT_SUPPORTED` | 挂起当前事务，非事务执行 |

99% 情况用默认 `REQUIRED` 就行。

## N+1 问题

```java
List<User> users = userRepository.findAll();   // 1 次 SQL 查 users
users.forEach(u -> log.info(u.getDepartment().getName()));
// 每个 user 触发一次 SQL 查 department -> N 次
```

解决：
1. **`@EntityGraph`**（推荐）

   ```java
   @EntityGraph(attributePaths = {"department"})
   List<User> findAll();
   ```

2. **JPQL `join fetch`**

   ```java
   @Query("select u from User u join fetch u.department")
   List<User> findAllWithDept();
   ```

**总结**：查列表要用到关联字段时，明确告诉 JPA "带着查"。

## 什么时候返回 Entity，什么时候返回 DTO

- **内部调用**：Entity 无所谓。
- **REST 响应**：返回 DTO。Entity 带关联关系，直接序列化会触发懒加载 / N+1 / 循环引用。
- **简单演示项目**（像本 repo 的 `/api/users`）：图方便直接返回 Entity 也行，数据量小、关联少。

## 踩坑清单

- **`@Entity` 的字段名变了，但数据库没改**：`ddl-auto: update` 只加字段、不改 / 不删。生产用 Flyway/Liquibase 管迁移。
- **save 后 `id` 还是 null**：`@GeneratedValue` 是 `IDENTITY` 时，`save` 返回后才有 id。要用返回值，不要用原对象。
- **Transactional 对 private 方法无效**：代理拦截不到 private，必须 public。
- **懒加载异常 `LazyInitializationException`**：事务结束（比如返回到 Controller 里）后再访问懒加载字段就炸。在事务里提前加载好，或者 Controller 层包一层事务（不推荐）。
- **不小心级联删了一片数据**：`CascadeType.ALL` 谨慎用。特别是双向关联 + 删除。
- **`findAll()` 数据量大时 OOM**：加 `Pageable` 或流式 `Stream<User> streamAll()`。
