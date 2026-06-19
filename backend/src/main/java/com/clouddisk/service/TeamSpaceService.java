package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.clouddisk.common.BusinessException;
import com.clouddisk.entity.*;
import com.clouddisk.mapper.*;
import com.clouddisk.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamSpaceService {

    private final TeamSpaceMapper teamSpaceMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamInvitationMapper teamInvitationMapper;
    private final FolderMapper folderMapper;
    private final FileMapper fileMapper;
    private final UserMapper userMapper;
    private final StorageService storageService;
    private final NotificationDispatcher notificationDispatcher;
    private final FolderTreeHelper folderTreeHelper;

    // ==================== 团队空间 CRUD ====================

    /**
     * 创建团队空间
     */
    public TeamSpace create(String name) {
        long userId = AuthService.currentUserId();
        if (name == null || name.isBlank()) {
            throw new BusinessException("团队名称不能为空");
        }
        name = name.trim();

        // 为团队创建根文件夹（挂在系统根下，owner 为创建者）
        Folder rootFolder = new Folder();
        rootFolder.setUserId(userId);
        rootFolder.setParentId(0L);
        rootFolder.setFolderName("[团队] " + name);
        rootFolder.setDeleted(0);
        folderMapper.insert(rootFolder);

        TeamSpace space = new TeamSpace();
        space.setName(name);
        space.setOwnerId(userId);
        space.setRootFolderId(rootFolder.getId());
        space.setMaxSize(0L);
        space.setStatus(1);
        teamSpaceMapper.insert(space);

        // 自动将创建者添加为 OWNER 成员
        addMember(space.getId(), userId, "OWNER");

        return space;
    }

    /**
     * 列出当前用户所在的所有团队空间
     */
    public List<Map<String, Object>> listSpaces() {
        long userId = AuthService.currentUserId();
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId));
        if (memberships.isEmpty()) return List.of();

        List<Long> spaceIds = memberships.stream().map(TeamMember::getSpaceId).collect(Collectors.toList());
        List<TeamSpace> spaces = teamSpaceMapper.selectList(
                new LambdaQueryWrapper<TeamSpace>().in(TeamSpace::getId, spaceIds).eq(TeamSpace::getStatus, 1));

        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamSpace space : spaces) {
            TeamMember member = memberships.stream()
                    .filter(m -> m.getSpaceId().equals(space.getId())).findFirst().orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", space.getId());
            m.put("name", space.getName());
            m.put("ownerId", space.getOwnerId());
            m.put("rootFolderId", space.getRootFolderId());
            m.put("maxSize", space.getMaxSize());
            m.put("myRole", member != null ? member.getRole() : "MEMBER");
            m.put("memberCount", teamMemberMapper.selectCount(
                    new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getSpaceId, space.getId())));
            m.put("createdAt", space.getCreateTime());
            result.add(m);
        }
        return result;
    }

    /**
     * 获取团队空间详情
     */
    public TeamSpace getSpace(Long spaceId) {
        TeamSpace space = teamSpaceMapper.selectById(spaceId);
        if (space == null || space.getStatus() != 1) {
            throw new BusinessException("团队空间不存在");
        }
        return space;
    }

    /**
     * 获取团队空间详情（验证当前用户为成员）
     */
    public TeamSpace getOwnedSpace(Long spaceId, long userId) {
        TeamSpace space = getSpace(spaceId);
        TeamMember member = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getSpaceId, spaceId)
                        .eq(TeamMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException("你不是该团队的成员");
        }
        return space;
    }

    /**
     * 重命名团队空间（同步更新云盘根目录文件夹名称）
     */
    public TeamSpace renameSpace(Long spaceId, String name) {
        long userId = AuthService.currentUserId();
        requireAdmin(spaceId, userId);
        if (name == null || name.isBlank()) {
            throw new BusinessException("团队名称不能为空");
        }
        name = name.trim();
        if (name.length() > 64) {
            throw new BusinessException("团队名称不能超过64个字符");
        }

        TeamSpace space = getOwnedSpace(spaceId, userId);
        space.setName(name);
        teamSpaceMapper.updateById(space);

        if (space.getRootFolderId() != null) {
            Folder root = folderMapper.selectById(space.getRootFolderId());
            if (root != null && root.getDeleted() == 0) {
                root.setFolderName("[团队] " + name);
                folderMapper.updateById(root);
            }
        }
        return space;
    }

    /** 获取团队详情（当前登录成员） */
    public TeamSpace getDetailForMember(Long spaceId) {
        return getOwnedSpace(spaceId, AuthService.currentUserId());
    }

    // ==================== 成员管理 ====================

    /**
     * 按用户名邀请成员（需对方确认后才加入）
     */
    public Map<String, Object> inviteMemberByUsername(Long spaceId, String username, String role) {
        long currentUserId = AuthService.currentUserId();
        TeamSpace space = getOwnedSpace(spaceId, currentUserId);
        requireAdmin(spaceId, currentUserId);

        User invitee = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (invitee == null) {
            throw new BusinessException("用户不存在");
        }
        if (Objects.equals(invitee.getId(), currentUserId)) {
            throw new BusinessException("不能邀请自己");
        }

        TeamMember existing = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getSpaceId, spaceId)
                        .eq(TeamMember::getUserId, invitee.getId()));
        if (existing != null) {
            throw new BusinessException("该用户已是团队成员");
        }

        TeamInvitation pending = teamInvitationMapper.selectOne(
                new LambdaQueryWrapper<TeamInvitation>()
                        .eq(TeamInvitation::getSpaceId, spaceId)
                        .eq(TeamInvitation::getInviteeId, invitee.getId())
                        .eq(TeamInvitation::getStatus, "PENDING"));
        if (pending != null) {
            throw new BusinessException("已向该用户发送邀请，等待对方确认");
        }

        if (role == null || role.isBlank()) role = "MEMBER";

        TeamInvitation invitation = new TeamInvitation();
        invitation.setSpaceId(spaceId);
        invitation.setInviterId(currentUserId);
        invitation.setInviteeId(invitee.getId());
        invitation.setRole(role);
        invitation.setStatus("PENDING");
        teamInvitationMapper.insert(invitation);

        User inviter = userMapper.selectById(currentUserId);
        String inviterName = inviter != null && StringUtils.hasText(inviter.getNickname())
                ? inviter.getNickname()
                : (inviter != null ? inviter.getUsername() : "管理员");

        sendNotification(invitee.getId(), "TEAM_INVITED", "团队邀请",
                inviterName + " 邀请你加入团队「" + space.getName() + "」，请确认是否接受",
                String.valueOf(invitation.getId()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", invitation.getId());
        result.put("spaceId", spaceId);
        result.put("username", username);
        result.put("status", "PENDING");
        return result;
    }

    /**
     * 接受团队邀请
     */
    public void acceptInvitation(Long invitationId) {
        long currentUserId = AuthService.currentUserId();
        TeamInvitation invitation = teamInvitationMapper.selectById(invitationId);
        if (invitation == null) {
            throw new BusinessException("邀请不存在");
        }
        if (!Objects.equals(invitation.getInviteeId(), currentUserId)) {
            throw new BusinessException("无权操作此邀请");
        }
        if (!"PENDING".equals(invitation.getStatus())) {
            throw new BusinessException("邀请已处理");
        }

        TeamSpace space = getSpace(invitation.getSpaceId());
        if (space.getStatus() != 1) {
            throw new BusinessException("团队空间已停用");
        }

        TeamMember existing = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getSpaceId, invitation.getSpaceId())
                        .eq(TeamMember::getUserId, currentUserId));
        if (existing != null) {
            invitation.setStatus("ACCEPTED");
            teamInvitationMapper.updateById(invitation);
            return;
        }

        addMember(invitation.getSpaceId(), currentUserId, invitation.getRole());
        invitation.setStatus("ACCEPTED");
        teamInvitationMapper.updateById(invitation);
    }

    /**
     * 拒绝团队邀请
     */
    public void rejectInvitation(Long invitationId) {
        long currentUserId = AuthService.currentUserId();
        TeamInvitation invitation = teamInvitationMapper.selectById(invitationId);
        if (invitation == null) {
            throw new BusinessException("邀请不存在");
        }
        if (!Objects.equals(invitation.getInviteeId(), currentUserId)) {
            throw new BusinessException("无权操作此邀请");
        }
        if (!"PENDING".equals(invitation.getStatus())) {
            throw new BusinessException("邀请已处理");
        }
        invitation.setStatus("REJECTED");
        teamInvitationMapper.updateById(invitation);
    }

    public ResponseEntity<org.springframework.core.io.Resource> loadMemberAvatar(
            Long spaceId, Long targetUserId, jakarta.servlet.http.HttpServletRequest request) {
        long userId = com.clouddisk.util.AuthHelper.requireUserId(request);
        getOwnedSpace(spaceId, userId);

        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getSpaceId, spaceId)
                .eq(TeamMember::getUserId, targetUserId));
        if (member == null) {
            throw new BusinessException("成员不存在");
        }

        User user = userMapper.selectById(targetUserId);
        if (user == null || !StringUtils.hasText(user.getAvatar())) {
            throw new BusinessException("头像不存在");
        }

        var resource = storageService.loadAsResource(user.getAvatar());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(resource);
    }

    /**
     * 移除成员
     */
    public void removeMember(Long spaceId, Long targetUserId) {
        long currentUserId = AuthService.currentUserId();
        requireAdmin(spaceId, currentUserId);

        TeamSpace space = getSpace(spaceId);
        if (Objects.equals(space.getOwnerId(), targetUserId)) {
            throw new BusinessException("不能移除团队创建者");
        }

        teamMemberMapper.delete(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getSpaceId, spaceId)
                        .eq(TeamMember::getUserId, targetUserId));
    }

    /**
     * 列出团队成员
     */
    public List<Map<String, Object>> listMembers(Long spaceId) {
        long userId = AuthService.currentUserId();
        getOwnedSpace(spaceId, userId);

        List<TeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getSpaceId, spaceId));

        // 批量查询用户信息
        List<Long> userIds = members.stream().map(TeamMember::getUserId).collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamMember m : members) {
            User user = userMap.get(m.getUserId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", m.getUserId());
            item.put("username", user != null ? user.getUsername() : null);
            item.put("nickname", user != null ? user.getNickname() : null);
            item.put("avatar", user != null ? user.getAvatar() : null);
            item.put("hasAvatar", user != null && StringUtils.hasText(user.getAvatar()));
            item.put("role", m.getRole());
            item.put("joinTime", m.getJoinTime());
            result.add(item);
        }
        return result;
    }

    // ==================== 团队文件操作 ====================

    /**
     * 列出团队空间文件
     */
    public Map<String, Object> listFiles(Long spaceId, Long folderId) {
        long userId = AuthService.currentUserId();
        TeamSpace space = getOwnedSpace(spaceId, userId);
        Long fid = folderId != null ? folderId : space.getRootFolderId();

        if (!isFolderInTeamSpace(space, fid)) {
            throw new BusinessException("无权访问该目录");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spaceId", spaceId);
        result.put("rootFolderId", space.getRootFolderId());
        result.put("currentFolderId", fid);

        List<Folder> folders = folderMapper.selectList(
                new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getParentId, fid)
                        .eq(Folder::getDeleted, 0)
                        .orderByAsc(Folder::getFolderName));

        List<FileRecord> files = fileMapper.selectList(
                new LambdaQueryWrapper<FileRecord>()
                        .eq(FileRecord::getFolderId, fid)
                        .eq(FileRecord::getStatus, 1)
                        .orderByDesc(FileRecord::getCreateTime));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Folder f : folders) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", f.getId());
            item.put("name", f.getFolderName());
            item.put("type", "folder");
            item.put("parentId", f.getParentId());
            item.put("createdAt", f.getCreateTime());
            items.add(item);
        }
        for (FileRecord f : files) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", f.getId());
            item.put("name", f.getFileName());
            item.put("type", "file");
            item.put("sizeBytes", f.getFileSize());
            item.put("mimeType", f.getFileType());
            item.put("folderId", f.getFolderId());
            item.put("hasThumbnail", StringUtils.hasText(f.getThumbnailPath()) || StringUtils.hasText(f.getPosterPath()));
            item.put("previewable", isTeamFilePreviewable(f.getFileType(), f.getFileName()));
            item.put("createdAt", f.getCreateTime());
            items.add(item);
        }

        result.put("items", items);
        return result;
    }

    private boolean isFolderInTeamSpace(TeamSpace space, Long folderId) {
        if (folderId == null || folderId <= 0) return false;
        Folder current = folderMapper.selectById(folderId);
        if (current == null) return false;
        int depth = 0;
        while (current != null && depth < 30) {
            if (Objects.equals(current.getId(), space.getRootFolderId())) {
                return true;
            }
            Long parentId = current.getParentId();
            if (parentId == null || parentId <= 0) {
                return false;
            }
            current = folderMapper.selectById(parentId);
            depth++;
        }
        return false;
    }

    private boolean isTeamFilePreviewable(String mimeType, String fileName) {
        String type = mimeType != null ? mimeType : "";
        String lower = fileName != null ? fileName.toLowerCase() : "";
        if (type.startsWith("image/") || type.equals("application/pdf") || type.startsWith("video/")) {
            return true;
        }
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".pdf")
                || lower.endsWith(".mp4") || lower.endsWith(".webm");
    }

    // ==================== 内部方法 ====================

    private void addMember(Long spaceId, Long userId, String role) {
        TeamMember member = new TeamMember();
        member.setSpaceId(spaceId);
        member.setUserId(userId);
        member.setRole(role);
        member.setJoinTime(java.time.LocalDateTime.now());
        teamMemberMapper.insert(member);
    }

    private void requireAdmin(Long spaceId, long userId) {
        TeamMember member = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getSpaceId, spaceId)
                        .eq(TeamMember::getUserId, userId));
        if (member == null || (!"OWNER".equals(member.getRole()) && !"ADMIN".equals(member.getRole()))) {
            throw new BusinessException("需要管理员权限");
        }
    }

    /**
     * 解散/删除团队空间
     */
    public void deleteSpace(Long spaceId) {
        long currentUserId = AuthService.currentUserId();
        TeamSpace space = getSpace(spaceId);
        if (!Objects.equals(space.getOwnerId(), currentUserId)) {
            throw new BusinessException("只有创建者可以解散团队空间");
        }

        // 1. 设置团队空间状态为 0 (停用/删除)
        space.setStatus(0);
        teamSpaceMapper.updateById(space);

        // 2. 删除对应的团队成员记录
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getSpaceId, spaceId));

        // 3. 删除/回收团队对应的根文件夹及其所有子文件
        if (space.getRootFolderId() != null && space.getRootFolderId() > 0) {
            try {
                Folder rootFolder = folderMapper.selectById(space.getRootFolderId());
                if (rootFolder != null) {
                    rootFolder.setDeleted(1);
                    folderMapper.updateById(rootFolder);

                    List<Long> subfolderIds = folderTreeHelper.collectActiveSubtreeIds(space.getRootFolderId());
                    folderMapper.update(null, new LambdaUpdateWrapper<Folder>()
                            .in(Folder::getId, subfolderIds)
                            .set(Folder::getDeleted, 1));

                    fileMapper.update(null, new LambdaUpdateWrapper<FileRecord>()
                            .in(FileRecord::getFolderId, subfolderIds)
                            .set(FileRecord::getStatus, 0));
                }
            } catch (Exception e) {
                log.error("删除团队根文件夹失败", e);
            }
        }
    }

    /**
     * 退出团队空间
     */
    public void leaveSpace(Long spaceId) {
        long currentUserId = AuthService.currentUserId();
        TeamSpace space = getSpace(spaceId);
        if (Objects.equals(space.getOwnerId(), currentUserId)) {
            throw new BusinessException("创建者不能退出团队空间，请选择解散团队");
        }

        // 移除成员记录
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getSpaceId, spaceId)
                .eq(TeamMember::getUserId, currentUserId));
    }

    private void sendNotification(Long userId, String type, String title, String content, String refId) {
        notificationDispatcher.dispatch(userId, type, title, content, refId);
    }
}
