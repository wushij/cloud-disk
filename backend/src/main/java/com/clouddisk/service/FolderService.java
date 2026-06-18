package com.clouddisk.service;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.clouddisk.common.BusinessException;

import com.clouddisk.dto.FolderCreateRequest;

import com.clouddisk.dto.MoveRequest;

import com.clouddisk.dto.RenameRequest;

import com.clouddisk.entity.FileRecord;

import com.clouddisk.entity.Folder;

import com.clouddisk.mapper.FileMapper;

import com.clouddisk.mapper.FolderMapper;

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



    @Autowired(required = false)

    private FileSearchServiceBridge fileSearchBridge;



    public List<Map<String, Object>> tree() {

        long userId = AuthService.currentUserId();

        List<Folder> all = folderMapper.selectList(new LambdaQueryWrapper<Folder>()

                .eq(Folder::getUserId, userId)

                .eq(Folder::getDeleted, 0)

                .orderByAsc(Folder::getFolderName));

        Map<Long, List<Folder>> byParent = all.stream()

                .collect(Collectors.groupingBy(Folder::getParentId));

        return buildTree(0L, byParent);

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

            Folder parent = getOwned(parentId, userId);

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

        Folder folder = getOwned(id, userId);

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

        Folder folder = getOwned(id, userId);

        if (folder.getDeleted() != 0) throw new BusinessException("文件夹在回收站中");

        Long targetId = req.getTargetFolderId() != null ? req.getTargetFolderId() : 0L;

        if (Objects.equals(folder.getId(), targetId)) {

            throw new BusinessException("不能移动到自身");

        }

        if (targetId > 0) {

            Folder target = getOwned(targetId, userId);

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

        Folder folder = getOwned(id, userId);

        if (folder.getDeleted() != 0) throw new BusinessException("文件夹已在回收站");



        List<Long> folderIds = folderTreeHelper.collectActiveSubtreeIds(userId, id);

        folderMapper.update(null, new LambdaUpdateWrapper<Folder>()

                .in(Folder::getId, folderIds)

                .set(Folder::getDeleted, 1));



        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                .eq(FileRecord::getUserId, userId)

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



    public Folder getOwned(Long id, long userId) {

        Folder folder = folderMapper.selectById(id);

        if (folder == null || !Objects.equals(folder.getUserId(), userId)) {

            throw new BusinessException("文件夹不存在");

        }

        return folder;

    }



    private void checkDuplicateName(long userId, Long parentId, String name, Long excludeId) {

        LambdaQueryWrapper<Folder> q = new LambdaQueryWrapper<Folder>()

                .eq(Folder::getUserId, userId)

                .eq(Folder::getParentId, parentId)

                .eq(Folder::getFolderName, name)

                .eq(Folder::getDeleted, 0);

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

            if (current == null || !Objects.equals(current.getUserId(), userId)) break;

        }

        return false;

    }

}


