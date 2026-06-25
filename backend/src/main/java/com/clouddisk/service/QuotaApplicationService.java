package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clouddisk.auth.SystemRole;
import com.clouddisk.entity.QuotaApplication;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.QuotaApplicationMapper;
import com.clouddisk.mapper.UserMapper;
import com.clouddisk.cache.CacheService;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.clouddisk.common.BusinessException;
import com.clouddisk.common.UserStatus;

import java.util.*;

@Service
@RequiredArgsConstructor
public class QuotaApplicationService {

    private final QuotaApplicationMapper quotaApplicationMapper;
    private final UserMapper userMapper;
    private final NotificationDispatcher notificationDispatcher;
    private final CacheService cacheService;
    private final AuditLogService auditLogService;
    private final AdminAccessService adminAccessService;

    /**
     * 用户自主发起扩容申请
     */
    @Transactional(rollbackFor = Exception.class)
    public QuotaApplication apply(Long applyQuota, String reason) {
        long userId = StpUtil.getLoginIdAsLong();
        
        // 1. 校验是否已有未审批的申请
        Long pendingCount = quotaApplicationMapper.selectCount(
                new LambdaQueryWrapper<QuotaApplication>()
                        .eq(QuotaApplication::getUserId, userId)
                        .eq(QuotaApplication::getStatus, "PENDING")
        );
        if (pendingCount > 0) {
            throw new BusinessException("您已有未审批的扩容申请，请耐心等待管理员审批");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (SystemRole.isSuperAdmin(user.getRole())) {
            throw new BusinessException("超级管理员无需申请扩容");
        }
        if (!SystemRole.isUser(user.getRole()) && !SystemRole.isAdmin(user.getRole())) {
            throw new BusinessException("当前账号无法申请扩容");
        }

        long currentQuota = user.getStorageQuota() != null ? user.getStorageQuota() : 0L;
        if (applyQuota == null || applyQuota <= currentQuota) {
            throw new BusinessException("申请配额必须大于当前配额");
        }
        long targetQuota = applyQuota;

        // 3. 创建申请工单
        QuotaApplication qa = new QuotaApplication();
        qa.setUserId(userId);
        qa.setCurrentQuota(currentQuota);
        qa.setApplyQuota(targetQuota);
        qa.setReason(reason);
        qa.setStatus("PENDING");
        quotaApplicationMapper.insert(qa);

        // 4. 通知超级管理员审批（普通管理员的申请也仅由超级管理员处理）
        List<User> superAdmins = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, SystemRole.SUPER_ADMIN)
                        .eq(User::getStatus, UserStatus.ACTIVE)
        );
        String displayName = user.getNickname() != null ? user.getNickname() : user.getUsername();
        String content = displayName + "（" + user.getUsername() + "）申请扩容容量至 " + formatSize(targetQuota) + "，理由：" + reason;

        for (User admin : superAdmins) {
            notificationDispatcher.dispatch(admin.getId(), "QUOTA_APPLY", "容量扩容申请", content, String.valueOf(qa.getId()));
        }

        return qa;
    }

    /**
     * 管理员审批：通过
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String opinion) {
        adminAccessService.requireSuperAdmin();
        QuotaApplication qa = quotaApplicationMapper.selectById(id);
        if (qa == null) {
            throw new BusinessException("申请记录不存在");
        }
        if (!"PENDING".equals(qa.getStatus())) {
            throw new BusinessException("该申请已被处理");
        }

        User user = userMapper.selectById(qa.getUserId());
        if (user == null) {
            throw new BusinessException("申请用户不存在");
        }

        // 1. 更新用户存储配额
        user.setStorageQuota(qa.getApplyQuota());
        userMapper.updateById(user);
        cacheService.delete("user:" + user.getId());

        // 2. 更新申请单状态
        qa.setStatus("APPROVED");
        qa.setApprovalOpinion(opinion);
        quotaApplicationMapper.updateById(qa);

        // 3. 记录日志
        auditLogService.logCurrentUser("ADMIN_APPROVE_QUOTA", "quota_application", String.valueOf(id),
                "通过用户 " + user.getUsername() + " 扩容至 " + formatSize(qa.getApplyQuota()));

        // 4. 通知用户
        String userContent = "您申请将容量扩容至 " + formatSize(qa.getApplyQuota()) + " 已被管理员审核通过！" + 
                (opinion != null && !opinion.trim().isEmpty() ? " 审批意见：" + opinion : "");
        notificationDispatcher.dispatch(qa.getUserId(), "QUOTA_RESULT", "容量扩容审核已通过", userContent, String.valueOf(id));
    }

    /**
     * 管理员审批：拒绝
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String opinion) {
        adminAccessService.requireSuperAdmin();
        QuotaApplication qa = quotaApplicationMapper.selectById(id);
        if (qa == null) {
            throw new BusinessException("申请记录不存在");
        }
        if (!"PENDING".equals(qa.getStatus())) {
            throw new BusinessException("该申请已被处理");
        }

        User user = userMapper.selectById(qa.getUserId());
        if (user == null) {
            throw new BusinessException("申请用户不存在");
        }

        // 1. 更新申请单状态
        qa.setStatus("REJECTED");
        qa.setApprovalOpinion(opinion);
        quotaApplicationMapper.updateById(qa);

        // 2. 记录日志
        auditLogService.logCurrentUser("ADMIN_REJECT_QUOTA", "quota_application", String.valueOf(id),
                "拒绝用户 " + user.getUsername() + " 扩容至 " + formatSize(qa.getApplyQuota()));

        // 3. 通知用户
        String userContent = "您申请将容量扩容至 " + formatSize(qa.getApplyQuota()) + " 已被管理员拒绝。" + 
                (opinion != null && !opinion.trim().isEmpty() ? " 拒绝原因：" + opinion : "");
        notificationDispatcher.dispatch(qa.getUserId(), "QUOTA_RESULT", "容量扩容审核已被拒绝", userContent, String.valueOf(id));
    }

    /**
     * 获取用户本人的申请历史
     */
    public List<QuotaApplication> listUserHistory() {
        long userId = StpUtil.getLoginIdAsLong();
        return quotaApplicationMapper.selectList(
                new LambdaQueryWrapper<QuotaApplication>()
                        .eq(QuotaApplication::getUserId, userId)
                        .orderByDesc(QuotaApplication::getCreateTime)
        );
    }

    /**
     * 管理员获取所有申请（包含分页）
     */
    public Page<QuotaApplication> listAllApplications(int page, int size) {
        adminAccessService.requireSuperAdmin();
        return quotaApplicationMapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<QuotaApplication>()
                        .orderByDesc(QuotaApplication::getCreateTime)
        );
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0B";
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.ROOT, "%.1fMB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.ROOT, "%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
