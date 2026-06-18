package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.Folder;
import com.clouddisk.entity.ShareRecord;
import com.clouddisk.entity.UploadSession;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.mapper.FolderMapper;
import com.clouddisk.mapper.ShareMapper;
import com.clouddisk.mapper.UploadSessionMapper;
import com.clouddisk.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupTaskService {

    private final ShareMapper shareMapper;
    private final FileMapper fileMapper;
    private final FolderMapper folderMapper;
    private final UploadSessionMapper uploadSessionMapper;
    private final RecycleService recycleService;
    private final CloudDiskProperties properties;
    private final StorageService storageService;
    private final NotificationDispatcher notificationDispatcher;

    public void cleanExpiredShares() {
        List<ShareRecord> expired = shareMapper.selectList(new LambdaQueryWrapper<ShareRecord>()
                .eq(ShareRecord::getStatus, 1)
                .isNotNull(ShareRecord::getExpireTime)
                .lt(ShareRecord::getExpireTime, LocalDateTime.now()));
        for (ShareRecord s : expired) {
            s.setStatus(0);
            shareMapper.updateById(s);
            notificationDispatcher.dispatch(
                    s.getUserId(), "SHARE_EXPIRED", "分享已过期",
                    "你的分享（" + s.getShareCode() + "）已过期失效",
                    String.valueOf(s.getId()));
        }
        if (!expired.isEmpty()) {
            log.info("已清理过期分享 {} 条", expired.size());
        }
    }

    public void cleanOldRecycle() {
        int days = properties.getSchedule().getRecycleRetainDays();
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getStatus, 0)
                .lt(FileRecord::getUpdateTime, before));
        for (FileRecord f : files) {
            try {
                recycleService.permanentDeleteFile(f.getId());
            } catch (Exception e) {
                log.warn("清理回收站文件失败 id={}", f.getId());
            }
        }
        List<Folder> folders = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getDeleted, 1)
                .lt(Folder::getUpdateTime, before));
        for (Folder folder : folders) {
            try {
                recycleService.permanentDeleteFolder(folder.getId());
            } catch (Exception e) {
                log.warn("清理回收站文件夹失败 id={}", folder.getId());
            }
        }
        if (!files.isEmpty() || !folders.isEmpty()) {
            log.info("已自动清理回收站（保留 {} 天）文件 {} 文件夹 {}", days, files.size(), folders.size());
        }
    }

    /** 按上传会话过期时间清理分片临时文件 */
    public void cleanExpiredChunks() {
        LocalDateTime now = LocalDateTime.now();
        List<UploadSession> expired = uploadSessionMapper.selectList(new LambdaQueryWrapper<UploadSession>()
                .isNotNull(UploadSession::getExpiresAt)
                .lt(UploadSession::getExpiresAt, now));
        for (UploadSession session : expired) {
            storageService.deleteByPrefix("chunks/" + session.getId());
            uploadSessionMapper.deleteById(session.getId());
        }
        if (!expired.isEmpty()) {
            log.info("已清理过期上传会话 {} 个", expired.size());
        }
    }
}
