package com.example.iam.sync;

import com.example.iam.dto.ExternalDepartment;
import com.example.iam.dto.ExternalUser;

import java.util.List;

/**
 * 组织架构数据源接口(抽象)。
 *
 * 设计关键:用接口隔离"数据源"和"同步逻辑"。
 *   - 今天接 LDAP,写一个 LdapOrgSource implements OrgSource
 *   - 明天接钉钉,写一个 DingtalkOrgSource implements OrgSource
 *   - 同步服务本身代码不用动
 *
 * TS 类比:interface OrgSource { fetchUsers(): Promise<ExternalUser[]>; ... }
 *
 * 这就是面向接口编程,Java 工程文化的基石之一。
 */
public interface OrgSource {

    /**
     * 数据源的名字,用于日志和指标。
     */
    String getSourceName();

    /**
     * 拉取所有部门。
     * 返回的列表应该是"拓扑有序"的(父部门在前,子部门在后),
     * 或者同步服务里自己做排序。
     */
    List<ExternalDepartment> fetchDepartments();

    /**
     * 拉取所有用户。
     * 真实场景可能要支持分页 + 增量拉取(只拉上次同步后变更的),
     * 这里简化为全量拉取。
     */
    List<ExternalUser> fetchUsers();
}
