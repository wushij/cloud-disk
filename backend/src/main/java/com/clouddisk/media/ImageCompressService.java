package com.clouddisk.media;

import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 图片压缩（设计文档：RabbitMQ 异步图片压缩，区别于缩略图）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCompressService {

    private static final long COMPRESS_THRESHOLD = 512 * 1024;

    private final FileMapper fileMapper;
    private final StorageService storageService;
    private final CloudDiskProperties properties;

    public void compressIfNeeded(Long fileId) {
        FileRecord file = fileMapper.selectById(fileId);
        if (file == null || file.getFileType() == null || !file.getFileType().startsWith("image/")) {
            return;
        }
        if (file.getFileSize() != null && file.getFileSize() < COMPRESS_THRESHOLD) {
            return;
        }
        if (!properties.getFfmpeg().isEnabled()) {
            // 复用 ffmpeg.enabled 作为媒体处理总开关
        }
        try {
            try (InputStream in = storageService.loadAsResource(file.getStoragePath()).getInputStream()) {
                BufferedImage src = ImageIO.read(in);
                if (src == null) return;
                int max = 1920;
                BufferedImage scaled = resize(src, max);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(scaled, "jpg", out);
                byte[] data = out.toByteArray();
                if (data.length >= file.getFileSize()) {
                    return;
                }
                String compressedPath = "compressed/" + file.getUserId() + "/" + fileId + ".jpg";
                storageService.store(new ByteArrayInputStream(data), compressedPath, data.length, "image/jpeg");
                file.setStoragePath(compressedPath);
                file.setFileType("image/jpeg");
                file.setFileSize((long) data.length);
                fileMapper.updateById(file);
                log.info("图片压缩完成 fileId={} -> {} bytes", fileId, data.length);
            }
        } catch (Exception e) {
            log.warn("图片压缩失败 fileId={}: {}", fileId, e.getMessage());
        }
    }

    private BufferedImage resize(BufferedImage src, int max) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) max / Math.max(w, h));
        int nw = Math.max(1, (int) (w * scale));
        int nh = Math.max(1, (int) (h * scale));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }
}
