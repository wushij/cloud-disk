package com.clouddisk.media;

import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.service.NotificationDispatcher;
import com.clouddisk.service.ThumbnailService;
import com.clouddisk.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessService {

    private final FileMapper fileMapper;
    private final StorageService storageService;
    private final FfmpegService ffmpegService;
    private final ThumbnailService thumbnailService;
    private final CloudDiskProperties properties;
    private final NotificationDispatcher notificationDispatcher;

    @Async("mediaExecutor")
    public void processAsync(Long fileId) {
        processVideo(fileId);
    }

    public void processVideo(Long fileId) {
        FileRecord file = fileMapper.selectById(fileId);
        if (file == null || !MediaProcessService.isVideo(file)) return;
        if (!ffmpegService.isAvailable()) {
            log.warn("FFmpeg 不可用，跳过视频处理 fileId={}", fileId);
            thumbnailService.generateAsync(fileId);
            return;
        }
        Path workDir = null;
        try {
            file.setTranscodeStatus(TranscodeStatus.PROCESSING);
            fileMapper.updateById(file);

            workDir = Files.createTempDirectory("cd-video-" + fileId);
            Path input = workDir.resolve("source");
            try (InputStream in = storageService.loadAsResource(file.getStoragePath()).getInputStream()) {
                Files.copy(in, input);
            }

            Path poster = workDir.resolve("poster.jpg");
            ffmpegService.captureScreenshot(input, poster, properties.getFfmpeg().getScreenshotTime());
            String posterPath = "posters/" + file.getUserId() + "/" + fileId + ".jpg";
            try (InputStream in = Files.newInputStream(poster)) {
                storageService.store(in, posterPath, Files.size(poster), "image/jpeg");
            }
            file.setPosterPath(posterPath);
            file.setThumbnailPath(posterPath);

            Path output = workDir.resolve("output.mp4");
            ffmpegService.transcodeToMp4(input, output);
            String base = file.getFileName();
            int dot = base.lastIndexOf('.');
            String transName = (dot > 0 ? base.substring(0, dot) : base) + properties.getFfmpeg().getTranscodeSuffix();
            String transPath = file.getUserId() + "/transcoded/" + fileId + "_" + transName.replaceAll("[\\\\/:*?\"<>|]", "_");
            try (InputStream in = Files.newInputStream(output)) {
                storageService.store(in, transPath, Files.size(output), "video/mp4");
            }
            file.setTranscodePath(transPath);
            file.setTranscodeStatus(TranscodeStatus.DONE);
            fileMapper.updateById(file);
            log.info("视频处理完成 fileId={}", fileId);
            notificationDispatcher.dispatch(file.getUserId(), "TRANSCODE_DONE",
                    "视频转码完成", file.getFileName() + " 已转码完成", String.valueOf(fileId));
        } catch (Exception e) {
            log.error("视频处理失败 fileId={}: {}", fileId, e.getMessage());
            // 复用已加载的 file 记录，避免重复查询
            if (file != null) {
                file.setTranscodeStatus(TranscodeStatus.FAILED);
                fileMapper.updateById(file);
            }
            thumbnailService.generateAsync(fileId);
        } finally {
            if (workDir != null) {
                try {
                    Files.walk(workDir).sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }
    }
}
