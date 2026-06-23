package com.clouddisk.service;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.clouddisk.common.BusinessException;

import com.clouddisk.dto.FolderCreateRequest;

import com.clouddisk.dto.MoveRequest;

import com.clouddisk.dto.RenameRequest;

import com.clouddisk.entity.FileRecord;

import com.clouddisk.entity.Folder;

import com.clouddisk.entity.TeamSpace;

import com.clouddisk.entity.TeamMember;

import com.clouddisk.mapper.FileMapper;

import com.clouddisk.mapper.FolderMapper;

import com.clouddisk.mapper.TeamSpaceMapper;

import com.clouddisk.mapper.TeamMemberMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;



import java.util.*;

import java.util.stream.Collectors;



@Service

@RequiredArgsConstructor

public class FolderService {



    private final FolderMapper folderMapper;

    private final FileMapper fileMapper;

    private final FolderTreeHelper folderTreeHelper;

    private final TeamSpaceMapper teamSpaceMapper;

    private final TeamMemberMapper teamMemberMapper;



    @Autowired(required = false)

    private FileSearchServiceBridge fileSearchBridge;



    public List<Map<String, Object>> tree() {

        long userId = AuthService.currentUserId();

        List<Folder> all = listAllFoldersForTree(userId);

        Map<Long, List<Folder>> byParent = all.stream()

                .collect(Collectors.groupingBy(Folder::getParentId));

        return buildTree(0L, byParent);

    }



