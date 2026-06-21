package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.common.UserStatus;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.AuditLog;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.AuditLogMapper;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.mapper.UserMapper;
import com.clouddisk.search.FileSearchService;
import com.clouddisk.storage.StorageService;
import com.clouddisk.util.AuthHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private final PasswordEncoder passwordEncoder;

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
        long userCount = userMapper.selectCount(null);
        long fileCount = fileMapper.selectCount(new LambdaQueryWrapper<FileRecord>().eq(FileRecord::getStatus, 1));
        long pendingUserCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, UserStatus.PENDING));
        m.put("userCount", userCount);
        m.put("pendingUserCount", pendingUserCount);
        m.put("fileCount", fileCount);
        m.put("storageType", storageService.storageType());
        m.put("bucket", storageService.bucketName());
        m.put("totalUsedBytes", sumActiveFileBytes());
        m.put("redisEnabled", properties.getRedis().isEnabled());
        m.put("elasticsearchEnabled", properties.getElasticsearch().isEnabled());
        m.put("rabbitmqEnabled", properties.getRabbitmq().isEnabled());
        return m;
    }

    private long sumActiveFileBytes() {
        long total = 0L;
        List<Map<String, Object>> sumResult = fileMapper.selectMaps(
                new LambdaQueryWrapper<FileRecord>()
                        .select(FileRecord::getFileSize)
                        .eq(FileRecord::getStatus, 1));
        for (Map<String, Object> row : sumResult) {
            Object sizeObj = row.get("file_size");
            if (sizeObj instanceof Number) {
                total += ((Number) sizeObj).longValue();
            }
        }
        return total;
    }

    public List<Map<String, Object>> listUsers() {
        return listUsers(0, Integer.MAX_VALUE);
    }

    public List<Map<String, Object>> listUsers(int page, int size) {
        requireAdmin();
        Page<User> p = userMapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreateTime));
        List<User> records = new ArrayList<>(p.getRecords());
        records.sort((a, b) -> {
            boolean aAdmin = "ADMIN".equalsIgnoreCase(a.getRole());
            boolean bAdmin = "ADMIN".equalsIgnoreCase(b.getRole());
            if (aAdmin != bAdmin) {
                return aAdmin ? -1 : 1;
            }
            if (a.getCreateTime() != null && b.getCreateTime() != null) {
                return b.getCreateTime().compareTo(a.getCreateTime());
            }
            return Long.compare(b.getId() != null ? b.getId() : 0L, a.getId() != null ? a.getId() : 0L);
        });
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : records) {
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
            row.put("hasAvatar", u.getAvatar() != null && !u.getAvatar().isBlank());
            result.add(row);
        }
        return result;
    }

    public ResponseEntity<Resource> loadUserAvatar(Long userId, jakarta.servlet.http.HttpServletRequest request) {
        long callerId = AuthHelper.requireUserId(request);
        User caller = userMapper.selectById(callerId);
        if (caller == null || !"ADMIN".equalsIgnoreCase(caller.getRole())) {
            throw new BusinessException("需要管理员权限");
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getAvatar() == null || user.getAvatar().isBlank()) {
            throw new BusinessException("头像不存在");
        }
        Resource resource = storageService.loadAsResource(user.getAvatar());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(resource);
    }

    public void setUserStatus(Long userId, int status) {
        requireAdmin();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if ("admin".equalsIgnoreCase(user.getUsername()) && status == UserStatus.DISABLED) {
            throw new BusinessException("不能禁用超级管理员");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        cacheService.delete("user:" + userId);
        if (status == UserStatus.DISABLED) {
            StpUtil.logout(userId);
        }
        auditLogService.logCurrentUser("ADMIN_DISABLE_USER", "user", String.valueOf(userId),
                status == UserStatus.ACTIVE ? "启用" : (status == UserStatus.PENDING ? "待审核" : "禁用"));
    }

    public void approveRegistration(Long userId) {
        requireAdmin();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (user.getStatus() == null || user.getStatus() != UserStatus.PENDING) {
            throw new BusinessException("该注册申请已处理");
        }
        user.setStatus(UserStatus.ACTIVE);
        if (user.getStorageQuota() == null || user.getStorageQuota() <= 0) {
            user.setStorageQuota(UserStatus.DEFAULT_QUOTA_BYTES);
        }
        userMapper.updateById(user);
        cacheService.delete("user:" + userId);
        auditLogService.logCurrentUser("ADMIN_APPROVE_REGISTER", "user", String.valueOf(userId), user.getUsername());
    }

    public void rejectRegistration(Long userId) {
        requireAdmin();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (user.getStatus() == null || user.getStatus() != UserStatus.PENDING) {
            throw new BusinessException("该注册申请已处理");
        }
        String username = user.getUsername();
        userMapper.deleteById(userId);
        cacheService.delete("user:" + userId);
        auditLogService.logCurrentUser("ADMIN_REJECT_REGISTER", "user", String.valueOf(userId), username);
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
        // 使用 selectMaps 只映射 file_size 列，避免全量 FileRecord 加载到内存
        // 更优方案：使用 MyBatis XML 或 @Select 注解写 SELECT SUM(file_size) SQL
        Long totalUsed = 0L;
        List<Map<String, Object>> sumResult = fileMapper.selectMaps(
                new LambdaQueryWrapper<FileRecord>()
                        .select(FileRecord::getFileSize)
                        .eq(FileRecord::getStatus, 1));
        for (Map<String, Object> row : sumResult) {
            Object sizeObj = row.get("file_size");
            if (sizeObj instanceof Number) {
                totalUsed += ((Number) sizeObj).longValue();
            }
        }
        result.put("totalUsedBytes", totalUsed);
        List<User> users = userMapper.selectPage(
                new Page<>(1, 50),
                new LambdaQueryWrapper<User>()
                        .orderByDesc(User::getStorageUsed))
                .getRecords();
        List<Map<String, Object>> userStats = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("username", u.getUsername());
            row.put("nickname", u.getNickname());
            row.put("hasAvatar", u.getAvatar() != null && !u.getAvatar().isBlank());
            row.put("storageUsed", u.getStorageUsed() != null ? u.getStorageUsed() : 0);
            row.put("storageQuota", u.getStorageQuota() != null ? u.getStorageQuota() : 0);
            userStats.add(row);
        }
        result.put("userStats", userStats);
        return result;
    }

    /** 修改用户角色 */
    public void setUserRole(Long userId, String role) {
        requireAdmin();
        if (!"ADMIN".equalsIgnoreCase(role) && !"USER".equalsIgnoreCase(role)) {
            throw new BusinessException("非法的角色值，必须为 ADMIN 或 USER");
        }
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            throw new BusinessException("不能修改超级管理员的角色");
        }
        user.setRole(role.toUpperCase());
        userMapper.updateById(user);
        cacheService.delete("user:" + userId);
        auditLogService.logCurrentUser("ADMIN_SET_ROLE", "user", String.valueOf(userId), role.toUpperCase());
    }

    /** 重置用户密码 */
    public void resetUserPassword(Long userId, String newPassword) {
        requireAdmin();
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException("密码不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        cacheService.delete("user:" + userId);
        StpUtil.logout(userId); // 重置密码后强制下线
        auditLogService.logCurrentUser("ADMIN_RESET_PASSWORD", "user", String.valueOf(userId), "重置密码");
    }
}
