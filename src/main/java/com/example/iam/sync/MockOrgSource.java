package com.example.iam.sync;

import com.example.iam.dto.ExternalDepartment;
import com.example.iam.dto.ExternalUser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 假数据源,用于演示/本地开发。
 *
 * @Component 把这个类注册进 Spring 容器,可以被注入到其他地方。
 *
 * 真实项目里你会有:
 *   - LdapOrgSource      通过 Spring LDAP 查询 Active Directory
 *   - DingtalkOrgSource  调用钉钉 REST API
 *   - WeworkOrgSource    调用企业微信 API
 *   - ScimOrgSource      走 SCIM 协议
 *
 * 只要都 implements OrgSource,上层同步服务就能统一处理。
 */
@Component
public class MockOrgSource implements OrgSource {

    @Override
    public String getSourceName() {
        return "mock";
    }

    @Override
    public List<ExternalDepartment> fetchDepartments() {
        return List.of(
            ExternalDepartment.builder()
                .externalId("dept-root").name("示例公司")
                .parentExternalId(null).sortOrder(0).build(),
            ExternalDepartment.builder()
                .externalId("dept-tech").name("技术部")
                .parentExternalId("dept-root").sortOrder(1).build(),
            ExternalDepartment.builder()
                .externalId("dept-product").name("产品部")
                .parentExternalId("dept-root").sortOrder(2).build(),
            ExternalDepartment.builder()
                .externalId("dept-hr").name("人事部")
                .parentExternalId("dept-root").sortOrder(3).build()
        );
    }

    @Override
    public List<ExternalUser> fetchUsers() {
        // 前三个用户与 OA 用户库的 email 完全对应，SSO 后可在 IAM 找到匹配档案。
        // admin 只存在于 IAM，演示"IAM 有而 OA 没有"的情况。
        return List.of(
            ExternalUser.builder()
                .externalId("u001").username("zhangsan").displayName("张三")
                .email("zhangsan@example.com").departmentExternalId("dept-tech").active(true).build(),
            ExternalUser.builder()
                .externalId("u002").username("lisi").displayName("李四")
                .email("lisi@example.com").departmentExternalId("dept-product").active(true).build(),
            ExternalUser.builder()
                .externalId("u003").username("wangwu").displayName("王五")
                .email("wangwu@example.com").departmentExternalId("dept-hr").active(true).build(),
            ExternalUser.builder()
                .externalId("u004").username("admin").displayName("系统管理员")
                .email("admin@example.com").departmentExternalId("dept-tech").active(true).build(),
            ExternalUser.builder()
                .externalId("u005").username("muse").displayName("muse muji")
                .email("1157959832@qq.com").departmentExternalId("dept-tech").active(true).build()
        );
    }
}
