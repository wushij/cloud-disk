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

    /** 通过新用户注册申请 */
    @PostMapping("/registrations/{userId}/approve")
    public Map<String, String> approveRegistration(@PathVariable Long userId) {
        adminService.approveRegistration(userId);
        return Map.of("message", "已通过注册申请");
    }

    /** 拒绝新用户注册申请 */
    @PostMapping("/registrations/{userId}/reject")
    public Map<String, String> rejectRegistration(@PathVariable Long userId) {
        adminService.rejectRegistration(userId);
        return Map.of("message", "已拒绝注册申请");
    }

    /** 全局存储用量统计 */
    @GetMapping("/storage/stats")
    public Map<String, Object> storageStats() {
        return adminService.storageStats();
    }

    /** 修改用户角色 */
    @PutMapping("/users/{id}/role")
    public Map<String, String> setUserRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null) throw new com.clouddisk.common.BusinessException("缺少 role 参数");
        adminService.setUserRole(id, role);
        return Map.of("message", "ok");
    }

    /** 重置用户密码 */
    @PutMapping("/users/{id}/password")
    public Map<String, String> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null) throw new com.clouddisk.common.BusinessException("缺少 password 参数");
        adminService.resetUserPassword(id, password);
        return Map.of("message", "ok");
    }
}