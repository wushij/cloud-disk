package com.clouddisk.util;

import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FileValidator {

    private final CloudDiskProperties properties;
    private final Set<String> allowedExt;
    private final Set<String> blockedExt;
    private final boolean allowAll;

    public FileValidator(CloudDiskProperties properties) {
        this.properties = properties;
        this.allowedExt = parseSet(properties.getUpload().getAllowedExtensions());
        this.blockedExt = parseSet(properties.getUpload().getBlockedExtensions());
        this.allowAll = allowedExt.contains("*");
    }

    private static Set<String> parseSet(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public void validate(String fileName, long size) {
        if (size <= 0) {
            throw new BusinessException("文件为空");
        }
        if (size > properties.getUpload().getMaxFileSize()) {
            throw new BusinessException("文件超过大小限制（最大 20GB）");
        }
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("文件名无效");
        }
        String ext = FileTypeUtils.extensionOf(fileName);
        if (ext.isEmpty()) {
            return;
        }
        if (blockedExt.contains(ext)) {
            throw new BusinessException("不允许上传该类型文件: ." + ext);
        }
        if (allowAll || allowedExt.isEmpty()) {
            return;
        }
        if (!allowedExt.contains(ext)) {
            throw new BusinessException("不支持的文件类型: ." + ext);
        }
    }
}
