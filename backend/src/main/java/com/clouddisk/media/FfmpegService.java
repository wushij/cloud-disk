package com.clouddisk.media;

import com.clouddisk.config.CloudDiskProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FfmpegService {

    private final CloudDiskProperties properties;
    private volatile String resolvedPath;

    @PostConstruct
    void logAvailability() {
        if (!properties.getFfmpeg().isEnabled()) {
            log.warn("FFmpeg 已禁用（clouddisk.ffmpeg.enabled=false），视频封面与转码将不可用");
            return;
        }
        if (isAvailable()) {
            log.info("FFmpeg 可用: {}", resolvedPath);
        } else {
            log.warn("FFmpeg 不可用，请安装并加入 PATH，或在 application.yml 配置 clouddisk.ffmpeg.path（例如 C:/ffmpeg/bin/ffmpeg.exe）");
        }
    }

    public boolean isAvailable() {
        if (!properties.getFfmpeg().isEnabled()) {
            return false;
        }
        return StringUtils.hasText(resolveExecutable());
    }

    public String resolveExecutable() {
        if (resolvedPath != null) {
            return resolvedPath;
        }
        synchronized (this) {
            if (resolvedPath != null) {
                return resolvedPath;
            }
            resolvedPath = locateExecutable();
            return resolvedPath;
        }
    }

    private String locateExecutable() {
        String configured = properties.getFfmpeg().getPath();
        if (testExecutable(configured)) {
            return configured;
        }
        if (isWindows()) {
            for (String candidate : List.of(
                    "ffmpeg.exe",
                    "C:\\ffmpeg\\bin\\ffmpeg.exe",
                    "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
                    System.getenv("LOCALAPPDATA") + "\\Microsoft\\WinGet\\Links\\ffmpeg.exe")) {
                if (candidate != null && testExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean testExecutable(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        try {
            Process p = new ProcessBuilder(path, "-version").redirectErrorStream(true).start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** 视频截图（封面） */
    public void captureScreenshot(Path input, Path output, String time) throws Exception {
        Files.createDirectories(output.getParent());
        String ffmpeg = requireExecutable();
        List<String> cmd = List.of(
                ffmpeg,
                "-y",
                "-ss", time,
                "-i", input.toString(),
                "-vframes", "1",
                "-q:v", "2",
                output.toString()
        );
        run(cmd, 120);
    }

    /** 转码为 MP4 H.264（720p） */
    public void transcodeToMp4(Path input, Path output) throws Exception {
        Files.createDirectories(output.getParent());
        var ff = properties.getFfmpeg();
        String ffmpeg = requireExecutable();
        List<String> cmd = new ArrayList<>(List.of(
                ffmpeg,
                "-y",
                "-i", input.toString(),
                "-c:v", ff.getVideoCodec(),
                "-preset", "fast",
                "-vf", "scale=-2:720",
                "-c:a", ff.getAudioCodec(),
                "-movflags", "+faststart",
                output.toString()
        ));
        run(cmd, 3600);
    }

    private String requireExecutable() {
        String ffmpeg = resolveExecutable();
        if (!StringUtils.hasText(ffmpeg)) {
            throw new IllegalStateException("FFmpeg 不可用");
        }
        return ffmpeg;
    }

    private void run(List<String> cmd, long timeoutSec) throws Exception {
        log.info("FFmpeg: {}", String.join(" ", cmd));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                log.debug("ffmpeg> {}", line);
            }
        }
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new RuntimeException("FFmpeg 执行超时");
        }
        if (p.exitValue() != 0) {
            throw new RuntimeException("FFmpeg 执行失败 exit=" + p.exitValue());
        }
    }
}
