package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clouddisk.cache.CacheService;
import com.clouddisk.cache.FileCacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.dto.MoveRequest;
import com.clouddisk.dto.RenameRequest;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.Folder;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.mapper.FolderMapper;
import com.clouddisk.media.MediaProcessService;
import com.clouddisk.media.TranscodeStatus;
import com.clouddisk.search.FileSearchService;
import com.clouddisk.security.VirusScanService;
import com.clouddisk.storage.StorageService;
import com.clouddisk.util.FileValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
public class FileService {

    private static final long MD5_CACHE_TTL = 86400 * 7;

    private final FileMapper fileMapper;
    private final FolderMapper folderMapper;
    private final FolderService folderService;
    private final StorageService storageService;
    private final FileValidator fileValidator;
    private final MediaProcessService mediaProcessService;
    private final CloudDiskProperties properties;
    private final CacheService cacheService;
    private final FileCacheService fileCacheService;
    private final StorageQuotaService quotaService;
    private final VirusScanService virusScanService;

    /** ElasticSearch 搜索服务（条件装配，ES 未启用时为 null） */
    @Autowired(required = false)
    private FileSearchService fileSearchService;

    public Map<String, Object> list(Long folderId, int page, int size, String keyword, String fileType) {
        long userId = AuthService.currentUserId();

        // ES 启用且有关键词时，使用 ElasticSearch 搜索
        if (fileSearchService != null && StringUtils.hasText(keyword)) {
            return fileSearchService.search(userId, keyword, fileType, page, size);
        }

        long fid = folderId != null ? folderId : 0L;

        LambdaQueryWrapper<FileRecord> fq = new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getUserId, userId)
                .eq(FileRecord::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            fq.like(FileRecord::getFileName, keyword.trim());
        } else {
            fq.eq(FileRecord::getFolderId, fid);
        }
        applyFileTypeFilter(fq, fileType);
        fq.orderByDesc(FileRecord::getCreateTime);

        Page<FileRecord> fp = fileMapper.selectPage(new Page<>(page + 1, size), fq);

