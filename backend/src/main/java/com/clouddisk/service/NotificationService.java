package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clouddisk.entity.Notification;
import com.clouddisk.entity.TeamInvitation;
import com.clouddisk.entity.User;
import com.clouddisk.common.UserStatus;
import com.clouddisk.entity.QuotaApplication;
import com.clouddisk.mapper.NotificationMapper;
import com.clouddisk.mapper.QuotaApplicationMapper;
import com.clouddisk.mapper.TeamInvitationMapper;
import com.clouddisk.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final TeamInvitationMapper teamInvitationMapper;
    private final UserMapper userMapper;
    private final QuotaApplicationMapper quotaApplicationMapper;

    /**
     * 获取当前用户的通知列表（使用 MyBatis-Plus Page 分页，避免 SQL 注入）
     */
    public List<Map<String, Object>> listNotifications(int page, int size) {
        long userId = AuthService.currentUserId();
        Page<Notification> p = notificationMapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification n : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("refId", n.getRefId());
            m.put("isRead", n.getIsRead());
            m.put("createdAt", n.getCreateTime());
            
            if ("TEAM_INVITED".equals(n.getType()) && n.getRefId() != null) {
                m.put("inviteStatus", resolveInviteStatus(n.getRefId()));
            }

            if ("USER_REGISTER".equals(n.getType()) && n.getRefId() != null) {
                m.put("registrationStatus", resolveRegistrationStatus(n.getRefId()));
            }

            if ("QUOTA_APPLY".equals(n.getType()) && n.getRefId() != null) {
                m.put("quotaStatus", resolveQuotaStatus(n.getRefId()));
            }
            
            result.add(m);
        }
        return result;
    }

    /**
     * 获取未读通知数量
     */
    public long unreadCount() {
        long userId = AuthService.currentUserId();
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
    }

    /**
     * 标记通知为已读
     */
    public void markRead(Long notificationId) {
        long userId = AuthService.currentUserId();
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getId, notificationId)
                        .eq(Notification::getUserId, userId)
                        .set(Notification::getIsRead, 1));
    }

    /**
     * 标记所有通知为已读
     */
    public void markAllRead() {
        long userId = AuthService.currentUserId();
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .set(Notification::getIsRead, 1));
    }

    /**
     * 持久化通知（供 MQ 消费者调用）
     */
    public Notification save(Long userId, String type, String title, String content, String refId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefId(refId);
        n.setIsRead(0);
        notificationMapper.insert(n);
        return n;
    }

    /**
     * 删除单条通知
     */
    public void deleteNotification(Long notificationId) {
        long userId = AuthService.currentUserId();
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId));
    }

    /**
     * 清空当前用户的所有通知
     */
    public void clearAllNotifications() {
        long userId = AuthService.currentUserId();
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId));
    }

    private String resolveInviteStatus(String refId) {
        try {
            Long invitationId = Long.parseLong(refId);
            TeamInvitation invitation = teamInvitationMapper.selectById(invitationId);
            if (invitation != null) {
                return invitation.getStatus();
            }
            return "EXPIRED";
        } catch (Exception e) {
            return "EXPIRED";
        }
    }

    private String resolveRegistrationStatus(String refId) {
        try {
            User user = userMapper.selectById(Long.parseLong(refId));
            if (user == null) {
                return "REJECTED";
            }
            if (user.getStatus() != null && user.getStatus() == UserStatus.PENDING) {
                return "PENDING";
            }
            if (user.getStatus() != null && user.getStatus() == UserStatus.ACTIVE) {
                return "APPROVED";
            }
            if (user.getStatus() == null) {
                return "PENDING";
            }
            return "REJECTED";
        } catch (Exception e) {
            return "PENDING";
        }
    }

    private String resolveQuotaStatus(String refId) {
        try {
            Long appId = Long.parseLong(refId);
            QuotaApplication qa = quotaApplicationMapper.selectById(appId);
            if (qa != null) {
                return qa.getStatus();
            }
            return "REJECTED";
        } catch (Exception e) {
            return "PENDING";
        }
    }

    /** 供 WebSocket 推送时附带可操作状态 */
    public Map<String, String> resolveActionStatuses(String type, String refId) {
        Map<String, String> statuses = new LinkedHashMap<>();
        if ("TEAM_INVITED".equals(type) && refId != null) {
            statuses.put("inviteStatus", resolveInviteStatus(refId));
        }
        if ("USER_REGISTER".equals(type) && refId != null) {
            statuses.put("registrationStatus", resolveRegistrationStatus(refId));
        }
        if ("QUOTA_APPLY".equals(type) && refId != null) {
            statuses.put("quotaStatus", resolveQuotaStatus(refId));
        }
        return statuses;
    }
}

