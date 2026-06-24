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
import com.clouddisk.team.TeamRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 团队空间访问控制：角色权限下沉到文件/文件夹 API，并校验团队存储配额。
 */
@Service
@RequiredArgsConstructor
public class TeamAccessService {

    private final TeamSpaceMapper teamSpaceMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final FolderMapper folderMapper;
    private final FileMapper fileMapper;
    private final FolderTreeHelper folderTreeHelper;

    public record TeamContext(TeamSpace space, TeamMember member) {
        public String role() {
            return TeamRole.normalize(member.getRole());
        }
    }

    public Optional<TeamContext> resolveForFolder(long folderId, long userId) {
        if (folderId <= 0) {
            return Optional.empty();
        }
        TeamSpace space = resolveTeamSpaceForFolder(folderId);
        if (space == null || space.getStatus() != 1) {
            return Optional.empty();
        }
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getSpaceId, space.getId())
                .eq(TeamMember::getUserId, userId));
        if (member == null) {
            return Optional.empty();
        }
        return Optional.of(new TeamContext(space, member));
    }

    public Optional<TeamContext> resolveForFile(FileRecord file, long userId) {
        if (file == null || file.getFolderId() == null || file.getFolderId() <= 0) {
            return Optional.empty();
        }
        return resolveForFolder(file.getFolderId(), userId);
    }

    public TeamSpace resolveTeamSpaceForFolder(long folderId) {
        Folder current = folderMapper.selectById(folderId);
        if (current == null) {
            return null;
        }
        int depth = 0;
        while (current != null && depth < 30) {
            TeamSpace space = teamSpaceMapper.selectOne(new LambdaQueryWrapper<TeamSpace>()
                    .eq(TeamSpace::getRootFolderId, current.getId())
                    .eq(TeamSpace::getStatus, 1));
            if (space != null) {
                return space;
            }
            Long parentId = current.getParentId();
            if (parentId == null || parentId <= 0) {
                break;
            }
            current = folderMapper.selectById(parentId);
            depth++;
        }
        return null;
    }

    public void requireWrite(long folderId, long userId) {
        Optional<TeamContext> ctx = resolveForFolder(folderId, userId);
        if (ctx.isEmpty()) {
            return;
        }
        if (!TeamRole.canWrite(ctx.get().role())) {
            throw new BusinessException("只读成员无法修改团队文件");
        }
    }

    public void requireModifyFile(FileRecord file, long userId) {
        Optional<TeamContext> ctx = resolveForFile(file, userId);
        if (ctx.isEmpty()) {
            return;
        }
        String role = ctx.get().role();
        if (!TeamRole.canWrite(role)) {
            throw new BusinessException("只读成员无法修改团队文件");
        }
        if (TeamRole.canDeleteAnyTeamContent(role)) {
            return;
        }
        if (!Objects.equals(file.getUserId(), userId)) {
            throw new BusinessException("无权修改他人上传的团队文件");
        }
    }

    public void requireDeleteFile(FileRecord file, long userId) {
        Optional<TeamContext> ctx = resolveForFile(file, userId);
        if (ctx.isEmpty()) {
            return;
        }
        String role = ctx.get().role();
        if (TeamRole.canDeleteAnyTeamContent(role)) {
            return;
        }
        if (!TeamRole.canWrite(role)) {
            throw new BusinessException("只读成员无法删除团队文件");
        }
        if (!Objects.equals(file.getUserId(), userId)) {
            throw new BusinessException("无权删除他人上传的团队文件");
        }
    }

    public void requireModifyFolder(Folder folder, long userId) {
        Optional<TeamContext> ctx = resolveForFolder(folder.getId(), userId);
        if (ctx.isEmpty()) {
            return;
        }
        String role = ctx.get().role();
        if (!TeamRole.canWrite(role)) {
            throw new BusinessException("只读成员无法修改团队文件夹");
        }
        if (TeamRole.canDeleteAnyTeamContent(role)) {
            return;
        }
        if (!Objects.equals(folder.getUserId(), userId)) {
            throw new BusinessException("无权修改他人创建的团队文件夹");
        }
    }

    public void requireDeleteFolder(Folder folder, long userId) {
        Optional<TeamContext> ctx = resolveForFolder(folder.getId(), userId);
        if (ctx.isEmpty()) {
            return;
        }
        if (!TeamRole.canDeleteAnyTeamContent(ctx.get().role())) {
            throw new BusinessException("仅团队管理员可删除文件夹");
        }
    }

    public void requireSharePermission(long folderId, long userId) {
        Optional<TeamContext> ctx = resolveForFolder(folderId, userId);
        if (ctx.isEmpty()) {
            return;
        }
        if (!TeamRole.canShare(ctx.get().role())) {
            throw new BusinessException("普通成员无法分享团队文件，请联系团队管理员");
        }
    }

    public void requireManageTeam(long spaceId, long userId) {
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getSpaceId, spaceId)
                .eq(TeamMember::getUserId, userId));
        if (member == null || !TeamRole.canManageTeam(member.getRole())) {
            throw new BusinessException("需要团队管理员权限");
        }
    }

    public void checkTeamQuota(long folderId, long additionalBytes) {
        TeamSpace space = resolveTeamSpaceForFolder(folderId);
        if (space == null) {
            return;
        }
        long maxSize = space.getMaxSize() != null ? space.getMaxSize() : 0L;
        if (maxSize <= 0) {
            return;
        }
        long used = calculateTeamUsedBytes(space.getRootFolderId());
        if (used + additionalBytes > maxSize) {
            throw new BusinessException("团队存储空间不足，已用 "
                    + formatSize(used) + "，配额 " + formatSize(maxSize)
                    + "，需要 " + formatSize(additionalBytes));
        }
    }

    public long calculateTeamUsedBytes(Long rootFolderId) {
        if (rootFolderId == null || rootFolderId <= 0) {
            return 0L;
        }
        List<Long> folderIds = folderTreeHelper.collectActiveSubtreeIds(rootFolderId);
        if (folderIds.isEmpty()) {
            return 0L;
        }
        return fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                        .select(FileRecord::getFileSize)
                        .in(FileRecord::getFolderId, folderIds)
                        .eq(FileRecord::getStatus, 1))
                .stream()
                .mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0L)
                .sum();
    }

    public Map<String, Object> usageStats(TeamSpace space) {
        long used = calculateTeamUsedBytes(space.getRootFolderId());
        long max = space.getMaxSize() != null ? space.getMaxSize() : 0L;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usedBytes", used);
        m.put("maxSize", max);
        m.put("usedFormatted", formatSize(used));
        m.put("maxSizeFormatted", formatSize(max));
        if (max > 0) {
            double percent = Math.round(used * 10000.0 / max) / 100.0;
            m.put("usedPercent", Math.min(percent, 100.0));
        } else {
            m.put("usedPercent", 0.0);
        }
        return m;
    }

    public Map<String, Object> toAccessMap(TeamContext ctx, long userId) {
        String role = ctx.role();
        Map<String, Object> usage = usageStats(ctx.space());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("spaceId", ctx.space().getId());
        m.put("role", role);
        m.put("canWrite", TeamRole.canWrite(role));
        m.put("canManageTeam", TeamRole.canManageTeam(role));
        m.put("canShare", TeamRole.canShare(role));
        m.put("canDeleteAny", TeamRole.canDeleteAnyTeamContent(role));
        m.putAll(usage);
        return m;
    }

    public void enrichFileItem(Map<String, Object> item, FileRecord file, long userId, TeamContext ctx) {
        String role = ctx.role();
        boolean canWrite = TeamRole.canWrite(role);
        boolean canDelete = TeamRole.canDeleteAnyTeamContent(role)
                || (canWrite && Objects.equals(file.getUserId(), userId));
        boolean canModify = TeamRole.canDeleteAnyTeamContent(role)
                || (canWrite && Objects.equals(file.getUserId(), userId));
        item.put("ownerId", file.getUserId());
        item.put("canDelete", canDelete);
        item.put("canModify", canModify);
        if (file.getFileType() != null && item.get("officeFile") == Boolean.TRUE) {
            item.put("canEdit", canModify);
        }
    }

    public void enrichFolderItem(Map<String, Object> item, Folder folder, long userId, TeamContext ctx) {
        String role = ctx.role();
        boolean canWrite = TeamRole.canWrite(role);
        item.put("ownerId", folder.getUserId());
        item.put("canDelete", TeamRole.canDeleteAnyTeamContent(role));
        item.put("canModify", TeamRole.canDeleteAnyTeamContent(role)
                || (canWrite && Objects.equals(folder.getUserId(), userId)));
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024);
        }
        return String.format(Locale.ROOT, "%.2f GB", bytes / 1024.0 / 1024 / 1024);
    }
}
