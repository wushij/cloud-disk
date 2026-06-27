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
import com.clouddisk.util.FileTypeUtils;
import com.clouddisk.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final long MD5_CACHE_TTL = 86400 * 7;

    /** 打包下载总大小上限：2 GB */
    private static final long MAX_ZIP_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;

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
    private final FolderTreeHelper folderTreeHelper;

    private final TeamAccessService teamAccessService;

    private final StoragePathService storagePathService;

    /** ElasticSearch 搜索服务（条件装配，ES 未启用时为 null） */
    @Autowired(required = false)
    private FileSearchService fileSearchService;

    public Map<String, Object> list(Long folderId, int page, int size, String keyword, String fileType) {
        long userId = AuthService.currentUserId();

        // ES 启用且有关键词时，使用 ElasticSearch 搜索（文件夹类型走数据库）
        if (fileSearchService != null && StringUtils.hasText(keyword)
                && !"folder".equalsIgnoreCase(fileType)) {
            return fileSearchService.search(userId, keyword, fileType, page, size);
        }

        long fid = folderId != null ? folderId : 0L;
        boolean typeFiltered = StringUtils.hasText(fileType);
        boolean foldersOnly = typeFiltered && "folder".equalsIgnoreCase(fileType);
        boolean sharedTeamFolder = folderService.isSharedTeamFolder(fid, userId);

        List<Folder> folders = List.of();
        if (!typeFiltered || foldersOnly) {
            LambdaQueryWrapper<Folder> dfq = new LambdaQueryWrapper<Folder>()
                    .eq(Folder::getDeleted, 0);
            if (!sharedTeamFolder) {
                dfq.eq(Folder::getUserId, userId);
            }
            if (!StringUtils.hasText(keyword)) {
                dfq.eq(Folder::getParentId, fid);
            } else {
                dfq.like(Folder::getFolderName, keyword.trim());
            }
            dfq.orderByAsc(Folder::getFolderName);
            folders = new ArrayList<>(folderMapper.selectList(dfq));

            if (fid == 0 && !StringUtils.hasText(keyword) && !typeFiltered) {
                Set<Long> existingIds = folders.stream().map(Folder::getId).collect(Collectors.toSet());
                for (Folder teamRoot : folderService.listTeamRootFoldersForUser(userId)) {
                    if (!existingIds.contains(teamRoot.getId())) {
                        folders.add(teamRoot);
                    }
                }
                folders.sort(Comparator.comparing(Folder::getFolderName, String.CASE_INSENSITIVE_ORDER));
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        Page<FileRecord> fp = new Page<>(page + 1, size);
        if (!foldersOnly) {
            LambdaQueryWrapper<FileRecord> fq = new LambdaQueryWrapper<FileRecord>()
                    .eq(FileRecord::getStatus, 1);
            if (!sharedTeamFolder) {
                fq.eq(FileRecord::getUserId, userId);
            }
            if (StringUtils.hasText(keyword)) {
                fq.like(FileRecord::getFileName, keyword.trim());
            } else {
                fq.eq(FileRecord::getFolderId, fid);
            }
            applyFileTypeFilter(fq, fileType);
            fq.orderByDesc(FileRecord::getCreateTime);
            fp = fileMapper.selectPage(fp, fq);
        }

        for (Folder f : folders) {
            Map<String, Object> item = folderToItem(f, userId);
            if (sharedTeamFolder) {
                teamAccessService.resolveForFolder(fid, userId)
                        .ifPresent(ctx -> teamAccessService.enrichFolderItem(item, f, userId, ctx));
            }
            items.add(item);
        }
        Optional<TeamAccessService.TeamContext> teamCtx = sharedTeamFolder
                ? teamAccessService.resolveForFolder(fid, userId)
                : Optional.empty();
        for (FileRecord f : fp.getRecords()) {
            Map<String, Object> item = fileToItem(f);
            teamCtx.ifPresent(ctx -> teamAccessService.enrichFileItem(item, f, userId, ctx));
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", items);
        result.put("totalElements", fp.getTotal() + folders.size());
        result.put("page", page);
        result.put("size", size);
        teamCtx.ifPresent(ctx -> result.put("teamAccess", teamAccessService.toAccessMap(ctx, userId)));
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
            if (folderService.isSharedTeamFolder(folderId, userId)) {
                teamAccessService.requireWrite(folderId, userId);
                teamAccessService.checkTeamQuota(folderId, size);
            }
        }
        fileValidator.validate(fileName, size);
        if (!StringUtils.hasText(mimeType)) {
            mimeType = FileTypeUtils.guessMimeType(fileName);
        }
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
            cacheService.set(FileCacheService.md5PathCacheKey(userId, md5), storagePath, MD5_CACHE_TTL);
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

        java.io.InputStream magicIn = file.getInputStream();
        if (!magicIn.markSupported()) {
            magicIn = new java.io.BufferedInputStream(magicIn);
        }
        fileValidator.validateMagicBytes(fileName, magicIn);

        String storagePath = buildStoragePath(userId, fileName);
        storageService.store(file.getInputStream(), storagePath, file.getSize(), file.getContentType());
        try (var in = storageService.loadAsResource(storagePath).getInputStream()) {
            virusScanService.scan(in, fileName, file.getSize());
        }
        return createRecord(userId, folderId, fileName, file.getSize(),
                file.getContentType(), null, storagePath);
    }

    /** 按当前用户 + MD5 查找可秒传文件（不跨用户）。 */
    public FileRecord findByMd5(long userId, String md5) {
        return fileCacheService.getByMd5(userId, md5);
    }

    public FileRecord instantUpload(String md5, String fileName, Long fileSize, Long folderId) {
        long userId = AuthService.currentUserId();
        FileRecord existing = findByMd5(userId, md5);
        if (existing == null) {
            throw new BusinessException("秒传失败：当前账号下不存在相同文件");
        }
        return createRecord(userId, folderId, fileName, fileSize, existing.getFileType(), md5, existing.getStoragePath());
    }

    public FileRecord rename(Long id, RenameRequest req) {
        long userId = AuthService.currentUserId();
        FileRecord file = getOwnedOrShared(id, userId);
        if (file.getStatus() != 1) throw new BusinessException("文件在回收站中");
        teamAccessService.requireModifyFile(file, userId);
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
        teamAccessService.requireModifyFile(file, userId);
        Long targetId = req.getTargetFolderId() != null ? req.getTargetFolderId() : 0L;
        if (targetId > 0) {
            folderService.getOwnedOrShared(targetId, userId);
            if (folderService.isSharedTeamFolder(targetId, userId)) {
                teamAccessService.requireWrite(targetId, userId);
            }
        }
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

    public void saveVideoPoster(Long id, String dataUrl) {
        long userId = AuthService.currentUserId();
        FileRecord file = getOwnedOrShared(id, userId);
        if (!MediaProcessService.isVideo(file)) {
            throw new BusinessException("仅视频文件支持设置封面");
        }
        if (!StringUtils.hasText(dataUrl) || !dataUrl.startsWith("data:image/")) {
            throw new BusinessException("封面数据无效");
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new BusinessException("封面数据格式错误");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("封面数据解码失败");
        }
        if (bytes.length == 0) {
            throw new BusinessException("封面内容为空");
        }
        fileValidator.validateImageContent(new ByteArrayInputStream(bytes));
        String posterPath = "posters/" + file.getUserId() + "/" + file.getId() + ".jpg";
        try {
            storageService.store(new ByteArrayInputStream(bytes), posterPath, bytes.length, "image/jpeg");
        } catch (Exception e) {
            throw new BusinessException("封面保存失败");
        }
        file.setPosterPath(posterPath);
        file.setThumbnailPath(posterPath);
        fileMapper.updateById(file);
        fileCacheService.evict(file.getId());
    }

    public FileRecord copy(Long id, MoveRequest req) {
        long userId = AuthService.currentUserId();
        FileRecord src = getOwnedOrShared(id, userId);
        if (src.getStatus() != 1) throw new BusinessException("文件在回收站中");
        teamAccessService.requireModifyFile(src, userId);
        Long targetId = req.getTargetFolderId() != null ? req.getTargetFolderId() : src.getFolderId();
        if (targetId > 0) {
            folderService.getOwnedOrShared(targetId, userId);
            if (folderService.isSharedTeamFolder(targetId, userId)) {
                teamAccessService.requireWrite(targetId, userId);
            }
        }
        String copyName = generateCopyName(userId, targetId, src.getFileName());
        return createRecord(userId, targetId, copyName, src.getFileSize(),
                src.getFileType(), src.getFileMd5(), src.getStoragePath());
    }

    public void deleteToRecycle(Long id) {
        long userId = AuthService.currentUserId();
        FileRecord file = getOwnedOrShared(id, userId);
        teamAccessService.requireDeleteFile(file, userId);
        file.setStatus(0);
        fileMapper.updateById(file);
        fileCacheService.evict(file.getId());
        quotaService.subtractUsage(file.getUserId(), file.getFileSize() != null ? file.getFileSize() : 0);
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
        return FileTypeUtils.isOfficeFile(mimeType, fileName);
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
        if (FileTypeUtils.isTextFile(mimeType, fileName)) {
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
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("url", null);
            m.put("expireSeconds", 0);
            m.put("storageType", storageService.storageType());
            m.put("bucket", storageService.bucketName());
            m.put("proxy", true);
            return m;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", url.get());
        m.put("expireSeconds", 3600);
        m.put("storageType", storageService.storageType());
        m.put("bucket", storageService.bucketName());
        return m;
    }

    public String buildStoragePath(long userId, String fileName) {
        return storagePathService.buildUserFilePath(userId, fileName);
    }

    public String chunkStoragePath(String uploadId, int chunkNo) {
        return "chunks/" + uploadId + "/" + chunkNo;
    }

    private void checkDuplicateFileName(long userId, Long folderId, String name, Long excludeId) {
        LambdaQueryWrapper<FileRecord> q = new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getFolderId, folderId)
                .eq(FileRecord::getFileName, name)
                .eq(FileRecord::getStatus, 1);
        // 个人云盘按用户隔离；团队目录同一文件夹内全局唯一
        if (!folderService.isSharedTeamFolder(folderId, userId)) {
            q.eq(FileRecord::getUserId, userId);
        }
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

    public Map<String, Object> toFileItem(FileRecord f) {
        return fileToItem(f);
    }

    public Map<String, Object> toFolderItem(Folder f) {
        return folderToItem(f);
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
        return folderToItem(f, AuthService.currentUserId());
    }

    private Map<String, Object> folderToItem(Folder f, long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("name", f.getFolderName());
        m.put("type", "folder");
        m.put("parentId", f.getParentId());
        m.put("createdAt", f.getCreateTime());
        if (folderService.isSharedTeamFolder(f.getId(), userId)) {
            m.put("teamShared", true);
        }
        return m;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ZipEntrySource {
        private String relativePath;
        private String storagePath;
    }

    private static class FolderZipNode {
        final Folder folder;
        final String zipPath;
        FolderZipNode(Folder folder, String zipPath) {
            this.folder = folder;
            this.zipPath = zipPath;
        }
    }

    private String resolveZipPathConflict(String originalName, Set<String> usedPaths, boolean isDir) {
        if (!usedPaths.contains(originalName)) {
            return originalName;
        }
        if (!isDir && originalName.contains(".")) {
            int lastDot = originalName.lastIndexOf('.');
            String base = lastDot > 0 ? originalName.substring(0, lastDot) : originalName;
            String ext = lastDot > 0 ? originalName.substring(lastDot) : "";
            int count = 1;
            while (true) {
                String newName = base + "_" + count + ext;
                if (!usedPaths.contains(newName)) {
                    return newName;
                }
                count++;
            }
        } else {
            int count = 1;
            while (true) {
                String newName = originalName + "_" + count;
                if (!usedPaths.contains(newName)) {
                    return newName;
                }
                count++;
            }
        }
    }

    public List<ZipEntrySource> prepareZipSources(List<Long> folderIds, List<Long> fileIds, long userId) {
        List<ZipEntrySource> sources = new ArrayList<>();
        Set<String> usedPaths = new HashSet<>();
        long totalSize = 0;

        log.info("prepareZipSources 开始: folderIds={}, fileIds={}, userId={}", folderIds, fileIds, userId);

        // 全局已处理的文件夹和文件 ID 集合，绝对防重防环
        Set<Long> visitedFolderIds = new HashSet<>();
        Set<Long> visitedFileIds = new HashSet<>();

        // 1. 去重并精简 folderIds，防止选中父文件夹的同时又选中了其子文件夹
        List<Long> cleanFolderIds = new ArrayList<>();
        if (folderIds != null && !folderIds.isEmpty()) {
            Set<Long> uniqueFolderIds = new LinkedHashSet<>(folderIds);
            Set<Long> allDescendantFolderIds = new HashSet<>();
            // 收集所有选中文件夹的所有子孙文件夹 ID
            for (Long fid : uniqueFolderIds) {
                List<Long> subtreeIds = folderTreeHelper.collectActiveSubtreeIds(fid);
                if (subtreeIds != null) {
                    for (Long subId : subtreeIds) {
                        if (!Objects.equals(subId, fid)) {
                            allDescendantFolderIds.add(subId);
                        }
                    }
                }
            }
            // 过滤：只有那些不属于任何其他选中文件夹的子孙的文件夹，才是顶级要打包的文件夹
            for (Long fid : uniqueFolderIds) {
                if (!allDescendantFolderIds.contains(fid)) {
                    cleanFolderIds.add(fid);
                }
            }
        }

        log.info("prepareZipSources 去重后顶级文件夹: cleanFolderIds={}", cleanFolderIds);

        // 2. 收集所有将被打包的文件夹 ID 集合（包含 cleanFolderIds 及其所有的子孙文件夹 ID）
        Set<Long> allCoveredFolderIds = new HashSet<>();
        if (!cleanFolderIds.isEmpty()) {
            for (Long fid : cleanFolderIds) {
                List<Long> subtreeIds = folderTreeHelper.collectActiveSubtreeIds(fid);
                log.info("prepareZipSources 收集子树: rootFolderId={}, subtreeIds={}", fid, subtreeIds);
                if (subtreeIds != null) {
                    allCoveredFolderIds.addAll(subtreeIds);
                }
            }
        }

        log.info("prepareZipSources allCoveredFolderIds={}", allCoveredFolderIds);

        // 3. 处理直接选中的文件（如果该文件所在的文件夹已经在打包计划中了，直接过滤掉，防重）
        if (fileIds != null) {
            Set<Long> uniqueFileIds = new LinkedHashSet<>(fileIds);
            for (Long fileId : uniqueFileIds) {
                if (visitedFileIds.contains(fileId)) continue;
                FileRecord file = getOwnedOrShared(fileId, userId);
                if (file.getStatus() != 1) continue;
                // 如果文件所属的文件夹已经属于要打包的文件夹树之一，就不再在根目录下重复打包了
                if (file.getFolderId() != null && allCoveredFolderIds.contains(file.getFolderId())) {
                    continue;
                }
                visitedFileIds.add(fileId);
                totalSize += safeSize(file.getFileSize());
                checkZipSizeLimit(totalSize);
                // 提取纯文件名：fileName 可能包含路径前缀
                String rawName = file.getFileName();
                int lastSlash = rawName.lastIndexOf('/');
                String baseName = lastSlash >= 0 ? rawName.substring(lastSlash + 1) : rawName;
                String zipPath = resolveZipPathConflict(baseName, usedPaths, false);
                usedPaths.add(zipPath);
                sources.add(new ZipEntrySource(zipPath, resolvePlayPath(file)));
            }
        }

        // 4. 处理直接选中的文件夹
        if (!cleanFolderIds.isEmpty()) {
            Set<Long> uniqueCleanFolderIds = new LinkedHashSet<>(cleanFolderIds);
            for (Long folderId : uniqueCleanFolderIds) {
                if (visitedFolderIds.contains(folderId)) continue;
                Folder rootFolder = folderService.getOwnedOrShared(folderId, userId);
                if (rootFolder.getDeleted() != 0) continue;

                String rootZipName = resolveZipPathConflict(rootFolder.getFolderName(), usedPaths, true);
                usedPaths.add(rootZipName);

                // 显式写入顶级文件夹的规范 Entry（以 / 结尾，没有物理存储路径）
                sources.add(new ZipEntrySource(rootZipName + "/", null));
                visitedFolderIds.add(rootFolder.getId());

                Queue<FolderZipNode> queue = new LinkedList<>();
                queue.offer(new FolderZipNode(rootFolder, rootZipName));

                while (!queue.isEmpty()) {
                    FolderZipNode node = queue.poll();
                    Folder currentFolder = node.folder;
                    String currentZipPath = node.zipPath;

                    log.debug("prepareZipSources BFS: folderId={}, folderName={}, zipPath={}",
                            currentFolder.getId(), currentFolder.getFolderName(), currentZipPath);

                    // 查询当前文件夹下的文件
                    List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                            .eq(FileRecord::getFolderId, currentFolder.getId())
                            .eq(FileRecord::getStatus, 1));

                    log.debug("prepareZipSources BFS文件: folderId={}, 文件数量={}, 文件列表={}",
                            currentFolder.getId(), files.size(),
                            files.stream().map(f -> f.getId() + ":" + f.getFileName()).toList());
                    
                    Set<String> localUsedNames = new HashSet<>();
                    for (FileRecord file : files) {
                        if (visitedFileIds.contains(file.getId())) continue;
                        visitedFileIds.add(file.getId());

                        totalSize += safeSize(file.getFileSize());
                        checkZipSizeLimit(totalSize);
                        // 提取纯文件名：部分文件上传时 fileName 可能包含完整相对路径（如 test/hh/123/hh.md）
                        // 而 ZIP 路径已由文件夹树构建（currentZipPath），直接拼接会导致路径重复
                        String rawFileName = file.getFileName();
                        String baseName = rawFileName;
                        int lastSlash = rawFileName.lastIndexOf('/');
                        if (lastSlash >= 0) {
                            baseName = rawFileName.substring(lastSlash + 1);
                        }
                        String resolvedFileName = resolveZipPathConflict(baseName, localUsedNames, false);
                        localUsedNames.add(resolvedFileName);
                        
                        String fileZipPath = currentZipPath + "/" + resolvedFileName;
                        sources.add(new ZipEntrySource(fileZipPath, resolvePlayPath(file)));
                    }

                    // 查询当前文件夹下的子文件夹
                    List<Folder> subfolders = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                            .eq(Folder::getParentId, currentFolder.getId())
                            .eq(Folder::getDeleted, 0));

                    log.debug("prepareZipSources BFS子文件夹: parentId={}, 子文件夹数量={}, 子文件夹列表={}",
                            currentFolder.getId(), subfolders.size(),
                            subfolders.stream().map(f -> f.getId() + ":" + f.getFolderName()).toList());
                    
                    Set<String> localUsedFolders = new HashSet<>();
                    for (Folder sub : subfolders) {
                        if (visitedFolderIds.contains(sub.getId())) continue;
                        visitedFolderIds.add(sub.getId());

                        String subFolderName = sub.getFolderName();
                        String resolvedSubFolder = resolveZipPathConflict(subFolderName, localUsedFolders, true);
                        localUsedFolders.add(resolvedSubFolder);

                        String subZipPath = currentZipPath + "/" + resolvedSubFolder;
                        // 显式写入子文件夹的规范 Entry
                        sources.add(new ZipEntrySource(subZipPath + "/", null));

                        queue.offer(new FolderZipNode(sub, subZipPath));
                    }
                }
            }
        }

        log.info("打包下载准备完成: {} 个实体(含文件与目录), 总大小 {} MB (userId={})", sources.size(), totalSize / 1024 / 1024, userId);
        return sources;
    }

    private static long safeSize(Long size) {
        return size != null ? size : 0;
    }

    private static void checkZipSizeLimit(long totalSize) {
        if (totalSize > MAX_ZIP_TOTAL_BYTES) {
            throw new BusinessException("打包文件总大小超过 2GB 上限，请减少选择后重试");
        }
    }

    public Resource loadStorageResource(String storagePath) {
        return storageService.loadAsResource(storagePath);
    }

    public String getFolderName(Long folderId, long userId) {
        try {
            return folderService.getOwnedOrShared(folderId, userId).getFolderName();
        } catch (Exception e) {
            return "archive";
        }
    }
}