        LambdaQueryWrapper<Folder> dfq = new LambdaQueryWrapper<Folder>()
                .eq(Folder::getUserId, userId)
                .eq(Folder::getDeleted, 0);
        if (!StringUtils.hasText(keyword)) {
            dfq.eq(Folder::getParentId, fid);
        } else {
            dfq.like(Folder::getFolderName, keyword.trim());
        }
        dfq.orderByAsc(Folder::getFolderName);
        List<Folder> folders = folderMapper.selectList(dfq);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Folder f : folders) {
            items.add(folderToItem(f));
        }
        for (FileRecord f : fp.getRecords()) {
            items.add(fileToItem(f));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", items);
        result.put("totalElements", fp.getTotal() + folders.size());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private void applyFileTypeFilter(LambdaQueryWrapper<FileRecord> fq, String fileType) {
        if (!StringUtils.hasText(fileType)) return;
        switch (fileType.toLowerCase()) {
            case "image" -> fq.likeRight(FileRecord::getFileType, "image/");
            case "video" -> fq.likeRight(FileRecord::getFileType, "video/");
            case "document" -> fq.and(w -> w.likeRight(FileRecord::getFileType, "application/pdf")
                    .or().like(FileRecord::getFileName, ".doc")
                    .or().like(FileRecord::getFileName, ".docx")
                    .or().like(FileRecord::getFileName, ".xls")
                    .or().like(FileRecord::getFileName, ".xlsx")
                    .or().like(FileRecord::getFileName, ".ppt")
                    .or().like(FileRecord::getFileName, ".pptx")
                    .or().like(FileRecord::getFileName, ".txt"));
            case "archive" -> fq.and(w -> w.like(FileRecord::getFileName, ".zip")
                    .or().like(FileRecord::getFileName, ".rar")
                    .or().like(FileRecord::getFileName, ".7z"));
            default -> {
            }
        }
    }

    public FileRecord getOwned(Long id, long userId) {
        FileRecord file = fileCacheService.getById(id);
        if (file == null || !Objects.equals(file.getUserId(), userId)) {
            throw new BusinessException("文件不存在");
        }
        return file;
    }

    public FileRecord getOwnedOrShared(Long id, long userId) {
        FileRecord file = fileCacheService.getById(id);
        if (file == null) {
            throw new BusinessException("文件不存在");
        }
        if (file.getUserId().equals(userId)) {
            return file;
        }
        if (file.getFolderId() != null && file.getFolderId() > 0) {
            if (folderService.hasAccessToFolder(file.getFolderId(), userId)) {
                return file;
            }
        }
        throw new BusinessException("没有权限访问该文件");
    }

    public FileRecord createRecord(long userId, Long folderId, String fileName, long size,
                                   String mimeType, String md5, String storagePath) {
        if (folderId != null && folderId > 0) {
            folderService.getOwnedOrShared(folderId, userId);
        }
        fileValidator.validate(fileName, size);
        quotaService.checkQuota(userId, size);
        checkDuplicateFileName(userId, folderId != null ? folderId : 0L, fileName, null);
        FileRecord record = new FileRecord();
        record.setUserId(userId);
        record.setFolderId(folderId != null ? folderId : 0L);
        record.setFileName(fileName);
        record.setFileSize(size);
        record.setFileType(mimeType);
        record.setFileMd5(md5);
        record.setStoragePath(storagePath);
        record.setBucketName(storageService.bucketName());
        record.setStatus(1);
        fileMapper.insert(record);
        fileCacheService.cacheFile(record);
        quotaService.addUsage(userId, size);
        if (StringUtils.hasText(md5)) {
            cacheService.set("md5:" + md5, storagePath, MD5_CACHE_TTL);
        }
        if (MediaProcessService.isVideo(mimeType, fileName)) {
            record.setTranscodeStatus(TranscodeStatus.PENDING);
            fileMapper.updateById(record);
        }
        mediaProcessService.afterFileCreated(record);
        // ES 索引同步
        if (fileSearchService != null) {
            fileSearchService.indexFile(record);
        }
        return record;
    }

    public FileRecord simpleUpload(MultipartFile file, Long folderId) throws Exception {
        long userId = AuthService.currentUserId();
        if (file.isEmpty()) throw new BusinessException("文件为空");
        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "unnamed");
        fileValidator.validate(fileName, file.getSize());
        String storagePath = buildStoragePath(userId, fileName);
        storageService.store(file.getInputStream(), storagePath, file.getSize(), file.getContentType());
        try (var in = storageService.loadAsResource(storagePath).getInputStream()) {
            virusScanService.scan(in, fileName, file.getSize());
        }
        return createRecord(userId, folderId, fileName, file.getSize(),
                file.getContentType(), null, storagePath);
    }

    public FileRecord findByMd5(String md5) {
        return fileCacheService.getByMd5(md5);
    }

    public FileRecord instantUpload(String md5, String fileName, Long fileSize, Long folderId) {
        long userId = AuthService.currentUserId();
        FileRecord existing = findByMd5(md5);
        if (existing == null) {
            throw new BusinessException("秒传失败：文件不存在");
        }
        return createRecord(userId, folderId, fileName, fileSize, existing.getFileType(), md5, existing.getStoragePath());
    }

    public FileRecord rename(Long id, RenameRequest req) {
        long userId = AuthService.currentUserId();
        FileRecord file = getOwnedOrShared(id, userId);
        if (file.getStatus() != 1) throw new BusinessException("文件在回收站中");
        String name = req.getName();
        if (name == null || name.isBlank()) throw new BusinessException("名称不能为空");
        name = name.trim();
        checkDuplicateFileName(userId, file.getFolderId(), name, id);
        file.setFileName(name);
        fileMapper.updateById(file);
        fileCacheService.evict(file.getId());
        // ES 索引同步
        if (fileSearchService != null) {
            fileSearchService.indexFile(file);
        }
        return file;
    }

    public FileRecord move(Long id, MoveRequest req) {
        long userId = AuthService.currentUserId();
        FileRecord file = getOwnedOrShared(id, userId);
        if (file.getStatus() != 1) throw new BusinessException("文件在回收站中");
        Long targetId = req.getTargetFolderId() != null ? req.getTargetFolderId() : 0L;
        if (targetId > 0) folderService.getOwnedOrShared(targetId, userId);
        checkDuplicateFileName(userId, targetId, file.getFileName(), id);
        file.setFolderId(targetId);
        fileMapper.updateById(file);
        fileCacheService.evict(file.getId());
        // ES 索引同步
        if (fileSearchService != null) {
            fileSearchService.indexFile(file);
        }
        return file;
    }

    public FileRecord copy(Long id, MoveRequest req) {
        long userId = AuthService.currentUserId();
        FileRecord src = getOwnedOrShared(id, userId);
        if (src.getStatus() != 1) throw new BusinessException("文件在回收站中");
        Long targetId = req.getTargetFolderId() != null ? req.getTargetFolderId() : src.getFolderId();
        if (targetId > 0) folderService.getOwnedOrShared(targetId, userId);
        String copyName = generateCopyName(userId, targetId, src.getFileName());
        return createRecord(userId, targetId, copyName, src.getFileSize(),
                src.getFileType(), src.getFileMd5(), src.getStoragePath());
    }

    public void deleteToRecycle(Long id) {
        long userId = AuthService.currentUserId();
        FileRecord file = getOwnedOrShared(id, userId);
        file.setStatus(0);
        fileMapper.updateById(file);
        fileCacheService.evict(file.getId());
        quotaService.subtractUsage(userId, file.getFileSize() != null ? file.getFileSize() : 0);
        // ES 索引同步（更新 status 字段）
        if (fileSearchService != null) {
            fileSearchService.indexFile(file);
        }
    }

    public Resource download(Long id, long userId) {
        FileRecord file = getOwnedOrShared(id, userId);
        String path = resolvePlayPath(file);
        return storageService.loadAsResource(path);
    }

    /** 视频转码完成后优先返回转码 file */
    public String resolvePlayPath(FileRecord file) {
        if (TranscodeStatus.DONE.equals(file.getTranscodeStatus())
                && StringUtils.hasText(file.getTranscodePath())) {
            return file.getTranscodePath();
        }
        return file.getStoragePath();
    }

    public Resource loadThumbnail(Long id, long userId) {
        FileRecord file = getOwnedOrShared(id, userId);
        String path = StringUtils.hasText(file.getThumbnailPath()) ? file.getThumbnailPath()
                : (StringUtils.hasText(file.getPosterPath()) ? file.getPosterPath() : null);
        if (!StringUtils.hasText(path)) {
            if (file.getFileType() != null && file.getFileType().startsWith("image/")) {
                return storageService.loadAsResource(file.getStoragePath());
            }
            throw new BusinessException("缩略图不存在");
        }
        return storageService.loadAsResource(path);
    }

    public FileRecord getForDownload(Long id) {
        return fileCacheService.getById(id);
    }

    public boolean isOfficeFile(String mimeType, String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".xls") || lower.endsWith(".xlsx")
                || lower.endsWith(".ppt") || lower.endsWith(".pptx");
    }

    /** OnlyOffice 已启用且为 Office 文档时，前端才展示在线编辑入口 */
    public boolean isOfficePreviewable(String mimeType, String fileName) {
        return properties.getOnlyoffice().isEnabled() && isOfficeFile(mimeType, fileName);
    }

    public boolean isPreviewable(String mimeType, String fileName) {
        if (mimeType == null) mimeType = "";
        String lower = fileName.toLowerCase();
        if (mimeType.startsWith("image/") || mimeType.equals("application/pdf") || mimeType.startsWith("video/")) {
            return true;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".pdf")
                || lower.endsWith(".mp4") || lower.endsWith(".webm")) {
            return true;
        }
        return properties.getOnlyoffice().isEnabled() && isOfficeFile(mimeType, fileName);
    }

    public Map<String, Object> presignedDownloadUrl(Long fileId, long userId) {
        FileRecord file = getOwnedOrShared(fileId, userId);
        return presignedUrlForPath(resolvePlayPath(file));
    }

    public Map<String, Object> presignedUrlForPath(String storagePath) {
        var cdnUrl = storageService.cdnUrl(storagePath, 3600);
        if (cdnUrl.isPresent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("url", cdnUrl.get());
            m.put("expireSeconds", 3600);
            m.put("storageType", "cdn");
            m.put("bucket", storageService.bucketName());
            return m;
        }
        var url = storageService.presignedDownloadUrl(storagePath, 3600);
        if (url.isEmpty()) {
            throw new BusinessException("当前存储模式不支持直链，请使用下载接口");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", url.get());
        m.put("expireSeconds", 3600);
        m.put("storageType", storageService.storageType());
        m.put("bucket", storageService.bucketName());
        return m;
    }

    public String buildStoragePath(long userId, String fileName) {
        return userId + "/" + UUID.randomUUID().toString().replace("-", "") + "_" + sanitize(fileName);
    }

    public String chunkStoragePath(String uploadId, int chunkNo) {
        return "chunks/" + uploadId + "/" + chunkNo;
    }

    private String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void checkDuplicateFileName(long userId, Long folderId, String name, Long excludeId) {
        LambdaQueryWrapper<FileRecord> q = new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getFolderId, folderId)
                .eq(FileRecord::getFileName, name)
                .eq(FileRecord::getStatus, 1);
        if (excludeId != null) q.ne(FileRecord::getId, excludeId);
        if (fileMapper.selectCount(q) > 0) {
            throw new BusinessException("同名文件已存在");
        }
    }

    private String generateCopyName(long userId, Long folderId, String original) {
        String base = original;
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot > 0) {
            base = original.substring(0, dot);
            ext = original.substring(dot);
        }
        String candidate = base + " - 副本" + ext;
        int n = 2;
        while (true) {
            LambdaQueryWrapper<FileRecord> q = new LambdaQueryWrapper<FileRecord>()
                    .eq(FileRecord::getFolderId, folderId)
                    .eq(FileRecord::getFileName, candidate)
                    .eq(FileRecord::getStatus, 1);
            if (fileMapper.selectCount(q) == 0) return candidate;
            candidate = base + " - 副本(" + n + ")" + ext;
            n++;
        }
    }

    private Map<String, Object> fileToItem(FileRecord f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("name", f.getFileName());
        m.put("type", "file");
        m.put("sizeBytes", f.getFileSize());
        m.put("mimeType", f.getFileType());
        m.put("fileMd5", f.getFileMd5());
        m.put("folderId", f.getFolderId());
        m.put("hasThumbnail", StringUtils.hasText(f.getThumbnailPath()) || StringUtils.hasText(f.getPosterPath()));
        m.put("transcodeStatus", f.getTranscodeStatus());
        m.put("hasPoster", StringUtils.hasText(f.getPosterPath()));
        m.put("previewable", isPreviewable(f.getFileType(), f.getFileName()));
        m.put("officeFile", isOfficePreviewable(f.getFileType(), f.getFileName()));
        m.put("createdAt", f.getCreateTime());
        return m;
    }

    private Map<String, Object> folderToItem(Folder f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("name", f.getFolderName());
        m.put("type", "folder");
        m.put("parentId", f.getParentId());
        m.put("createdAt", f.getCreateTime());
        return m;
    }
}
