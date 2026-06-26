package com.clouddisk.media;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 重新调度长时间停留在 PENDING / PROCESSING 的视频，避免上传后转码任务丢失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoProcessRecoveryTask {

    private final FileMapper fileMapper;
    private final VideoProcessService videoProcessService;
    private final CloudDiskProperties properties;

    @Qualifier("mediaExecutor")
    private final Executor mediaExecutor;

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void recoverStuckVideos() {
        if (!properties.getSchedule().isEnabled()) {
            return;
        }
        LocalDateTime pendingCutoff = LocalDateTime.now().minusSeconds(90);
        LocalDateTime processingCutoff = LocalDateTime.now().minusMinutes(15);

        List<FileRecord> pending = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getTranscodeStatus, TranscodeStatus.PENDING)
                .eq(FileRecord::getStatus, 1)
                .and(w -> w.isNull(FileRecord::getUpdateTime).or().lt(FileRecord::getUpdateTime, pendingCutoff))
                .last("LIMIT 30"));

        List<FileRecord> processing = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getTranscodeStatus, TranscodeStatus.PROCESSING)
                .eq(FileRecord::getStatus, 1)
                .lt(FileRecord::getUpdateTime, processingCutoff)
                .last("LIMIT 10"));

        redispatch(pending);
        redispatch(processing);
    }

    private void redispatch(List<FileRecord> files) {
        for (FileRecord file : files) {
            if (!MediaProcessService.isVideo(file)) {
                continue;
            }
            Long fileId = file.getId();
            log.info("重新调度视频处理 fileId={} status={}", fileId, file.getTranscodeStatus());
            mediaExecutor.execute(() -> {
                try {
                    videoProcessService.processVideo(fileId);
                } catch (Exception e) {
                    log.error("视频处理恢复失败 fileId={}: {}", fileId, e.getMessage(), e);
                }
            });
        }
    }
}
