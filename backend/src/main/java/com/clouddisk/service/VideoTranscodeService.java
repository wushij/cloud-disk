package com.clouddisk.service;

import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 视频转码服务 —— 基于 FFmpeg
 * <ul>
 *   <li>transcode: 将视频转码为 H.264 MP4（兼容浏览器播放）</li>
 *   <li>generateScreenshot: 截取视频封面图</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTranscodeService {

    private final CloudDiskProperties properties;
    private final StorageService storageService;

    /**
     * 将视频转码为 H.264 MP4
     *
     * @param inputStoragePath  原始视频在存储中的相对路径
     * @param outputStoragePath 转码后视频在存储中的相对路径
     * @return true 表示转码成功
     */
    public boolean transcode(String inputStoragePath, String outputStoragePath) {
        Path tmpIn = null;
        Path tmpOut = null;
        try {
            // 1. 将存储文件下载到临时目录
            Resource resource = storageService.loadAsResource(inputStoragePath);
            tmpIn = Files.createTempFile("clouddisk-transcode-in-", getExtension(inputStoragePath));
            try (InputStream in = resource.getInputStream();
                 OutputStream out = Files.newOutputStream(tmpIn)) {
                in.transferTo(out);
            }

            tmpOut = Files.createTempFile("clouddisk-transcode-out-", ".mp4");

            // 2. 执行 FFmpeg 转码
            CloudDiskProperties.Ffmpeg ffmpegCfg = properties.getFfmpeg();
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegCfg.getPath(),
                    "-i", tmpIn.toAbsolutePath().toString(),
                    "-c:v", ffmpegCfg.getVideoCodec(),
                    "-c:a", ffmpegCfg.getAudioCodec(),
                    "-movflags", "+faststart",
                    "-y",
                    tmpOut.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取 FFmpeg 输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[FFmpeg] {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("FFmpeg 转码失败，exitCode={}, 输入文件={}", exitCode, inputStoragePath);
                return false;
            }

            // 3. 将转码后文件写回存储
            long fileSize = Files.size(tmpOut);
            try (InputStream transIn = Files.newInputStream(tmpOut)) {
                storageService.store(transIn, outputStoragePath, fileSize, "video/mp4");
            }

            log.info("视频转码成功: {} -> {}", inputStoragePath, outputStoragePath);
            return true;

        } catch (Exception e) {
            log.error("视频转码异常: inputPath={}, error={}", inputStoragePath, e.getMessage(), e);
            return false;
        } finally {
            deleteTempFile(tmpIn);
            deleteTempFile(tmpOut);
        }
    }

    /**
     * 截取视频封面图
     *
     * @param inputStoragePath     视频在存储中的相对路径
     * @param thumbnailStoragePath 封面图在存储中的相对路径
     * @return true 表示截图成功
     */
    public boolean generateScreenshot(String inputStoragePath, String thumbnailStoragePath) {
        Path tmpIn = null;
        Path tmpThumb = null;
        try {
            Resource resource = storageService.loadAsResource(inputStoragePath);
            tmpIn = Files.createTempFile("clouddisk-screenshot-in-", getExtension(inputStoragePath));
            try (InputStream in = resource.getInputStream();
                 OutputStream out = Files.newOutputStream(tmpIn)) {
                in.transferTo(out);
            }

            tmpThumb = Files.createTempFile("clouddisk-screenshot-out-", ".jpg");

            CloudDiskProperties.Ffmpeg ffmpegCfg = properties.getFfmpeg();
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegCfg.getPath(),
                    "-i", tmpIn.toAbsolutePath().toString(),
                    "-ss", ffmpegCfg.getScreenshotTime(),
                    "-vframes", "1",
                    "-q:v", "2",
                    "-y",
                    tmpThumb.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[FFmpeg-screenshot] {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("FFmpeg 截图失败，exitCode={}, 输入文件={}", exitCode, inputStoragePath);
                return false;
            }

            long fileSize = Files.size(tmpThumb);
            try (InputStream thumbIn = Files.newInputStream(tmpThumb)) {
                storageService.store(thumbIn, thumbnailStoragePath, fileSize, "image/jpeg");
            }

            log.info("视频截图成功: {} -> {}", inputStoragePath, thumbnailStoragePath);
            return true;

        } catch (Exception e) {
            log.error("视频截图异常: inputPath={}, error={}", inputStoragePath, e.getMessage(), e);
            return false;
        } finally {
            deleteTempFile(tmpIn);
            deleteTempFile(tmpThumb);
        }
    }

    // ---------- 工具方法 ----------

    private String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot > 0 ? path.substring(dot) : ".tmp";
    }

    private void deleteTempFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }
}
