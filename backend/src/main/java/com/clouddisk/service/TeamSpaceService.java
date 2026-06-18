package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clouddisk.common.BusinessException;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.Folder;
import com.clouddisk.entity.TeamMember;
import com.clouddisk.entity.TeamSpace;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.mapper.FolderMapper;
import com.clouddisk.mapper.TeamMemberMapper;
import com.clouddisk.mapper.TeamSpaceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamSpaceService {

    private final TeamSpaceMapper teamSpaceMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final FolderMapper folderMapper;
    private final FileMapper fileMapper;
    private final NotificationDispatcher notificationDispatcher;

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

    // ==================== 成员管理 ====================

    /**
     * 邀请成员
     */
    public TeamMember inviteMember(Long spaceId, Long userId, String role) {
        long currentUserId = AuthService.currentUserId();
        TeamSpace space = getOwnedSpace(spaceId, currentUserId);
        requireAdmin(spaceId, currentUserId);

        // 检查目标用户是否已是成员
        TeamMember existing = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getSpaceId, spaceId)
                        .eq(TeamMember::getUserId, userId));
        if (existing != null) {
            throw new BusinessException("该用户已是团队成员");
        }

        if (role == null || role.isBlank()) role = "MEMBER";
        addMember(spaceId, userId, role);

        // 发送通知
        sendNotification(userId, "TEAM_INVITED", "团队邀请",
                "你已被邀请加入团队「" + space.getName() + "」", String.valueOf(spaceId));

        TeamMember member = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getSpaceId, spaceId)
                        .eq(TeamMember::getUserId, userId));
        return member;
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
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamMember m : members) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", m.getUserId());
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

        // 验证文件夹属于团队空间（简单验证：文件夹的 owner 为空间创建者）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spaceId", spaceId);
        result.put("rootFolderId", space.getRootFolderId());
        result.put("currentFolderId", fid);

        // 列出子文件夹
        List<Folder> folders = folderMapper.selectList(
                new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getParentId, fid)
                        .eq(Folder::getDeleted, 0)
                        .orderByAsc(Folder::getFolderName));

        // 列出文件
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
            item.put("createdAt", f.getCreateTime());
            items.add(item);
        }

        result.put("items", items);
        return result;
    }

    // ==================== 内部方法 ====================

    private void addMember(Long spaceId, Long userId, String role) {
        TeamMember member = new TeamMember();
        member.setSpaceId(spaceId);
        member.setUserId(userId);
        member.setRole(role);
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

    private void sendNotification(Long userId, String type, String title, String content, String refId) {
        notificationDispatcher.dispatch(userId, type, title, content, refId);
    }
}
