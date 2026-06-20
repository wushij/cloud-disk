package com.clouddisk.service;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.clouddisk.common.BusinessException;

import com.clouddisk.entity.FileRecord;

import com.clouddisk.entity.Folder;

import com.clouddisk.mapper.FileMapper;

import com.clouddisk.mapper.FolderMapper;

import com.clouddisk.storage.StorageService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;



import java.time.LocalDateTime;
import java.util.*;



@Service

@RequiredArgsConstructor

public class RecycleService {



    private final FileMapper fileMapper;

    private final FolderMapper folderMapper;

    private final StorageService storageService;

    private final FolderTreeHelper folderTreeHelper;

    private final StorageQuotaService quotaService;

    private final FolderService folderService;



    @Autowired(required = false)

    private FileSearchServiceBridge fileSearchBridge;



    public List<Map<String, Object>> list() {

        long userId = AuthService.currentUserId();

        List<Map<String, Object>> items = new ArrayList<>();

        Set<Long> seenFileIds = new HashSet<>();

        Set<Long> seenFolderIds = new HashSet<>();

        Set<Long> teamFolderScope = teamFolderScope(userId);



        // 1. 先查询所有的已删除文件夹，并记录它们的 ID 以便做层级过滤

        List<Folder> folders = folderMapper.selectList(new LambdaQueryWrapper<Folder>()

                .eq(Folder::getUserId, userId)

                .eq(Folder::getDeleted, 1)

                .orderByDesc(Folder::getUpdateTime));



        List<Folder> teamFolders = new ArrayList<>();

        if (!teamFolderScope.isEmpty()) {

            teamFolders = folderMapper.selectList(new LambdaQueryWrapper<Folder>()

                    .eq(Folder::getDeleted, 1)

                    .in(Folder::getId, teamFolderScope)

                    .orderByDesc(Folder::getUpdateTime));

        }



        Set<Long> deletedFolderIds = new HashSet<>();

        for (Folder f : folders) {

            deletedFolderIds.add(f.getId());

        }

        for (Folder f : teamFolders) {

            deletedFolderIds.add(f.getId());

        }



        // 2. 查询并过滤文件：如果文件所在的直接父文件夹也在已删除列表中，则在此列表内隐藏该文件（只保留最上层被删文件夹）

        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                .eq(FileRecord::getUserId, userId)

                .eq(FileRecord::getStatus, 0)

                .orderByDesc(FileRecord::getUpdateTime));

        for (FileRecord f : files) {

            if (f.getFolderId() != null && f.getFolderId() > 0 && deletedFolderIds.contains(f.getFolderId())) {

                continue;

            }

            appendFileItem(items, seenFileIds, f);

        }



        if (!teamFolderScope.isEmpty()) {

            List<FileRecord> teamFiles = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                    .eq(FileRecord::getStatus, 0)

                    .in(FileRecord::getFolderId, teamFolderScope)

                    .orderByDesc(FileRecord::getUpdateTime));

