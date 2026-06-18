package com.clouddisk.media;

import com.clouddisk.config.CloudDiskProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public boolean isAvailable() {
        if (!properties.getFfmpeg().isEnabled()) return false;
        try {
            Process p = new ProcessBuilder(properties.getFfmpeg().getPath(), "-version").start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 视频截图（封面） */
    public void captureScreenshot(Path input, Path output, String time) throws Exception {
        Files.createDirectories(output.getParent());
        List<String> cmd = List.of(
                properties.getFfmpeg().getPath(),
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
        List<String> cmd = new ArrayList<>(List.of(
                ff.getPath(),
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
