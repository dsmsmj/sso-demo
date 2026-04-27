package com.example.iam.repository;

import com.example.iam.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository(数据访问层)—— Spring Data JPA 的魔法所在。
 *
 * 你只需要继承 JpaRepository<实体, 主键类型>,就自动获得:
 *   - save() / saveAll()
 *   - findById() / findAll()
 *   - deleteById()
 *   - count() / existsById()
 *   ...等 20+ 个常用方法
 *
 * 额外的查询方法,只要按命名约定写接口,Spring 会自动生成 SQL:
 *   findByUsername        → SELECT * FROM users WHERE username = ?
 *   findByEmailAndStatus  → SELECT * FROM users WHERE email = ? AND status = ?
 *
 * TS 类比:比 TypeORM 的 Repository 还要"无代码"。
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByExternalId(String externalId);

    Optional<User> findByKeycloakSub(String keycloakSub);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    List<User> findByDepartmentExternalId(String departmentExternalId);

    List<User> findByStatus(User.UserStatus status);
}
