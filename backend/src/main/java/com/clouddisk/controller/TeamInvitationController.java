package com.clouddisk.controller;

import com.clouddisk.service.TeamSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/team-invitations")
@RequiredArgsConstructor
public class TeamInvitationController {

    private final TeamSpaceService teamSpaceService;

    /** 接受团队邀请 */
    @PostMapping("/{id}/accept")
    public Map<String, String> accept(@PathVariable Long id) {
        teamSpaceService.acceptInvitation(id);
        return Map.of("message", "已加入团队");
    }

    /** 拒绝团队邀请 */
    @PostMapping("/{id}/reject")
    public Map<String, String> reject(@PathVariable Long id) {
        teamSpaceService.rejectInvitation(id);
        return Map.of("message", "已拒绝邀请");
    }
}
