package com.clouddisk.websocket;



import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import org.springframework.web.socket.CloseStatus;

import org.springframework.web.socket.TextMessage;

import org.springframework.web.socket.WebSocketSession;

import org.springframework.web.socket.handler.TextWebSocketHandler;



import java.util.LinkedHashMap;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;



@Component

@RequiredArgsConstructor

public class UploadProgressHandler extends TextWebSocketHandler {



    private final ObjectMapper objectMapper;

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();



    @Override

    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        Object uid = session.getAttributes().get("userId");

        if (uid != null) {

            sessions.put(Long.parseLong(uid.toString()), session);

        }

    }



    @Override

    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        Object uid = session.getAttributes().get("userId");

        if (uid != null) {

            sessions.remove(Long.parseLong(uid.toString()));

        }

    }



    public void sendProgress(long userId, String taskId, String fileName, double progress, String status) {

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("type", "upload_progress");

        payload.put("taskId", taskId);

        payload.put("fileName", fileName);

        payload.put("progress", progress);

        payload.put("status", status);

        send(userId, payload);

    }



    public void sendNotification(long userId, String notifyType, String title, String content, String refId) {
        sendNotification(userId, notifyType, title, content, refId, null);
    }

    public void sendNotification(long userId, String notifyType, String title, String content, String refId, Long notifyId) {
        sendNotificationWithStatuses(userId, notifyType, title, content, refId, notifyId, null);
    }

    public void sendNotificationWithStatuses(long userId, String notifyType, String title, String content,
                                           String refId, Long notifyId, Map<String, String> actionStatuses) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "notification");
        payload.put("notifyType", notifyType);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("refId", refId);
        if (notifyId != null) {
            payload.put("notifyId", notifyId);
        }
        if (actionStatuses != null) {
            if (actionStatuses.containsKey("inviteStatus")) {
                payload.put("inviteStatus", actionStatuses.get("inviteStatus"));
            }
            if (actionStatuses.containsKey("registrationStatus")) {
                payload.put("registrationStatus", actionStatuses.get("registrationStatus"));
            }
        }
        send(userId, payload);
    }



    private void send(long userId, Map<String, Object> payload) {

        WebSocketSession session = sessions.get(userId);

        if (session == null || !session.isOpen()) return;

        try {

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));

        } catch (Exception ignored) {

        }

    }

}

