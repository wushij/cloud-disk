package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clouddisk.entity.Notification;
import com.clouddisk.entity.TeamInvitation;
import com.clouddisk.mapper.NotificationMapper;
import com.clouddisk.mapper.TeamInvitationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final TeamInvitationMapper teamInvitationMapper;

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
                try {
                    Long invitationId = Long.parseLong(n.getRefId());
                    TeamInvitation invitation = teamInvitationMapper.selectById(invitationId);
                    if (invitation != null) {
                        m.put("inviteStatus", invitation.getStatus());
                    } else {
                        m.put("inviteStatus", "EXPIRED");
                    }
                } catch (Exception e) {
                    m.put("inviteStatus", "EXPIRED");
                }
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
}
