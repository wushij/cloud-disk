package com.clouddisk.controller;

import com.clouddisk.entity.TeamMember;
import com.clouddisk.entity.TeamSpace;
import com.clouddisk.service.TeamSpaceService;
import lombok.RequiredArgsConstructor;
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
        return teamSpaceService.getSpace(id);
    }

    /** 列出团队成员 */
    @GetMapping("/{id}/members")
    public List<Map<String, Object>> listMembers(@PathVariable Long id) {
        return teamSpaceService.listMembers(id);
    }

    /** 邀请成员 */
    @PostMapping("/{id}/members")
    public TeamMember invite(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String role = body.get("role") != null ? body.get("role").toString() : "MEMBER";
        return teamSpaceService.inviteMember(id, userId, role);
    }

    /** 移除成员 */
    @DeleteMapping("/{id}/members/{userId}")
    public Map<String, String> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        teamSpaceService.removeMember(id, userId);
        return Map.of("message", "已移除成员");
    }

    /** 列出团队文件 */
    @GetMapping("/{id}/files")
    public Map<String, Object> listFiles(
            @PathVariable Long id,
            @RequestParam(required = false) Long folderId) {
        return teamSpaceService.listFiles(id, folderId);
    }
}
