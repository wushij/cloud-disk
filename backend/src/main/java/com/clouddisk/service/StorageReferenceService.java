package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 存储对象引用计数：复制/秒传会共用 storage_path，永久删除前须确认无其他记录引用。
 */
@Service
@RequiredArgsConstructor
public class StorageReferenceService {

    private final FileMapper fileMapper;
    private final StorageService storageService;

    /**
     * 统计仍引用指定物理路径的文件记录数（不含 excludeFileId）。
     * 路径可能出现在 storage_path、thumbnail_path、poster_path、transcode_path 任一字段。
     */
    public long countReferencesToPath(String path, Long excludeFileId) {
        if (!StringUtils.hasText(path)) {
            return 0L;
        }
        LambdaQueryWrapper<FileRecord> q = new LambdaQueryWrapper<FileRecord>()
                .and(w -> w.eq(FileRecord::getStoragePath, path)
                        .or().eq(FileRecord::getThumbnailPath, path)
                        .or().eq(FileRecord::getPosterPath, path)
                        .or().eq(FileRecord::getTranscodePath, path));
        if (excludeFileId != null) {
            q.ne(FileRecord::getId, excludeFileId);
        }
        return fileMapper.selectCount(q);
    }

    /**
     * 永久删除文件记录前，安全删除其关联物理对象（仅当无其他记录引用时）。
     */
    public void deletePhysicalArtifacts(FileRecord file, Long excludeFileId) {
        if (file == null) {
            return;
        }
        deletePathIfUnreferenced(file.getStoragePath(), excludeFileId);
        deletePathIfUnreferenced(file.getThumbnailPath(), excludeFileId);
        String poster = file.getPosterPath();
        if (StringUtils.hasText(poster) && !poster.equals(file.getThumbnailPath())) {
            deletePathIfUnreferenced(poster, excludeFileId);
        }
        deletePathIfUnreferenced(file.getTranscodePath(), excludeFileId);
    }

    private void deletePathIfUnreferenced(String path, Long excludeFileId) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        if (countReferencesToPath(path, excludeFileId) == 0) {
            storageService.delete(path);
        }
    }
}
