package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.AuditLog;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.AuditLogMapper;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.mapper.UserMapper;
import com.clouddisk.search.FileSearchService;
import com.clouddisk.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserMapper userMapper;
    private final FileMapper fileMapper;
    private final AuditLogMapper auditLogMapper;
    private final StorageService storageService;
    private final CloudDiskProperties properties;
    private final CacheService cacheService;
    private final AuditLogService auditLogService;
    private final StorageQuotaService quotaService;

    @Autowired(required = false)
    private FileSearchService fileSearchService;

    public void requireAdmin() {
        long userId = AuthService.currentUserId();
        User user = userMapper.selectById(userId);
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException("需要管理员权限");
        }
    }

    public Map<String, Object> dashboard() {
        requireAdmin();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userCount", userMapper.selectCount(null));
        m.put("fileCount", fileMapper.selectCount(new LambdaQueryWrapper<FileRecord>().eq(FileRecord::getStatus, 1)));
        m.put("storageType", storageService.storageType());
        m.put("bucket", storageService.bucketName());
        m.put("redisEnabled", properties.getRedis().isEnabled());
        m.put("elasticsearchEnabled", properties.getElasticsearch().isEnabled());
        m.put("rabbitmqEnabled", properties.getRabbitmq().isEnabled());
        return m;
    }

    public List<Map<String, Object>> listUsers() {
        requireAdmin();
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().orderByDesc(User::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("username", u.getUsername());
            row.put("nickname", u.getNickname());
            row.put("email", u.getEmail());
            row.put("role", u.getRole() != null ? u.getRole() : "USER");
            row.put("status", u.getStatus());
            row.put("storageQuota", u.getStorageQuota() != null ? u.getStorageQuota() : 0);
            row.put("storageUsed", u.getStorageUsed() != null ? u.getStorageUsed() : 0);
            row.put("createTime", u.getCreateTime());
            result.add(row);
        }
        return result;
    }

    public void setUserStatus(Long userId, int status) {
        requireAdmin();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if ("admin".equalsIgnoreCase(user.getUsername()) && status == 0) {
            throw new BusinessException("不能禁用超级管理员");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        cacheService.delete("user:" + userId);
        auditLogService.logCurrentUser("ADMIN_DISABLE_USER", "user", String.valueOf(userId),
                status == 1 ? "启用" : "禁用");
    }

    public void rebuildSearchIndex() {
        requireAdmin();
        if (fileSearchService == null) {
            throw new BusinessException("全文搜索未启用");
        }
        List<FileRecord> all = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>().eq(FileRecord::getStatus, 1));
        fileSearchService.rebuildIndex(all);
        auditLogService.logCurrentUser("ADMIN_ES_REBUILD", "search", "all", "count=" + all.size());
    }

    public Map<String, Object> auditLogs(int page, int size) {
        requireAdmin();
        Page<AuditLog> p = auditLogMapper.selectPage(new Page<>(page + 1, size),
                new LambdaQueryWrapper<AuditLog>().orderByDesc(AuditLog::getCreateTime));
        Map<String, Object> result = new HashMap<>();
        result.put("content", p.getRecords());
        result.put("totalElements", p.getTotal());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public void setUserQuota(Long userId, long quotaBytes) {
        requireAdmin();
        quotaService.setQuota(userId, quotaBytes);
        auditLogService.logCurrentUser("ADMIN_SET_QUOTA", "user", String.valueOf(userId),
                "quota=" + quotaBytes);
    }

    public Map<String, Object> storageStats() {
        requireAdmin();
        Map<String, Object> result = new LinkedHashMap<>();
        // 全局总用量
        Long totalUsed = fileMapper.selectList(
                new LambdaQueryWrapper<FileRecord>()
                        .select(FileRecord::getFileSize)
                        .eq(FileRecord::getStatus, 1))
                .stream()
                .mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0)
                .sum();
        result.put("totalUsedBytes", totalUsed);
        // 各用户用量排行
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .orderByDesc(User::getStorageUsed)
                        .last("LIMIT 50"));
        List<Map<String, Object>> userStats = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("username", u.getUsername());
            row.put("storageUsed", u.getStorageUsed() != null ? u.getStorageUsed() : 0);
            row.put("storageQuota", u.getStorageQuota() != null ? u.getStorageQuota() : 0);
            userStats.add(row);
        }
        result.put("userStats", userStats);
        return result;
    }
}
