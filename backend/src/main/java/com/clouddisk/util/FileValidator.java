package com.clouddisk.util;

import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FileValidator {

    private final CloudDiskProperties properties;
    private final Set<String> allowedExt;

    public FileValidator(CloudDiskProperties properties) {
        this.properties = properties;
        this.allowedExt = Arrays.stream(properties.getUpload().getAllowedExtensions().split(","))
                .map(String::trim)
                .map(String::toLowerCase)
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
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return;
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        if (!allowedExt.contains(ext)) {
            throw new BusinessException("不支持的文件类型: ." + ext);
        }
    }
}
