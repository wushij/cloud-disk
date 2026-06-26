package com.clouddisk.storage;

import com.clouddisk.config.CloudDiskProperties;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MinioStorageService implements StorageService {

    private static final int PART_SIZE = 10 * 1024 * 1024;

    private final MinioClient client;
    private final String bucket;
    private final String endpoint;
    private final CloudDiskProperties properties;

    public MinioStorageService(CloudDiskProperties properties) {
        var m = properties.getMinio();
        this.bucket = m.getBucket();
        this.endpoint = m.getEndpoint();
        this.properties = properties;
        this.client = MinioClient.builder()
                .endpoint(m.getEndpoint())
                .credentials(m.getAccessKey(), m.getSecretKey())
                .build();
        ensureBucket();
        log.info("MinIO 已连接 endpoint={} bucket={}", endpoint, bucket);
    }

    private void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 存储桶已自动创建: {}", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO 初始化失败，请确认服务已启动: " + endpoint, e);
        }
    }

    @Override
    public String store(InputStream input, String relativePath, long size) throws Exception {
        return store(input, relativePath, size, null);
    }

    @Override
    public String store(InputStream input, String relativePath, long size, String contentType) throws Exception {
        var builder = PutObjectArgs.builder()
                .bucket(bucket)
                .object(relativePath)
                .stream(input, size > 0 ? size : -1, size > 0 ? -1 : PART_SIZE);
        if (StringUtils.hasText(contentType)) {
            builder.contentType(contentType);
        }
        client.putObject(builder.build());
        return relativePath;
    }

    @Override
    public Resource loadAsResource(String relativePath) {
        try {
            InputStream stream = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(relativePath)
                    .build());
            return new InputStreamResource(stream);
        } catch (Exception e) {
            throw new RuntimeException("MinIO 读取失败: " + relativePath, e);
        }
    }

    @Override
    public long size(String relativePath) throws Exception {
        return client.statObject(StatObjectArgs.builder()
                .bucket(bucket)
                .object(relativePath)
                .build()).size();
    }

    @Override
    public Path resolvePath(String relativePath) {
        throw new UnsupportedOperationException("MinIO 不支持本地路径");
    }

    @Override
    public void delete(String relativePath) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(relativePath).build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void deleteByPrefix(String prefix) {
        try {
            String p = prefix.endsWith("/") ? prefix : prefix + "/";
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(p)
                    .recursive(true)
                    .build());
            List<DeleteObject> toDelete = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                toDelete.add(new DeleteObject(item.objectName()));
            }
            if (toDelete.isEmpty()) {
                delete(prefix);
                return;
            }
            client.removeObjects(RemoveObjectsArgs.builder().bucket(bucket).objects(toDelete).build())
                    .forEach(r -> {
                        try {
                            r.get();
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception e) {
            log.warn("MinIO 前缀删除失败 prefix={}: {}", prefix, e.getMessage());
        }
    }

    @Override
    public boolean exists(String relativePath) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(relativePath).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void mergeParts(String targetRelativePath, List<String> partRelativePaths) throws Exception {
        List<ComposeSource> sources = new ArrayList<>();
        for (String part : partRelativePaths) {
            sources.add(ComposeSource.builder().bucket(bucket).object(part).build());
        }
        client.composeObject(ComposeObjectArgs.builder()
                .bucket(bucket)
                .object(targetRelativePath)
                .sources(sources)
                .build());
        for (String part : partRelativePaths) {
            delete(part);
        }
    }

    @Override
    public void move(String sourcePath, String targetPath) {
        if (!StringUtils.hasText(sourcePath) || !StringUtils.hasText(targetPath)) {
            return;
        }
        if (sourcePath.equals(targetPath)) {
            return;
        }
        if (!exists(sourcePath)) {
            return;
        }
        try {
            client.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(targetPath)
                    .source(CopySource.builder().bucket(bucket).object(sourcePath).build())
                    .build());
            delete(sourcePath);
        } catch (Exception e) {
            throw new RuntimeException("MinIO 移动对象失败: " + sourcePath + " -> " + targetPath, e);
        }
    }

    @Override
    public String bucketName() {
        return bucket;
    }

    @Override
    public String storageType() {
        return "minio";
    }

    @Override
    public Optional<String> presignedDownloadUrl(String relativePath, int expireSeconds) {
        try {
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(relativePath)
                    .expiry(expireSeconds, TimeUnit.SECONDS)
                    .build());
            return Optional.of(url);
        } catch (Exception e) {
            log.warn("生成 MinIO 预签名 URL 失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> cdnUrl(String relativePath, int expireSeconds) {
        var cdn = properties.getCdn();
        if (!cdn.isEnabled() || !StringUtils.hasText(cdn.getDomain())) {
            return Optional.empty();
        }
        // 将 MinIO endpoint 替换为 CDN 域名
        try {
            String presigned = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(relativePath)
                    .expiry(expireSeconds, TimeUnit.SECONDS)
                    .build());
            // 替换 endpoint 为 CDN 域名
            String cdnDomain = cdn.getDomain().endsWith("/")
                    ? cdn.getDomain().substring(0, cdn.getDomain().length() - 1)
                    : cdn.getDomain();
            String endpointNormalized = endpoint.endsWith("/")
                    ? endpoint.substring(0, endpoint.length() - 1)
                    : endpoint;
            String cdnLink = presigned.replace(endpointNormalized, cdnDomain);
            return Optional.of(cdnLink);
        } catch (Exception e) {
            log.warn("生成 CDN URL 失败: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
