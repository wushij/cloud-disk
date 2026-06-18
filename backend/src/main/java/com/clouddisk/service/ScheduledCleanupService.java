package com.clouddisk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${xxl.job.enabled:false}' == 'false' && '${clouddisk.schedule.enabled:true}' == 'true'")
public class ScheduledCleanupService {

    private final CleanupTaskService cleanupTaskService;

    @Scheduled(cron = "0 0 * * * ?")
    public void cleanExpiredShares() {
        cleanupTaskService.cleanExpiredShares();
    }

    @Scheduled(cron = "0 30 2 * * ?")
    public void cleanOldRecycle() {
        cleanupTaskService.cleanOldRecycle();
    }

    @Scheduled(cron = "0 15 3 * * ?")
    public void cleanExpiredChunks() {
        cleanupTaskService.cleanExpiredChunks();
    }
}
