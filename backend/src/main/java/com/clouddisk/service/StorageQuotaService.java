package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存储配额管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageQuotaService {

    private static final long QUOTA_CACHE_TTL = 60; // 用量缓存 60 秒

    private final UserMapper userMapper;
    private final FileMapper fileMapper;
    private final CacheService cacheService;

    /**
     * 获取用户存储用量信息
     */
    public Map<String, Object> getUsage(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        long usedBytes = getUsedBytes(userId);
        long quotaBytes = user.getStorageQuota() != null ? user.getStorageQuota() : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("usedBytes", usedBytes);
        result.put("quotaBytes", quotaBytes);
        result.put("usedFormatted", formatSize(usedBytes));
        result.put("quotaFormatted", formatSize(quotaBytes));
        if (quotaBytes > 0) {
            double percent = Math.round(usedBytes * 10000.0 / quotaBytes) / 100.0;
            result.put("usedPercent", Math.min(percent, 100.0));
        } else {
            result.put("usedPercent", 0.0);
        }
        return result;
    }

    /**
     * 上传前校验配额是否充足
     *
     * @throws BusinessException 配额不足时抛出
     */
    public void checkQuota(long userId, long additionalBytes) {
        User user = userMapper.selectById(userId);
        if (user == null) return;
        long quotaBytes = user.getStorageQuota() != null ? user.getStorageQuota() : 0;
        if (quotaBytes <= 0) return; // 0 = 不限

        long usedBytes = getUsedBytes(userId);
        if (usedBytes + additionalBytes > quotaBytes) {
            throw new BusinessException("存储空间不足，已用 " + formatSize(usedBytes)
                    + "，配额 " + formatSize(quotaBytes)
                    + "，需要 " + formatSize(additionalBytes));
        }
    }

    /**
     * 上传成功后累加用量
     */
    public void addUsage(long userId, long bytes) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("storage_used = COALESCE(storage_used, 0) + " + bytes));
        evictUsageCache(userId);
    }

    /**
     * 删除文件后扣减用量
     */
    public void subtractUsage(long userId, long bytes) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("storage_used = GREATEST(0, COALESCE(storage_used, 0) - " + bytes + ")"));
        evictUsageCache(userId);
    }

    /**
     * 全量重算用户存储用量
     */
    public void recalculateUsage(long userId) {
        long total = fileMapper.selectList(
                new LambdaQueryWrapper<FileRecord>()
                        .select(FileRecord::getFileSize)
                        .eq(FileRecord::getUserId, userId)
                        .eq(FileRecord::getStatus, 1))
                .stream()
                .mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0)
                .sum();

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStorageUsed, total));
        evictUsageCache(userId);
        log.info("重算用户用量 userId={}, total={}", userId, total);
    }

    /**
     * 管理员设置用户配额
     */
    public void setQuota(long userId, long quotaBytes) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        user.setStorageQuota(quotaBytes);
        userMapper.updateById(user);
        evictUsageCache(userId);
    }

    // ==================== 内部方法 ====================

    private long getUsedBytes(long userId) {
        String cacheKey = "quota:used:" + userId;
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            try {
                return Long.parseLong(cached);
            } catch (NumberFormatException ignored) {
            }
        }
        long used = sumActiveFileBytes(userId);
        User user = userMapper.selectById(userId);
        if (user != null) {
            Long stored = user.getStorageUsed();
            if (stored == null || stored != used) {
                userMapper.update(null, new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getStorageUsed, used));
            }
        }
        cacheService.set(cacheKey, String.valueOf(used), QUOTA_CACHE_TTL);
        return used;
    }

    private long sumActiveFileBytes(long userId) {
        return fileMapper.selectList(
                        new LambdaQueryWrapper<FileRecord>()
                                .select(FileRecord::getFileSize)
                                .eq(FileRecord::getUserId, userId)
                                .eq(FileRecord::getStatus, 1))
                .stream()
                .mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0)
                .sum();
    }

    private void evictUsageCache(long userId) {
        cacheService.delete("quota:used:" + userId);
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024);
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.2f GB", bytes / 1024.0 / 1024 / 1024);
        return String.format("%.2f TB", bytes / 1024.0 / 1024 / 1024 / 1024);
    }
}
