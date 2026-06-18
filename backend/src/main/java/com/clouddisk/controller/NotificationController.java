package com.clouddisk.controller;

import com.clouddisk.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 获取通知列表 */
    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.listNotifications(page, size);
    }

    /** 获取未读数量 */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount());
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public Map<String, String> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Map.of("message", "已标记为已读");
    }

    /** 标记全部已读 */
    @PutMapping("/read-all")
    public Map<String, String> markAllRead() {
        notificationService.markAllRead();
        return Map.of("message", "已全部标记为已读");
    }
}
