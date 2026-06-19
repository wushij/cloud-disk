package com.clouddisk.controller;

import com.clouddisk.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return adminService.dashboard();
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return adminService.listUsers();
    }

    @GetMapping("/users/{id}/avatar")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> userAvatar(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest request) {
        return adminService.loadUserAvatar(id, request);
    }

    @PutMapping("/users/{id}/status")
    public Map<String, String> setUserStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) throw new com.clouddisk.common.BusinessException("缺少 status 参数");
        adminService.setUserStatus(id, status);
        return Map.of("message", "ok");
    }

    @PostMapping("/search/rebuild")
    public Map<String, String> rebuildSearch() {
        adminService.rebuildSearchIndex();
        return Map.of("message", "索引重建已提交");
    }

    @GetMapping("/audit-logs")
    public Map<String, Object> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.auditLogs(page, size);
    }

    /** 设置用户存储配额 */
    @PutMapping("/users/{id}/quota")
    public Map<String, String> setQuota(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long quota = body.get("storageQuota");
        if (quota == null) throw new com.clouddisk.common.BusinessException("缺少 storageQuota 参数");
        adminService.setUserQuota(id, quota);
        return Map.of("message", "ok");
    }

    /** 全局存储用量统计 */
    @GetMapping("/storage/stats")
    public Map<String, Object> storageStats() {
        return adminService.storageStats();
    }
}