package com.example.iam.sync;

import com.example.iam.dto.ExternalDepartment;
import com.example.iam.dto.ExternalUser;
import com.example.iam.entity.Department;
import com.example.iam.entity.User;
import com.example.iam.repository.DepartmentRepository;
import com.example.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 组织架构同步服务 —— 核心业务逻辑在这里。
 *
 * 关键注解:
 *   @Service              把这个类标记为业务服务,由 Spring 管理生命周期
 *   @RequiredArgsConstructor  Lombok 自动生成带所有 final 字段的构造器
 *                             配合 Spring 的构造器注入,这就是依赖注入
 *   @Slf4j                Lombok 自动生成 log 变量,直接 log.info(...) 即可
 *   @Transactional        方法内所有数据库操作在一个事务里,失败自动回滚
 *
 * TS 类比:
 *   class OrgSyncService {
 *     constructor(
 *       private userRepo: UserRepository,
 *       private deptRepo: DepartmentRepository,
 *     ) {}
 *   }
 * 只是 Spring 帮你做了实例化和注入。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrgSyncService {

    // 构造器注入(推荐):final 字段 + @RequiredArgsConstructor
    // 不要用 @Autowired 字段注入,那种方式不利于测试,是老项目遗留风格
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final List<OrgSource> orgSources;  // Spring 自动把所有 OrgSource 实现注入成列表

    /**
     * 执行一次完整同步。
     *
     * 真实场景的增量同步会更复杂:
     *   - 只处理上次同步后变更的数据
     *   - 处理删除(外部系统里没了的要标记 DELETED)
     *   - 分批处理 + 断点续传
     *   - 乐观锁防止并发冲突
     */
    @Transactional
    public SyncResult syncAll() {
        SyncResult result = new SyncResult();
        for (OrgSource source : orgSources) {
            log.info("开始同步数据源: {}", source.getSourceName());
            syncFromSource(source, result);
        }
        log.info("同步完成: {}", result);
        return result;
    }

    private void syncFromSource(OrgSource source, SyncResult result) {
        // 第一步:同步部门
        // 要按层级顺序建,否则子部门找不到父部门
        List<ExternalDepartment> externalDepts = source.fetchDepartments();
        List<ExternalDepartment> sortedDepts = topologicalSort(externalDepts);

        for (ExternalDepartment ext : sortedDepts) {
            upsertDepartment(ext, result);
        }

        // 第二步:同步用户
        List<ExternalUser> externalUsers = source.fetchUsers();
        for (ExternalUser ext : externalUsers) {
            upsertUser(ext, result);
        }
    }

    /**
     * Upsert:存在就更新,不存在就插入。
     * 用 externalId 作为匹配依据,这是组织同步的通用模式。
     */
    private void upsertDepartment(ExternalDepartment ext, SyncResult result) {
        Optional<Department> existing = departmentRepository.findByExternalId(ext.getExternalId());

        if (existing.isPresent()) {
            Department dept = existing.get();
            dept.setName(ext.getName());
            dept.setParentExternalId(ext.getParentExternalId());
            dept.setSortOrder(ext.getSortOrder() != null ? ext.getSortOrder() : 0);
            departmentRepository.save(dept);
            result.deptUpdated++;
        } else {
            Department dept = Department.builder()
                .externalId(ext.getExternalId())
                .name(ext.getName())
                .parentExternalId(ext.getParentExternalId())
                .sortOrder(ext.getSortOrder() != null ? ext.getSortOrder() : 0)
                .build();
            departmentRepository.save(dept);
            result.deptCreated++;
        }
    }

    private void upsertUser(ExternalUser ext, SyncResult result) {
        Optional<User> existing = userRepository.findByExternalId(ext.getExternalId());

        User.UserStatus status = ext.isActive() ? User.UserStatus.ACTIVE : User.UserStatus.DISABLED;

        if (existing.isPresent()) {
            User user = existing.get();
            user.setUsername(ext.getUsername());
            user.setDisplayName(ext.getDisplayName());
            user.setEmail(ext.getEmail());
            user.setPhone(ext.getPhone());
            user.setDepartmentExternalId(ext.getDepartmentExternalId());
            user.setStatus(status);
            userRepository.save(user);
            result.userUpdated++;
        } else {
            User user = User.builder()
                .externalId(ext.getExternalId())
                .username(ext.getUsername())
                .displayName(ext.getDisplayName())
                .email(ext.getEmail())
                .phone(ext.getPhone())
                .departmentExternalId(ext.getDepartmentExternalId())
                .status(status)
                .build();
            userRepository.save(user);
            result.userCreated++;
        }
    }

    /**
     * 简单的拓扑排序:保证父部门在子部门之前处理。
     * 真实项目可以用 Kahn 算法,这里用递归够用。
     */
    private List<ExternalDepartment> topologicalSort(List<ExternalDepartment> depts) {
        Map<String, ExternalDepartment> byId = new HashMap<>();
        for (ExternalDepartment d : depts) {
            byId.put(d.getExternalId(), d);
        }

        List<ExternalDepartment> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (ExternalDepartment d : depts) {
            visit(d, byId, visited, sorted);
        }
        return sorted;
    }

    private void visit(ExternalDepartment d, Map<String, ExternalDepartment> byId,
                       Set<String> visited, List<ExternalDepartment> sorted) {
        if (visited.contains(d.getExternalId())) return;
        visited.add(d.getExternalId());

        String parentId = d.getParentExternalId();
        if (parentId != null && byId.containsKey(parentId)) {
            visit(byId.get(parentId), byId, visited, sorted);
        }
        sorted.add(d);
    }

    /**
     * 同步结果统计。
     * 用 static class 表示"只在这个外层类里用的辅助结构",
     * 类似 TS 里定义在模块内部但不 export 的小类型。
     */
    public static class SyncResult {
        public int deptCreated;
        public int deptUpdated;
        public int userCreated;
        public int userUpdated;

        @Override
        public String toString() {
            return String.format("SyncResult{dept: +%d/~%d, user: +%d/~%d}",
                deptCreated, deptUpdated, userCreated, userUpdated);
        }
    }
}
