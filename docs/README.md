# Spring Boot 学习文档（TS 背景向）

这套文档配合 `iam-starter` 项目食用。假设你有 Node/TS 项目经验，不浪费篇幅讲「什么是 HTTP」这种，只讲 Spring/Java 世界里你会 **第一次遇到就踩坑** 的地方。

## 建议阅读顺序

1. [01. Spring 核心：IoC / DI / 自动配置](./01-spring-core.md)
   先搞懂 Bean 和注入，后面所有章节都建立在这上面。
2. [02. Web / REST](./02-web-rest.md)
   对应 `UserController.java`。参数绑定、异常、校验。
3. [03. 数据层 JPA](./03-data-jpa.md)
   对应 `entity/` 和 `repository/`。Entity 关系、方法命名查询、事务。
4. [04. Security / OAuth2](./04-security-oauth2.md)
   对应 `SecurityConfig.java`。本项目最难、也最值钱的部分。
5. [05. 工程实践](./05-engineering.md)
   `application.yml`、profile、测试、打包。日常开发真正需要的东西。

## 速查表（英 → 中文关键词）

| 英文术语 | 含义 | 在项目中的位置 |
|---|---|---|
| Bean | 被 Spring 容器管理的对象 | 所有 `@Service` `@Repository` `@Controller` |
| IoC Container | 装所有 Bean 的池子 | `IamApplication` 启动后就有 |
| Dependency Injection | Spring 把 Bean 塞进你构造器 | `UserController` 的 `@RequiredArgsConstructor` |
| Auto-configuration | starter 自动帮你配好组件 | `spring-boot-starter-*` 的威力 |
| Filter Chain | 请求经过的一串处理器 | `SecurityConfig.securityFilterChain` |
| Entity | 映射到数据库表的类 | `User` `Department` |
| Repository | 数据访问接口 | `UserRepository` `DepartmentRepository` |

## 官方资源（按有用程度）

- [Spring Boot 参考文档](https://docs.spring.io/spring-boot/index.html) — 查配置项必去
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/index.html) — Security 章节绕不开
- [Baeldung](https://www.baeldung.com/) — 实战教程写得最好的英文站
- [Spring 官方 Guides](https://spring.io/guides) — 短小的 how-to，适合对比自己的代码
