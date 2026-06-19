package com.clouddisk.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.dto.ShareCreateRequest;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.Folder;
import com.clouddisk.entity.ShareRecord;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.mapper.FolderMapper;
import com.clouddisk.mapper.ShareMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clouddisk.util.ClientIpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ShareService {

    private static final long SHARE_CACHE_TTL = 600;

    private final ShareMapper shareMapper;
    private final FileMapper fileMapper;
    private final FolderMapper folderMapper;
    private final FileService fileService;
    private final FolderService folderService;
    private final CacheService cacheService;
    private final CloudDiskProperties properties;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> listMine() {
        long userId = AuthService.currentUserId();
        List<ShareRecord> shares = shareMapper.selectList(new LambdaQueryWrapper<ShareRecord>()
                .eq(ShareRecord::getUserId, userId)
                .orderByDesc(ShareRecord::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ShareRecord s : shares) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("shareCode", s.getShareCode());
            m.put("shareType", s.getShareType() != null ? s.getShareType() : "FILE");
            m.put("extractCode", s.getExtractCode());
            m.put("expireTime", s.getExpireTime());
            m.put("viewCount", s.getViewCount());
            m.put("downloadCount", s.getDownloadCount());
            m.put("status", s.getStatus());
            m.put("shareUrl", "/share/" + s.getShareCode());
            m.put("createdAt", s.getCreateTime());
            if (s.isFolderShare()) {
                Folder folder = folderMapper.selectById(s.getFolderId());
                m.put("folderId", s.getFolderId());
                m.put("fileName", folder != null ? folder.getFolderName() + "（文件夹）" : "已删除");
            } else {
                FileRecord file = fileService.getForDownload(s.getFileId());
                m.put("fileId", s.getFileId());
                m.put("fileName", file != null ? file.getFileName() : "已删除");
            }
            result.add(m);
        }
        return result;
    }

    public void cancel(Long shareId) {
        long userId = AuthService.currentUserId();
        ShareRecord share = shareMapper.selectById(shareId);
        if (share == null || !share.getUserId().equals(userId)) {
            throw new BusinessException("分享不存在");
        }
        share.setStatus(0);
        shareMapper.updateById(share);
        cacheService.delete(shareCacheKey(share.getShareCode()));
    }

    public Map<String, Object> create(ShareCreateRequest req) {
        if (req.getFolderId() != null && req.getFolderId() > 0) {
            return createFolderShare(req);
        }
        return createFileShare(req);
    }

    private Map<String, Object> createFileShare(ShareCreateRequest req) {
        long userId = AuthService.currentUserId();
        if (req.getFileId() == null) throw new BusinessException("请选择文件");
        FileRecord file = fileService.getOwned(req.getFileId(), userId);
        if (file.getStatus() != 1) throw new BusinessException("文件不可用");

        ShareRecord share = buildShareRecord(req, userId);
        share.setShareType("FILE");
        share.setFileId(file.getId());
        shareMapper.insert(share);
        cacheShare(share);
        return toShareResult(share);
    }

    private Map<String, Object> createFolderShare(ShareCreateRequest req) {
        long userId = AuthService.currentUserId();
        Long folderId = req.getFolderId();
        Folder folder = folderService.getOwned(folderId, userId);
        if (folder.getDeleted() != 0) throw new BusinessException("文件夹不可用");

        ShareRecord share = buildShareRecord(req, userId);
        share.setShareType("FOLDER");
        share.setFolderId(folderId);
        shareMapper.insert(share);
        cacheShare(share);
        return toShareResult(share);
    }

    private ShareRecord buildShareRecord(ShareCreateRequest req, long userId) {
        ShareRecord share = new ShareRecord();
        share.setUserId(userId);
        share.setShareCode(generateCode());
        share.setExtractCode(StringUtils.hasText(req.getExtractCode()) ? req.getExtractCode().trim() : null);
        if (req.getExpireHours() != null && req.getExpireHours() > 0) {
            share.setExpireTime(LocalDateTime.now().plusHours(req.getExpireHours()));
        }
        share.setViewCount(0);
        share.setDownloadCount(0);
        share.setStatus(1);
        return share;
    }

    private Map<String, Object> toShareResult(ShareRecord share) {
        Map<String, Object> result = new HashMap<>();
        result.put("shareCode", share.getShareCode());
        result.put("shareType", share.getShareType());
        result.put("extractCode", share.getExtractCode());
        result.put("expireTime", share.getExpireTime());
        result.put("shareUrl", "/share/" + share.getShareCode());
        return result;
    }

    public Map<String, Object> getShareInfo(String shareCode) {
        ShareRecord share = getValidShare(shareCode);
        share.setViewCount(share.getViewCount() + 1);
        shareMapper.updateById(share);
        cacheShare(share);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shareType", share.getShareType() != null ? share.getShareType() : "FILE");
        m.put("needExtractCode", share.getExtractCode() != null);

        if (share.isFolderShare()) {
            Folder folder = folderMapper.selectById(share.getFolderId());
            if (folder == null || folder.getDeleted() != 0) {
                throw new BusinessException("分享的文件夹不存在");
            }
            m.put("folderName", folder.getFolderName());
            m.put("folderId", folder.getId());
            m.put("fileName", folder.getFolderName());
            m.put("previewable", false);
            return m;
        }

        FileRecord file = fileService.getForDownload(share.getFileId());
        m.put("fileName", file.getFileName());
        m.put("fileSize", file.getFileSize());
        m.put("mimeType", file.getFileType());
        m.put("fileId", file.getId());
        m.put("previewable", fileService.isPreviewable(file.getFileType(), file.getFileName()));
        m.put("officeFile", fileService.isOfficePreviewable(file.getFileType(), file.getFileName()));
        return m;
    }

    public List<Map<String, Object>> listFolderShareItems(String shareCode, String extractCode, Long folderId) {
        ShareRecord share = getValidShare(shareCode);
        verifyExtractCode(share, extractCode);
        if (!share.isFolderShare()) {
            throw new BusinessException("不是文件夹分享");
        }
        Long fid = folderId != null ? folderId : share.getFolderId();
        if (!isFolderInSharedTree(share.getFolderId(), fid, share.getUserId())) {
            throw new BusinessException("目录不在分享范围内");
        }
        return listFolderShareItems(share, fid);
    }

    private boolean isFolderInSharedTree(Long shareRootId, Long folderId, long userId) {
        if (Objects.equals(shareRootId, folderId)) return true;
        Folder current = folderMapper.selectById(folderId);
        while (current != null) {
            if (Objects.equals(current.getId(), shareRootId)) return true;
            if (current.getParentId() == null || current.getParentId() <= 0) break;
            current = folderMapper.selectById(current.getParentId());
            if (current == null || !Objects.equals(current.getUserId(), userId)) break;
        }
        return false;
    }

    public List<Map<String, Object>> listFolderShareItems(ShareRecord share, Long folderId) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<Folder> folders = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getUserId, share.getUserId())
                .eq(Folder::getParentId, folderId)
                .eq(Folder::getDeleted, 0)
                .orderByAsc(Folder::getFolderName));
        for (Folder f : folders) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", f.getId());
            row.put("name", f.getFolderName());
            row.put("type", "folder");
            items.add(row);
        }
        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getUserId, share.getUserId())
                .eq(FileRecord::getFolderId, folderId)
                .eq(FileRecord::getStatus, 1)
                .orderByDesc(FileRecord::getCreateTime));
        for (FileRecord f : files) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", f.getId());
            row.put("name", f.getFileName());
            row.put("type", "file");
            row.put("sizeBytes", f.getFileSize());
            row.put("mimeType", f.getFileType());
            row.put("previewable", fileService.isPreviewable(f.getFileType(), f.getFileName()));
            row.put("officeFile", fileService.isOfficePreviewable(f.getFileType(), f.getFileName()));
            items.add(row);
        }
        return items;
    }

    public FileRecord verifyAndGetFile(String shareCode, String extractCode) {
        return verifyAndGetFile(shareCode, extractCode, null);
    }

    public FileRecord verifyAndGetFile(String shareCode, String extractCode, Long fileId) {
        ShareRecord share = getValidShare(shareCode);
        verifyExtractCode(share, extractCode);
        FileRecord file = resolveSharedFile(share, fileId);
        incrementDownloadCount(share);
        return file;
    }

    public FileRecord resolveSharedFile(String shareCode, String extractCode) {
        return resolveSharedFile(shareCode, extractCode, null);
    }

    public FileRecord resolveSharedFile(String shareCode, String extractCode, Long fileId) {
        ShareRecord share = getValidShare(shareCode);
        verifyExtractCode(share, extractCode);
        return resolveSharedFile(share, fileId);
    }

    private FileRecord resolveSharedFile(ShareRecord share, Long fileId) {
        if (share.isFolderShare()) {
            if (fileId == null) {
                throw new BusinessException("请指定文件");
            }
            FileRecord file = fileMapper.selectById(fileId);
            if (file == null || !Objects.equals(file.getUserId(), share.getUserId()) || file.getStatus() != 1) {
                throw new BusinessException("文件不存在");
            }
            if (!isFileInSharedFolderTree(share.getFolderId(), file.getFolderId(), share.getUserId())) {
                throw new BusinessException("文件不在分享范围内");
            }
            return file;
        }
        return fileService.getForDownload(share.getFileId());
    }

    private void incrementDownloadCount(ShareRecord share) {
        share.setDownloadCount(share.getDownloadCount() + 1);
        shareMapper.updateById(share);
        cacheShare(share);
    }

    public Map<String, Object> listFolderShareItemsMeta(String shareCode, String extractCode, Long folderId) {
        ShareRecord share = getValidShare(shareCode);
        verifyExtractCode(share, extractCode);
        if (!share.isFolderShare()) {
            throw new BusinessException("不是文件夹分享");
        }
        Long fid = folderId != null ? folderId : share.getFolderId();
        if (!isFolderInSharedTree(share.getFolderId(), fid, share.getUserId())) {
            throw new BusinessException("目录不在分享范围内");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentFolderId", fid);
        result.put("shareRootFolderId", share.getFolderId());
        result.put("parentId", resolveParentFolderId(share, fid));
        result.put("breadcrumbs", buildShareBreadcrumbs(share, fid));
        result.put("items", listFolderShareItems(share, fid));
        return result;
    }

    public Map<String, Object> sharePresignedUrl(String shareCode, String extractCode, Long fileId) {
        FileRecord file = resolveSharedFile(shareCode, extractCode, fileId);
        return fileService.presignedUrlForPath(fileService.resolvePlayPath(file));
    }

    private Long resolveParentFolderId(ShareRecord share, Long folderId) {
        if (Objects.equals(folderId, share.getFolderId())) {
            return null;
        }
        Folder folder = folderMapper.selectById(folderId);
        if (folder == null || !Objects.equals(folder.getUserId(), share.getUserId())) {
            return share.getFolderId();
        }
        Long parentId = folder.getParentId();
        if (parentId == null || parentId <= 0 || !isFolderInSharedTree(share.getFolderId(), parentId, share.getUserId())) {
            return share.getFolderId();
        }
        return parentId;
    }

    private List<Map<String, Object>> buildShareBreadcrumbs(ShareRecord share, Long folderId) {
        List<Map<String, Object>> chain = new ArrayList<>();
        Folder current = folderMapper.selectById(folderId);
        while (current != null && Objects.equals(current.getUserId(), share.getUserId())) {
            Map<String, Object> crumb = new LinkedHashMap<>();
            crumb.put("id", current.getId());
            crumb.put("name", current.getFolderName());
            chain.add(0, crumb);
            if (Objects.equals(current.getId(), share.getFolderId())) {
                break;
            }
            if (current.getParentId() == null || current.getParentId() <= 0) {
                break;
            }
            current = folderMapper.selectById(current.getParentId());
        }
        return chain;
    }

    private boolean isFileInSharedFolderTree(Long shareRootFolderId, Long fileFolderId, long userId) {
        if (Objects.equals(shareRootFolderId, fileFolderId)) return true;
        Folder current = folderMapper.selectById(fileFolderId);
        while (current != null && current.getParentId() != null && current.getParentId() > 0) {
            if (Objects.equals(current.getParentId(), shareRootFolderId)) return true;
            if (Objects.equals(current.getId(), shareRootFolderId)) return true;
            current = folderMapper.selectById(current.getParentId());
            if (current == null || !Objects.equals(current.getUserId(), userId)) break;
        }
        return false;
    }

    public void verifyExtractCode(ShareRecord share, String extractCode) {
        if (share.getExtractCode() != null && !share.getExtractCode().equals(extractCode)) {
            recordExtractFailure(share.getShareCode());
            throw new BusinessException("提取码错误");
        }
    }

    public ShareRecord getValidShare(String shareCode) {
        ShareRecord cached = loadCachedShare(shareCode);
        if (cached != null) {
            validateShareActive(cached);
            return cached;
        }
        ShareRecord share = shareMapper.selectOne(new LambdaQueryWrapper<ShareRecord>()
                .eq(ShareRecord::getShareCode, shareCode)
                .eq(ShareRecord::getStatus, 1));
        if (share == null) throw new BusinessException("分享不存在或已失效");
        validateShareActive(share);
        cacheShare(share);
        return share;
    }

    private void validateShareActive(ShareRecord share) {
        if (share.getExpireTime() != null && share.getExpireTime().isBefore(LocalDateTime.now())) {
            share.setStatus(0);
            shareMapper.updateById(share);
            cacheService.delete(shareCacheKey(share.getShareCode()));
            throw new BusinessException("分享已过期");
        }
    }

    private void recordExtractFailure(String shareCode) {
        String ip = clientIp();
        String key = "share:fail:" + shareCode + ":" + ip;
        long count = cacheService.increment(key, 900);
        if (count > properties.getRateLimit().getShareExtractMaxAttempts()) {
            throw new BusinessException("提取码错误次数过多，请稍后再试");
        }
    }

    private ShareRecord loadCachedShare(String shareCode) {
        String json = cacheService.get(shareCacheKey(shareCode));
        if (!StringUtils.hasText(json)) return null;
        try {
            return objectMapper.readValue(json, ShareRecord.class);
        } catch (JsonProcessingException e) {
            cacheService.delete(shareCacheKey(shareCode));
            return null;
        }
    }

    private void cacheShare(ShareRecord share) {
        try {
            cacheService.set(shareCacheKey(share.getShareCode()), objectMapper.writeValueAsString(share), SHARE_CACHE_TTL);
        } catch (JsonProcessingException ignored) {
        }
    }

    private String shareCacheKey(String shareCode) {
        return "share:" + shareCode;
    }

    private String clientIp() {
        return ClientIpUtil.current();
    }

    private String generateCode() {
        for (int i = 0; i < 10; i++) {
            String code = RandomUtil.randomString(8);
            Long count = shareMapper.selectCount(new LambdaQueryWrapper<ShareRecord>()
                    .eq(ShareRecord::getShareCode, code));
            if (count == 0) return code;
        }
        return RandomUtil.randomString(12);
    }
}
