package com.clouddisk.service;

import com.clouddisk.entity.Notification;
import com.clouddisk.mq.MediaMessageProducer;
import com.clouddisk.mq.NotificationMessage;
import com.clouddisk.websocket.UploadProgressHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 统一通知：MQ 开启时走队列；否则落库 + WebSocket 实时推送。
 */
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationService notificationService;
    private final UploadProgressHandler progressHandler;

    @Autowired(required = false)
    private MediaMessageProducer notificationProducer;

    public void dispatch(Long userId, String type, String title, String content, String refId) {
        if (userId == null) return;
        if (notificationProducer != null) {
            notificationProducer.sendNotification(new NotificationMessage(userId, type, title, content, refId));
            return;
        }
        Notification saved = notificationService.save(userId, type, title, content, refId);
        Map<String, String> statuses = notificationService.resolveActionStatuses(type, refId);
        progressHandler.sendNotificationWithStatuses(
                userId, type, title, content, refId, saved.getId(), statuses);
    }
}