            for (FileRecord f : teamFiles) {

                if (f.getFolderId() != null && f.getFolderId() > 0 && deletedFolderIds.contains(f.getFolderId())) {

                    continue;

                }

                appendFileItem(items, seenFileIds, f);

            }

        }



        // 3. 过滤并追加文件夹：如果文件夹的父文件夹也在已删除列表中，则说明它是被级联删除的子目录，不在此处单独展示

        for (Folder f : folders) {

            if (f.getParentId() != null && f.getParentId() > 0 && deletedFolderIds.contains(f.getParentId())) {

                continue;

            }

            appendFolderItem(items, seenFolderIds, f);

        }



        for (Folder f : teamFolders) {

            if (f.getParentId() != null && f.getParentId() > 0 && deletedFolderIds.contains(f.getParentId())) {

                continue;

            }

            appendFolderItem(items, seenFolderIds, f);

        }



        items.sort(Comparator.comparing(

                (Map<String, Object> item) -> (LocalDateTime) item.get("deletedAt"),

                Comparator.nullsLast(Comparator.reverseOrder())));

        return items;

    }



    public void restoreFile(Long id) {

        long userId = AuthService.currentUserId();

        FileRecord file = fileMapper.selectById(id);

        if (file == null || file.getStatus() == null || file.getStatus() != 0) {

            throw new BusinessException("文件不存在");

        }

        if (!canAccessRecycledFile(userId, file)) {

            throw new BusinessException("文件不存在");

        }

        if (file.getFolderId() != null && file.getFolderId() > 0) {

            Folder parent = folderMapper.selectById(file.getFolderId());

            if (parent != null && parent.getDeleted() == 1) {

                throw new BusinessException("请先恢复所在文件夹");

            }

        }

        file.setStatus(1);

        fileMapper.updateById(file);

        quotaService.addUsage(file.getUserId(), file.getFileSize() != null ? file.getFileSize() : 0);

        syncSearch(file);

    }



    public void restoreFolder(Long id) {

        long userId = AuthService.currentUserId();

        Folder folder = folderMapper.selectById(id);

        if (folder == null || !canAccessRecycledFolder(userId, folder)) {

            throw new BusinessException("文件夹不存在");

        }

        if (folder.getDeleted() == 0) {

            throw new BusinessException("文件夹不在回收站");

        }

        if (folder.getParentId() != null && folder.getParentId() > 0) {

            Folder parent = folderMapper.selectById(folder.getParentId());

            if (parent != null && parent.getDeleted() == 1) {

                throw new BusinessException("请先恢复上级文件夹");

            }

        }

        List<Long> folderIds = folderTreeHelper.expandRecycledRestoreIds(id);

        folderMapper.update(null, new LambdaUpdateWrapper<Folder>()

                .in(Folder::getId, folderIds)

                .set(Folder::getDeleted, 0));



        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                .in(FileRecord::getFolderId, folderIds)

                .eq(FileRecord::getStatus, 0));

        for (FileRecord file : files) {

            file.setStatus(1);

            fileMapper.updateById(file);

            quotaService.addUsage(file.getUserId(), file.getFileSize() != null ? file.getFileSize() : 0);

            syncSearch(file);

        }

    }



    public void permanentDeleteFile(Long id) {

        long userId = AuthService.currentUserId();

        FileRecord file = fileMapper.selectById(id);

        if (file == null || file.getStatus() == null || file.getStatus() != 0) {

            throw new BusinessException("文件不存在");

        }

        if (!canAccessRecycledFile(userId, file)) {

            throw new BusinessException("文件不存在");

        }

        long refs = fileMapper.selectCount(new LambdaQueryWrapper<FileRecord>()

                .eq(FileRecord::getStoragePath, file.getStoragePath())

                .ne(FileRecord::getId, id));

        if (refs == 0) {

            storageService.delete(file.getStoragePath());

            if (file.getThumbnailPath() != null) storageService.delete(file.getThumbnailPath());

            if (file.getPosterPath() != null && !file.getPosterPath().equals(file.getThumbnailPath())) {

                storageService.delete(file.getPosterPath());

            }

            if (file.getTranscodePath() != null) storageService.delete(file.getTranscodePath());

        }

        fileMapper.deleteById(id);

        // 永久删除时扣减用量（如果文件在回收站中 status=0，用量已在移入回收站时扣减，
        // 这里只处理状态为1的文件被直接永久删除的情况）
        if (file.getStatus() != null && file.getStatus() == 1) {
            quotaService.subtractUsage(file.getUserId(), file.getFileSize() != null ? file.getFileSize() : 0);
        }

    }



    public void permanentDeleteFolder(Long id) {

        long userId = AuthService.currentUserId();

        Folder folder = folderMapper.selectById(id);

        if (folder == null || !canAccessRecycledFolder(userId, folder)) {

            throw new BusinessException("文件夹不存在");

        }

        List<Long> folderIds = folderTreeHelper.collectRecycledSubtreeIds(id);

        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                .in(FileRecord::getFolderId, folderIds)

                .eq(FileRecord::getStatus, 0));

        for (FileRecord f : files) {

            permanentDeleteFile(f.getId());

        }

        folderIds.sort(Comparator.reverseOrder());

        for (Long fid : folderIds) {

            folderMapper.deleteById(fid);

        }

    }



    public void clearAll() {

        long userId = AuthService.currentUserId();

        Set<Long> teamFolderScope = teamFolderScope(userId);



        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                .eq(FileRecord::getUserId, userId)

                .eq(FileRecord::getStatus, 0));

        for (FileRecord f : files) {

            silentDeleteFile(f.getId());

        }



        if (!teamFolderScope.isEmpty()) {

            List<FileRecord> teamFiles = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                    .eq(FileRecord::getStatus, 0)

                    .in(FileRecord::getFolderId, teamFolderScope));

            for (FileRecord f : teamFiles) {

                if (!Objects.equals(f.getUserId(), userId)) {

                    silentDeleteFile(f.getId());

                }

            }

        }



        List<Folder> folders = folderMapper.selectList(new LambdaQueryWrapper<Folder>()

                .eq(Folder::getUserId, userId)

                .eq(Folder::getDeleted, 1));

        for (Folder folder : folders) {

            silentDeleteFolder(folder.getId());

        }



        if (!teamFolderScope.isEmpty()) {

            List<Folder> teamFolders = folderMapper.selectList(new LambdaQueryWrapper<Folder>()

                    .eq(Folder::getDeleted, 1)

                    .in(Folder::getId, teamFolderScope));

            for (Folder folder : teamFolders) {

                if (!Objects.equals(folder.getUserId(), userId)) {

                    silentDeleteFolder(folder.getId());

                }

            }

        }

    }

    /**
     * 幂等删除文件：已被级联删除的文件直接跳过，不抛异常。
     */
    private void silentDeleteFile(Long id) {
        FileRecord file = fileMapper.selectById(id);
        if (file == null || file.getStatus() == null || file.getStatus() != 0) {
            return;
        }
        long refs = fileMapper.selectCount(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getStoragePath, file.getStoragePath())
                .ne(FileRecord::getId, id));
        if (refs == 0) {
            storageService.delete(file.getStoragePath());
            if (file.getThumbnailPath() != null) storageService.delete(file.getThumbnailPath());
            if (file.getPosterPath() != null && !file.getPosterPath().equals(file.getThumbnailPath())) {
                storageService.delete(file.getPosterPath());
            }
            if (file.getTranscodePath() != null) storageService.delete(file.getTranscodePath());
        }
        fileMapper.deleteById(id);
        if (file.getStatus() != null && file.getStatus() == 1) {
            quotaService.subtractUsage(file.getUserId(), file.getFileSize() != null ? file.getFileSize() : 0);
        }
    }

    /**
     * 幂等删除文件夹：已被级联删除的文件夹直接跳过，不抛异常。
     */
    private void silentDeleteFolder(Long id) {
        Folder folder = folderMapper.selectById(id);
        if (folder == null || folder.getDeleted() == 0) {
            return;
        }
        List<Long> folderIds = folderTreeHelper.collectRecycledSubtreeIds(id);
        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .in(FileRecord::getFolderId, folderIds)
                .eq(FileRecord::getStatus, 0));
        for (FileRecord f : files) {
            silentDeleteFile(f.getId());
        }
        folderIds.sort(Comparator.reverseOrder());
        for (Long fid : folderIds) {
            folderMapper.deleteById(fid);
        }
    }



    private void syncSearch(FileRecord file) {

        if (fileSearchBridge != null) {

            fileSearchBridge.onFileRecycled(file);

        }

    }



    private Set<Long> teamFolderScope(long userId) {

        Set<Long> scope = new HashSet<>();

        for (Folder root : folderService.listTeamRootFoldersForUser(userId)) {

            scope.addAll(folderTreeHelper.collectAllSubtreeIds(root.getId()));

        }

        return scope;

    }



    private boolean canAccessRecycledFile(long userId, FileRecord file) {

        if (Objects.equals(file.getUserId(), userId)) {

            return true;

        }

        Long folderId = file.getFolderId();

        return folderId != null && folderId > 0 && folderService.hasAccessToFolder(folderId, userId);

    }



    private boolean canAccessRecycledFolder(long userId, Folder folder) {

        if (Objects.equals(folder.getUserId(), userId)) {

            return true;

        }

        return folderService.hasAccessToFolder(folder.getId(), userId);

    }



    private void appendFileItem(List<Map<String, Object>> items, Set<Long> seenFileIds, FileRecord f) {

        if (!seenFileIds.add(f.getId())) {

            return;

        }

        Map<String, Object> m = new LinkedHashMap<>();

        m.put("id", f.getId());

        m.put("name", f.getFileName());

        m.put("type", "file");

        m.put("sizeBytes", f.getFileSize());

        m.put("deletedAt", f.getUpdateTime());

        m.put("mimeType", f.getFileType());

        m.put("hasThumbnail", org.springframework.util.StringUtils.hasText(f.getThumbnailPath()) || org.springframework.util.StringUtils.hasText(f.getPosterPath()));

        items.add(m);

    }



    private void appendFolderItem(List<Map<String, Object>> items, Set<Long> seenFolderIds, Folder f) {

        if (!seenFolderIds.add(f.getId())) {

            return;

        }

        Map<String, Object> m = new LinkedHashMap<>();

        m.put("id", f.getId());

        m.put("name", f.getFolderName());

        m.put("type", "folder");

        m.put("deletedAt", f.getUpdateTime());

        items.add(m);

    }

}