    /** 个人目录 + 已加入团队的目录树（供左侧树与云盘列表使用） */
    public List<Folder> listAllFoldersForTree(long userId) {
        List<Folder> owned = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getUserId, userId)
                .eq(Folder::getDeleted, 0)
                .orderByAsc(Folder::getFolderName));
        Map<Long, Folder> byId = new LinkedHashMap<>();
        for (Folder folder : owned) {
            byId.put(folder.getId(), folder);
        }
        for (Folder root : listTeamRootFoldersForUser(userId)) {
            for (Long folderId : folderTreeHelper.collectActiveSubtreeIds(root.getId())) {
                if (byId.containsKey(folderId)) continue;
                Folder folder = folderMapper.selectById(folderId);
                if (folder != null && folder.getDeleted() == 0) {
                    byId.put(folderId, folder);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }



    /** 当前用户可访问的团队根目录（文件夹 owner 为创建者） */
    public List<Folder> listTeamRootFoldersForUser(long userId) {
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId));
        if (memberships.isEmpty()) return List.of();

        List<Long> spaceIds = memberships.stream().map(TeamMember::getSpaceId).distinct().toList();
        List<TeamSpace> spaces = teamSpaceMapper.selectList(
                new LambdaQueryWrapper<TeamSpace>().in(TeamSpace::getId, spaceIds).eq(TeamSpace::getStatus, 1));
        if (spaces.isEmpty()) return List.of();

        List<Long> rootIds = spaces.stream()
                .map(TeamSpace::getRootFolderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (rootIds.isEmpty()) return List.of();

        return folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                .in(Folder::getId, rootIds)
                .eq(Folder::getDeleted, 0)
                .orderByAsc(Folder::getFolderName));
    }



    /** 团队空间目录：团队成员（含创建者）可访问的团队根或其子目录 */
    public boolean isSharedTeamFolder(long folderId, long userId) {
        if (folderId <= 0) return false;
        if (!hasAccessToFolder(folderId, userId)) return false;
        return isFolderUnderTeamSpace(folderId);
    }

    private boolean isFolderUnderTeamSpace(long folderId) {
        Folder current = folderMapper.selectById(folderId);
        if (current == null) return false;
        int depth = 0;
        while (current != null && depth < 30) {
            TeamSpace space = teamSpaceMapper.selectOne(new LambdaQueryWrapper<TeamSpace>()
                    .eq(TeamSpace::getRootFolderId, current.getId())
                    .eq(TeamSpace::getStatus, 1));
            if (space != null) return true;
            Long parentId = current.getParentId();
            if (parentId == null || parentId <= 0) break;
            current = folderMapper.selectById(parentId);
            depth++;
        }
        return false;
    }

    /** 团队目录下的分享权限：仅 OWNER / ADMIN 可创建外链 */
    public void requireTeamSharePermission(long folderId, long userId) {
        if (folderId <= 0) return;
        TeamSpace space = resolveTeamSpaceForFolder(folderId);
        if (space == null) return;

        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getSpaceId, space.getId())
                .eq(TeamMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException("你不是该团队的成员");
        }
        if ("OWNER".equals(member.getRole()) || "ADMIN".equals(member.getRole())) {
            return;
        }
        throw new BusinessException("普通成员无法分享团队文件，请联系团队管理员");
    }

    private TeamSpace resolveTeamSpaceForFolder(long folderId) {
        Folder current = folderMapper.selectById(folderId);
        if (current == null) return null;
        int depth = 0;
        while (current != null && depth < 30) {
            TeamSpace space = teamSpaceMapper.selectOne(new LambdaQueryWrapper<TeamSpace>()
                    .eq(TeamSpace::getRootFolderId, current.getId())
                    .eq(TeamSpace::getStatus, 1));
            if (space != null) return space;
            Long parentId = current.getParentId();
            if (parentId == null || parentId <= 0) break;
            current = folderMapper.selectById(parentId);
            depth++;
        }
        return null;
    }



    private List<Map<String, Object>> buildTree(Long parentId, Map<Long, List<Folder>> byParent) {

        List<Folder> children = byParent.getOrDefault(parentId, List.of());

        List<Map<String, Object>> result = new ArrayList<>();

        for (Folder f : children) {

            Map<String, Object> node = new LinkedHashMap<>();

            node.put("id", f.getId());

            node.put("label", f.getFolderName());

            node.put("parentId", f.getParentId());

            node.put("children", buildTree(f.getId(), byParent));

            result.add(node);

        }

        return result;

    }



    public Folder create(FolderCreateRequest req) {

        long userId = AuthService.currentUserId();

        Long parentId = req.getParentId() != null ? req.getParentId() : 0L;

        if (parentId > 0) {

            Folder parent = getOwnedOrShared(parentId, userId);

            if (parent.getDeleted() != 0) throw new BusinessException("父目录不存在");

        }

        String name = req.getFolderName();

        if (name == null || name.isBlank()) throw new BusinessException("文件夹名称不能为空");

        name = name.trim();

        checkDuplicateName(userId, parentId, name, null);

        Folder folder = new Folder();

        folder.setUserId(userId);

        folder.setParentId(parentId);

        folder.setFolderName(name);

        folder.setDeleted(0);

        folderMapper.insert(folder);

        return folder;

    }



    public Folder rename(Long id, RenameRequest req) {

        long userId = AuthService.currentUserId();

        Folder folder = getOwnedOrShared(id, userId);

        if (folder.getDeleted() != 0) throw new BusinessException("文件夹在回收站中");

        String name = req.getName();

        if (name == null || name.isBlank()) throw new BusinessException("名称不能为空");

        name = name.trim();

        checkDuplicateName(userId, folder.getParentId(), name, id);

        folder.setFolderName(name);

        folderMapper.updateById(folder);

        return folder;

    }



    public Folder move(Long id, MoveRequest req) {

        long userId = AuthService.currentUserId();

        Folder folder = getOwnedOrShared(id, userId);

        if (folder.getDeleted() != 0) throw new BusinessException("文件夹在回收站中");

        Long targetId = req.getTargetFolderId() != null ? req.getTargetFolderId() : 0L;

        if (Objects.equals(folder.getId(), targetId)) {

            throw new BusinessException("不能移动到自身");

        }

        if (targetId > 0) {

            Folder target = getOwnedOrShared(targetId, userId);

            if (target.getDeleted() != 0) throw new BusinessException("目标目录不存在");

            if (isDescendant(targetId, folder.getId(), userId)) {

                throw new BusinessException("不能移动到子目录");

            }

        }

        checkDuplicateName(userId, targetId, folder.getFolderName(), id);

        folder.setParentId(targetId);

        folderMapper.updateById(folder);

        return folder;

    }



    /** 级联移入回收站：子文件夹 + 子文件 */

    public void deleteToRecycle(Long id) {

        long userId = AuthService.currentUserId();

        Folder folder = getOwnedOrShared(id, userId);

        if (folder.getDeleted() != 0) throw new BusinessException("文件夹已在回收站");



        List<Long> folderIds = folderTreeHelper.collectActiveSubtreeIds(id);

        folderMapper.update(null, new LambdaUpdateWrapper<Folder>()

                .in(Folder::getId, folderIds)

                .set(Folder::getDeleted, 1));



        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                .in(FileRecord::getFolderId, folderIds)

                .eq(FileRecord::getStatus, 1));

        for (FileRecord file : files) {

            file.setStatus(0);

            fileMapper.updateById(file);

            if (fileSearchBridge != null) {

                fileSearchBridge.onFileRecycled(file);

            }

        }

    }



    public boolean hasAccessToFolder(Long folderId, long userId) {

        if (folderId == null || folderId <= 0) return false;

        Folder folder = folderMapper.selectById(folderId);

        if (folder == null) return false;

        if (folder.getUserId().equals(userId)) return true;



        Folder current = folder;

        int depth = 0;

        while (current != null && depth < 20) {

            TeamSpace space = teamSpaceMapper.selectOne(new LambdaQueryWrapper<TeamSpace>()

                    .eq(TeamSpace::getRootFolderId, current.getId()));

            if (space != null) {

                TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()

                        .eq(TeamMember::getSpaceId, space.getId())

                        .eq(TeamMember::getUserId, userId));

                return member != null;

            }

            if (current.getParentId() == null || current.getParentId() <= 0) {

                break;

            }

            current = folderMapper.selectById(current.getParentId());

            depth++;

        }

        return false;

    }



    public Folder getOwnedOrShared(Long id, long userId) {

        Folder folder = folderMapper.selectById(id);

        if (folder == null) {

            throw new BusinessException("文件夹不存在");

        }

        if (folder.getUserId().equals(userId)) {

            return folder;

        }

        if (hasAccessToFolder(id, userId)) {

            return folder;

        }

        throw new BusinessException("没有权限访问该文件夹");

    }



    public Folder getOwned(Long id, long userId) {

        Folder folder = folderMapper.selectById(id);

        if (folder == null || !Objects.equals(folder.getUserId(), userId)) {

            throw new BusinessException("文件夹不存在");

        }

        return folder;

    }



    private void checkDuplicateName(long userId, Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<Folder> q = new LambdaQueryWrapper<Folder>()
                .eq(Folder::getParentId, parentId)
                .eq(Folder::getFolderName, name)
                .eq(Folder::getDeleted, 0);
        // 个人云盘按用户隔离；团队目录同一父级下全局唯一
        if (!isSharedTeamFolder(parentId, userId)) {
            q.eq(Folder::getUserId, userId);
        }
        if (excludeId != null) q.ne(Folder::getId, excludeId);
        if (folderMapper.selectCount(q) > 0) {
            throw new BusinessException("同名文件夹已存在");
        }
    }



    private boolean isDescendant(Long folderId, Long ancestorId, long userId) {

        Folder current = folderMapper.selectById(folderId);

        while (current != null && current.getParentId() != null && current.getParentId() > 0) {

            if (Objects.equals(current.getParentId(), ancestorId)) return true;

            current = folderMapper.selectById(current.getParentId());

        }

        return false;

    }

    public List<Map<String, Object>> getBreadcrumbs(Long folderId, long userId) {
        return getBreadcrumbs(folderId, userId, false);
    }

    public List<Map<String, Object>> getBreadcrumbs(Long folderId, long userId, boolean full) {
        List<Map<String, Object>> crumbs = new ArrayList<>();
        if (folderId == null || folderId <= 0) {
            Map<String, Object> rootCrumb = new LinkedHashMap<>();
            rootCrumb.put("id", 0L);
            rootCrumb.put("name", "全部文件");
            crumbs.add(rootCrumb);
            return crumbs;
        }

        Folder folder = getOwnedOrShared(folderId, userId);
        Folder current = folder;
        int depth = 0;
        while (current != null && depth < 30) {
            Map<String, Object> crumb = new LinkedHashMap<>();
            crumb.put("id", current.getId());
            crumb.put("name", current.getFolderName());
            crumbs.add(0, crumb);

            Long parentId = current.getParentId();
            if (parentId == null || parentId <= 0) {
                break;
            }
            current = folderMapper.selectById(parentId);
            depth++;
        }

        if (full) {
            Map<String, Object> rootCrumb = new LinkedHashMap<>();
            rootCrumb.put("id", 0L);
            rootCrumb.put("name", "全部文件");
            crumbs.add(0, rootCrumb);
            return crumbs;
        }

        TeamSpace space = resolveTeamSpaceForFolder(folderId);
        if (space != null) {
            Long rootFolderId = space.getRootFolderId();
            int rootIndex = -1;
            for (int i = 0; i < crumbs.size(); i++) {
                if (Objects.equals(crumbs.get(i).get("id"), rootFolderId)) {
                    rootIndex = i;
                    break;
                }
            }
            if (rootIndex >= 0) {
                crumbs = crumbs.subList(rootIndex, crumbs.size());
                List<Map<String, Object>> copy = new ArrayList<>();
                for (int i = 0; i < crumbs.size(); i++) {
                    Map<String, Object> original = crumbs.get(i);
                    Map<String, Object> m = new LinkedHashMap<>(original);
                    if (i == 0) {
                        m.put("name", space.getName());
                    }
                    copy.add(m);
                }
                crumbs = copy;
            }
        } else {
            Map<String, Object> rootCrumb = new LinkedHashMap<>();
            rootCrumb.put("id", 0L);
            rootCrumb.put("name", "全部文件");
            crumbs.add(0, rootCrumb);
        }

        return crumbs;
    }

}


