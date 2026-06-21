package com.clouddisk.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface StorageService {
    String store(InputStream input, String relativePath, long size) throws Exception;

    default String store(InputStream input, String relativePath, long size, String contentType) throws Exception {
        return store(input, relativePath, size);
    }

    Resource loadAsResource(String relativePath);

    Path resolvePath(String relativePath);

    void delete(String relativePath);

    void deleteByPrefix(String prefix);

    boolean exists(String relativePath);

    void mergeParts(String targetRelativePath, List<String> partRelativePaths) throws Exception;

    /** 移动对象（用于团队重命名等场景同步更新存储路径） */
    void move(String sourcePath, String targetPath);

    String bucketName();

    String storageType();

    /** MinIO 等对象存储可生成直链（设计文档 CDN/加速场景） */
    Optional<String> presignedDownloadUrl(String relativePath, int expireSeconds);

    /**
     * CDN 加速 URL（当 CDN 启用时返回 CDN 域名链接，否则返回 empty）
     */
    default Optional<String> cdnUrl(String relativePath, int expireSeconds) {
        return Optional.empty();
    }
}
