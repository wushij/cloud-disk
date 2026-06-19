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



import java.util.*;



@Service

@RequiredArgsConstructor

public class RecycleService {



    private final FileMapper fileMapper;

    private final FolderMapper folderMapper;

    private final StorageService storageService;

    private final FolderTreeHelper folderTreeHelper;

    private final StorageQuotaService quotaService;



    @Autowired(required = false)

    private FileSearchServiceBridge fileSearchBridge;



    public List<Map<String, Object>> list() {

        long userId = AuthService.currentUserId();

        List<Map<String, Object>> items = new ArrayList<>();



        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                .eq(FileRecord::getUserId, userId)

                .eq(FileRecord::getStatus, 0)

                .orderByDesc(FileRecord::getUpdateTime));

        for (FileRecord f : files) {

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



        List<Folder> folders = folderMapper.selectList(new LambdaQueryWrapper<Folder>()

                .eq(Folder::getUserId, userId)

                .eq(Folder::getDeleted, 1)

                .orderByDesc(Folder::getUpdateTime));

        for (Folder f : folders) {

            Map<String, Object> m = new LinkedHashMap<>();

            m.put("id", f.getId());

            m.put("name", f.getFolderName());

            m.put("type", "folder");

            m.put("deletedAt", f.getUpdateTime());

            items.add(m);

        }

        return items;

    }



    public void restoreFile(Long id) {

        long userId = AuthService.currentUserId();

        FileRecord file = fileMapper.selectById(id);

        if (file == null || !Objects.equals(file.getUserId(), userId)) {

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

        quotaService.addUsage(userId, file.getFileSize() != null ? file.getFileSize() : 0);

        syncSearch(file);

    }



    public void restoreFolder(Long id) {

        long userId = AuthService.currentUserId();

        Folder folder = folderMapper.selectById(id);

        if (folder == null || !Objects.equals(folder.getUserId(), userId)) {

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



        List<Long> folderIds = folderTreeHelper.expandRecycledRestoreIds(userId, id);

        folderMapper.update(null, new LambdaUpdateWrapper<Folder>()

                .in(Folder::getId, folderIds)

                .set(Folder::getDeleted, 0));



        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                .eq(FileRecord::getUserId, userId)

                .in(FileRecord::getFolderId, folderIds)

                .eq(FileRecord::getStatus, 0));

        for (FileRecord file : files) {

            file.setStatus(1);

            fileMapper.updateById(file);

            syncSearch(file);

        }

    }



    public void permanentDeleteFile(Long id) {

        long userId = AuthService.currentUserId();

        FileRecord file = fileMapper.selectById(id);

        if (file == null || !Objects.equals(file.getUserId(), userId)) {

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
            quotaService.subtractUsage(userId, file.getFileSize() != null ? file.getFileSize() : 0);
        }

    }



    public void permanentDeleteFolder(Long id) {

        long userId = AuthService.currentUserId();

        Folder folder = folderMapper.selectById(id);

        if (folder == null || !Objects.equals(folder.getUserId(), userId)) {

            throw new BusinessException("文件夹不存在");

        }

        List<Long> folderIds = folderTreeHelper.collectRecycledSubtreeIds(userId, id);

        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                .eq(FileRecord::getUserId, userId)

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

        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()

                .eq(FileRecord::getUserId, userId)

                .eq(FileRecord::getStatus, 0));

        for (FileRecord f : files) {

            permanentDeleteFile(f.getId());

        }

        folderMapper.delete(new LambdaQueryWrapper<Folder>()

                .eq(Folder::getUserId, userId)

                .eq(Folder::getDeleted, 1));

    }



    private void syncSearch(FileRecord file) {

        if (fileSearchBridge != null) {

            fileSearchBridge.onFileRecycled(file);

        }

    }

}


