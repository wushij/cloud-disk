package com.clouddisk.storage;

import com.clouddisk.config.CloudDiskProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(CloudDiskProperties properties) {
        this.root = Path.of(properties.getStorage().getLocalRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (Exception e) {
            throw new RuntimeException("无法创建存储目录: " + root, e);
        }
    }

    @Override
    public String store(InputStream input, String relativePath, long size) throws Exception {
        Path target = resolvePath(relativePath);
        Files.createDirectories(target.getParent());
        Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return relativePath;
    }

    @Override
    public Resource loadAsResource(String relativePath) {
        try {
            Path p = resolvePath(relativePath);
            Resource resource = new UrlResource(p.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new RuntimeException("文件不存在: " + relativePath);
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败", e);
        }
    }

    @Override
    public Path resolvePath(String relativePath) {
        Path p = root.resolve(relativePath).normalize();
        if (!p.startsWith(root)) {
            throw new RuntimeException("非法路径");
        }
        return p;
    }

    @Override
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolvePath(relativePath));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void deleteByPrefix(String prefix) {
        try {
            Path dir = resolvePath(prefix);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
                }
            } else {
                delete(prefix);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean exists(String relativePath) {
        return Files.exists(resolvePath(relativePath));
    }

    @Override
    public void mergeParts(String targetRelativePath, List<String> partRelativePaths) throws Exception {
        Path target = resolvePath(targetRelativePath);
        Files.createDirectories(target.getParent());
        try (OutputStream out = Files.newOutputStream(target)) {
            for (String part : partRelativePaths) {
                try (InputStream in = Files.newInputStream(resolvePath(part))) {
                    in.transferTo(out);
                }
            }
        }
    }

    @Override
    public String bucketName() {
        return "local";
    }

    @Override
    public String storageType() {
        return "local";
    }

    @Override
    public Optional<String> presignedDownloadUrl(String relativePath, int expireSeconds) {
        return Optional.empty();
    }
}
