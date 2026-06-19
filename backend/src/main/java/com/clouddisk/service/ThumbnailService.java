package com.clouddisk.service;

import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private static final int THUMB_MAX = 200;

    private final FileMapper fileMapper;
    private final StorageService storageService;

    /**
     * 同步缩略图生成（供 RabbitMQ 消费者调用）
     */
    public void generate(Long fileId) {
        try {
            FileRecord file = fileMapper.selectById(fileId);
            if (file == null || file.getFileType() == null || !file.getFileType().startsWith("image/")) {
                return;
            }
            var resource = storageService.loadAsResource(file.getStoragePath());
            try (InputStream in = resource.getInputStream()) {
                BufferedImage src = ImageIO.read(in);
                if (src == null) return;
                BufferedImage thumb = resize(src, THUMB_MAX);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(thumb, "jpg", out);
                String thumbPath = "thumbs/" + file.getUserId() + "/" + fileId + ".jpg";
                storageService.store(new ByteArrayInputStream(out.toByteArray()), thumbPath, out.size(), "image/jpeg");
                file.setThumbnailPath(thumbPath);
                fileMapper.updateById(file);
            }
        } catch (Exception e) {
            log.warn("缩略图生成失败 fileId={}: {}", fileId, e.getMessage());
        }
    }

    @Async("mediaExecutor")
    public void generateAsync(Long fileId) {
        generate(fileId);
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
