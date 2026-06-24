package com.clouddisk.controller;

import com.clouddisk.entity.TeamSpace;
import com.clouddisk.service.TeamSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamSpaceController {

    private final TeamSpaceService teamSpaceService;

    /** 创建团队空间 */
    @PostMapping
    public TeamSpace create(@RequestBody Map<String, String> body) {
        return teamSpaceService.create(body.get("name"));
    }

    /** 列出我的团队 */
    @GetMapping
    public List<Map<String, Object>> list() {
        return teamSpaceService.listSpaces();
    }

    /** 获取团队详情 */
    @GetMapping("/{id}")
    public TeamSpace detail(@PathVariable Long id) {
        return teamSpaceService.getDetailForMember(id);
    }

    /** 重命名团队空间 */
    @PutMapping("/{id}")
    public TeamSpace rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return teamSpaceService.renameSpace(id, body.get("name"));
    }

    /** 列出团队成员 */
    @GetMapping("/{id}/members")
    public List<Map<String, Object>> listMembers(@PathVariable Long id) {
        return teamSpaceService.listMembers(id);
    }

    /** 邀请成员（仅支持用户名，需对方确认） */
    @PostMapping("/{id}/members")
    public Map<String, Object> invite(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String role = body.get("role") != null ? body.get("role").toString() : "MEMBER";
        if (body.get("username") == null) {
            throw new com.clouddisk.common.BusinessException("请输入用户名");
        }
        String username = body.get("username").toString().trim();
        if (username.isEmpty()) {
            throw new com.clouddisk.common.BusinessException("请输入用户名");
        }
        return teamSpaceService.inviteMemberByUsername(id, username, role);
    }

    /** 团队成员头像（同团队成员可查看） */
    @GetMapping("/{id}/members/{userId}/avatar")
    public ResponseEntity<Resource> memberAvatar(
            @PathVariable Long id,
            @PathVariable Long userId,
            jakarta.servlet.http.HttpServletRequest request) {
        return teamSpaceService.loadMemberAvatar(id, userId, request);
    }

    /** 移除成员 */
    @DeleteMapping("/{id}/members/{userId}")
    public Map<String, String> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        teamSpaceService.removeMember(id, userId);
        return Map.of("message", "已移除成员");
    }

    /** 调整成员角色（MEMBER / ADMIN / VIEWER） */
    @PutMapping("/{id}/members/{userId}/role")
    public Map<String, String> updateMemberRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || role.isBlank()) {
            throw new com.clouddisk.common.BusinessException("缺少 role 参数");
        }
        teamSpaceService.updateMemberRole(id, userId, role);
        return Map.of("message", "角色已更新");
    }

    /** 设置团队存储配额（字节，0=不限） */
    @PutMapping("/{id}/quota")
    public TeamSpace updateQuota(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("maxSize");
        if (raw == null) {
            throw new com.clouddisk.common.BusinessException("缺少 maxSize 参数");
        }
        long maxSize = Long.parseLong(raw.toString());
        return teamSpaceService.updateQuota(id, maxSize);
    }

    /** 列出团队文件 */
    @GetMapping("/{id}/files")
    public Map<String, Object> listFiles(
            @PathVariable Long id,
            @RequestParam(required = false) Long folderId) {
        return teamSpaceService.listFiles(id, folderId);
    }

    /** 解散/删除团队空间 */
    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        teamSpaceService.deleteSpace(id);
        return Map.of("message", "已解散并删除团队空间");
    }

    /** 退出团队空间 */
    @PostMapping("/{id}/leave")
    public Map<String, String> leave(@PathVariable Long id) {
        teamSpaceService.leaveSpace(id);
        return Map.of("message", "已退出团队空间");
    }

    /** 上传团队头像 */
    @PostMapping("/{id}/avatar")
    public Map<String, String> uploadAvatar(
            @PathVariable Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String path = teamSpaceService.uploadAvatar(id, file);
        return Map.of("avatar", path);
    }

    /** 获取团队头像 */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<Resource> getAvatar(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest request) {
        return teamSpaceService.loadTeamAvatar(id, request);
    }
}
