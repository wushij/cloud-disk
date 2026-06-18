package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.clouddisk.entity.Notification;
import com.clouddisk.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    /**
     * 获取当前用户的通知列表
     */
    public List<Map<String, Object>> listNotifications(int page, int size) {
        long userId = AuthService.currentUserId();
        List<Notification> list = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime)
                        .last("LIMIT " + size + " OFFSET " + (page * size)));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification n : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("refId", n.getRefId());
            m.put("isRead", n.getIsRead());
            m.put("createdAt", n.getCreateTime());
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
}
