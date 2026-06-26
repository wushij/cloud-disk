package com.clouddisk.media;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.mq.MediaMessage;
import com.clouddisk.mq.MediaMessageProducer;
import com.clouddisk.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaProcessService {

    private static final long IMAGE_COMPRESS_THRESHOLD = 512 * 1024;

    private final CloudDiskProperties properties;
    private final ObjectProvider<MediaMessageProducer> mediaMessageProducer;
    private final VideoProcessService videoProcessService;
    private final ThumbnailService thumbnailService;
    private final ImageCompressService imageCompressService;
    private final FileMapper fileMapper;

    @Qualifier("mediaExecutor")
    private final Executor mediaExecutor;

    public void afterFileCreated(FileRecord record) {
        if (record == null) return;
        String mime = record.getFileType();
        String name = record.getFileName();
        if (mime != null && mime.startsWith("image/")) {
            dispatchThumbnail(record);
            if (record.getFileSize() != null && record.getFileSize() >= IMAGE_COMPRESS_THRESHOLD) {
                dispatchImageCompress(record);
            }
            return;
        }
        if (isVideo(mime, name)) {
            dispatchVideoTasks(record);
        }
    }

    private void dispatchImageCompress(FileRecord record) {
        MediaMessage msg = toMessage(record);
        MediaMessageProducer producer = mediaMessageProducer.getIfAvailable();
        if (properties.getRabbitmq().isEnabled() && producer != null) {
            producer.sendImageCompressTask(msg);
        } else {
            imageCompressService.compressIfNeeded(record.getId());
        }
    }

    private void dispatchThumbnail(FileRecord record) {
        MediaMessage msg = toMessage(record);
        MediaMessageProducer producer = mediaMessageProducer.getIfAvailable();
        if (properties.getRabbitmq().isEnabled() && producer != null) {
            producer.sendThumbnailTask(msg);
        } else {
            thumbnailService.generateAsync(record.getId());
        }
    }

    private void dispatchVideoTasks(FileRecord record) {
        if (reusePeerVideoMedia(record)) {
            return;
        }
        MediaMessage msg = toMessage(record);
        MediaMessageProducer producer = mediaMessageProducer.getIfAvailable();
        if (properties.getRabbitmq().isEnabled() && producer != null) {
            producer.sendVideoTranscodeTask(msg);
        } else {
            submitVideoProcess(record.getId());
        }
    }

    /** 秒传等同存储路径时复用已有封面/转码结果，避免重复排队 */
    private boolean reusePeerVideoMedia(FileRecord record) {
        if (record == null || record.getId() == null || !StringUtils.hasText(record.getStoragePath())) {
            return false;
        }
        FileRecord peer = fileMapper.selectOne(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getStoragePath, record.getStoragePath())
                .ne(FileRecord::getId, record.getId())
                .and(w -> w.isNotNull(FileRecord::getPosterPath).or().isNotNull(FileRecord::getThumbnailPath))
                .orderByDesc(FileRecord::getUpdateTime)
                .last("LIMIT 1"));
        if (peer == null) {
            return false;
        }
        record.setPosterPath(peer.getPosterPath());
        record.setThumbnailPath(peer.getThumbnailPath());
        record.setTranscodePath(peer.getTranscodePath());
        record.setTranscodeStatus(
                StringUtils.hasText(peer.getTranscodeStatus()) ? peer.getTranscodeStatus() : TranscodeStatus.DONE);
        fileMapper.updateById(record);
        log.info("复用同存储视频媒体元数据 fileId={} peerId={}", record.getId(), peer.getId());
        return true;
    }

    private void submitVideoProcess(Long fileId) {
        if (fileId == null) {
            return;
        }
        mediaExecutor.execute(() -> {
            try {
                videoProcessService.processVideo(fileId);
            } catch (Exception e) {
                log.error("视频处理任务异常 fileId={}: {}", fileId, e.getMessage(), e);
            }
        });
    }

    private MediaMessage toMessage(FileRecord record) {
        return new MediaMessage(
                record.getId(),
                record.getUserId(),
                record.getStoragePath(),
                record.getFileName(),
                record.getFileType()
        );
    }

    public static boolean isVideo(String mime, String fileName) {
        if (mime != null && mime.startsWith("video/")) return true;
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv")
                || lower.endsWith(".avi") || lower.endsWith(".mov");
    }

    public static boolean isVideo(FileRecord file) {
        return isVideo(file.getFileType(), file.getFileName());
    }
}
