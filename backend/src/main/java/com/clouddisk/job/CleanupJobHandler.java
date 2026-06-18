package com.clouddisk.job;

import com.clouddisk.service.CleanupTaskService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "xxl.job.enabled", havingValue = "true")
public class CleanupJobHandler {

    private final CleanupTaskService cleanupTaskService;

    @XxlJob("cleanExpiredSharesJob")
    public void cleanExpiredShares() {
        cleanupTaskService.cleanExpiredShares();
        XxlJobHelper.handleSuccess("过期分享清理完成");
    }

    @XxlJob("cleanOldRecycleJob")
    public void cleanOldRecycle() {
        cleanupTaskService.cleanOldRecycle();
        XxlJobHelper.handleSuccess("回收站清理完成");
    }

    @XxlJob("cleanExpiredChunksJob")
    public void cleanExpiredChunks() {
        cleanupTaskService.cleanExpiredChunks();
        XxlJobHelper.handleSuccess("分片临时文件清理完成");
    }
}
