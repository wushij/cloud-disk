package com.clouddisk.media;

import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.mq.MediaMessage;
import com.clouddisk.mq.MediaMessageProducer;
import com.clouddisk.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

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
        MediaMessage msg = toMessage(record);
        MediaMessageProducer producer = mediaMessageProducer.getIfAvailable();
        if (properties.getRabbitmq().isEnabled() && producer != null) {
            producer.sendVideoTranscodeTask(msg);
        } else {
            videoProcessService.processAsync(record.getId());
        }
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
