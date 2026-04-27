package com.example.iam.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时同步任务。
 *
 * @Scheduled(cron = "...") 是 Spring 内置的定时调度,
 * cron 表达式格式:秒 分 时 日 月 周(注意 Spring 的 cron 有 6 位,比 Linux 多一个秒)
 *
 * 生产环境注意:
 *   - 多实例部署时,所有实例都会触发这个任务 → 需要分布式锁(Redisson / Shedlock)
 *   - 任务耗时长时考虑异步执行(@Async)
 *   - 大规模同步要拆分批次 + 用消息队列解耦
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrgSyncScheduler {

    private final OrgSyncService orgSyncService;

    /**
     * 从配置文件读 cron 表达式,不写死在代码里。
     * ${ldap.sync-cron} 对应 application.yml 的配置项。
     */
    @Scheduled(cron = "${ldap.sync-cron:0 */5 * * * *}")
    public void scheduledSync() {
        log.info("定时任务触发,开始同步组织架构");
        try {
            OrgSyncService.SyncResult result = orgSyncService.syncAll();
            log.info("定时同步成功: {}", result);
        } catch (Exception e) {
            log.error("定时同步失败", e);
        }
    }
}
