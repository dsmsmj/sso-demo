package com.example.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 应用主入口。
 *
 * TS 类比:相当于 Node 项目里的 index.ts / main.ts
 *
 * @SpringBootApplication 是一个"组合注解",等价于同时加了:
 *   - @Configuration        声明这是配置类
 *   - @EnableAutoConfiguration 打开自动装配(Spring Boot 的魔法来源)
 *   - @ComponentScan        扫描当前包及子包下的组件
 *
 * @EnableScheduling 打开定时任务支持,后面 OrgSyncScheduler 才能用 @Scheduled
 */
@SpringBootApplication
@EnableScheduling
public class IamApplication {
    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }
}
