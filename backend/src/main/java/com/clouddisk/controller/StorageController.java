package com.clouddisk.controller;

import com.clouddisk.cache.CacheService;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.service.AuthService;
import com.clouddisk.service.StorageQuotaService;
import com.clouddisk.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final CloudDiskProperties properties;
    private final StorageQuotaService quotaService;
    private final CacheService cacheService;

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", storageService.storageType());
        m.put("bucket", storageService.bucketName());
        m.put("endpoint", properties.getMinio().getEndpoint());
        m.put("healthy", true);
        return m;
    }

    /** 当前用户的存储用量 */
    @GetMapping("/usage")
    public Map<String, Object> usage() {
        return quotaService.getUsage(AuthService.currentUserId());
    }

    /** 缓存统计信息 */
    @GetMapping("/cache-stats")
    public Map<String, Object> cacheStats() {
        return cacheService.stats();
    }
}
