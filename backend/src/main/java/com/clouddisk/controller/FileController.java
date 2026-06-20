package com.clouddisk.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.clouddisk.common.BusinessException;
import com.clouddisk.dto.MoveRequest;
import com.clouddisk.dto.RenameRequest;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.service.FileService;
import com.clouddisk.service.AuthService;
import com.clouddisk.search.FileSearchService;
import com.clouddisk.util.AuthHelper;
import com.clouddisk.util.FileTypeUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** ES 搜索服务（条件装配，ES 未启用时为 null） */
    @Autowired(required = false)
    private FileSearchService fileSearchService;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String fileType) {
        return fileService.list(folderId, page, size, q, fileType);
    }

    @PostMapping("/simple")
    @SentinelResource(value = "simple_upload", blockHandler = "simpleUploadBlocked")
    public FileRecord simpleUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "0") Long folderId) throws Exception {
        return fileService.simpleUpload(file, folderId);
    }

    public static FileRecord simpleUploadBlocked(MultipartFile file, Long folderId, BlockException ex) {
        throw new BusinessException("上传过于频繁，请稍后再试");
    }

    @PutMapping("/{id}/rename")
    public FileRecord rename(@PathVariable Long id, @RequestBody RenameRequest req) {
        return fileService.rename(id, req);
    }

    @PutMapping("/{id}/move")
    public FileRecord move(@PathVariable Long id, @RequestBody MoveRequest req) {
        return fileService.move(id, req);
    }

    @PostMapping("/{id}/copy")
    public FileRecord copy(@PathVariable Long id, @RequestBody MoveRequest req) {
        return fileService.copy(id, req);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        fileService.deleteToRecycle(id);
        return Map.of("message", "已移入回收站");
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, HttpServletRequest request) {
        long userId = AuthHelper.requireUserId(request);
        FileRecord file = fileService.getOwnedOrShared(id, userId);
        Resource resource = fileService.download(id, userId);
        String encoded = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/download/zip")
    @SentinelResource(value = "download_zip", blockHandler = "downloadZipBlocked")
    public void downloadZip(
            @RequestParam(required = false) List<Long> folderIds,
            @RequestParam(required = false) List<Long> fileIds,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        long userId = AuthHelper.requireUserId(request);

        List<FileService.ZipEntrySource> sources = fileService.prepareZipSources(folderIds, fileIds, userId);
        if (sources.isEmpty()) {
            throw new BusinessException("没有可下载的文件");
        }

        response.setContentType("application/zip");
        String zipName = resolveZipName(folderIds, fileIds, userId);

        String encoded = URLEncoder.encode(zipName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            int failedCount = 0;
            for (FileService.ZipEntrySource source : sources) {
                try {
                    if (source.getStoragePath() == null) {
                        // 这是一个纯文件夹 Entry，规范命名须以 / 结尾
                        String dirPath = source.getRelativePath();
                        if (!dirPath.endsWith("/")) {
                            dirPath += "/";
                        }
                        ZipEntry zipEntry = new ZipEntry(dirPath);
                        zos.putNextEntry(zipEntry);
                        zos.closeEntry();
                    } else {
                        // 这是一个文件 Entry
                        Resource resource = fileService.loadStorageResource(source.getStoragePath());
                        ZipEntry zipEntry = new ZipEntry(source.getRelativePath());
                        zos.putNextEntry(zipEntry);
                        try (InputStream is = resource.getInputStream()) {
                            is.transferTo(zos);
                        }
                        zos.closeEntry();
                    }
                } catch (Exception e) {
                    failedCount++;
                    log.warn("打包下载跳过项目 [{}]: {}", source.getRelativePath(), e.getMessage());
                }
            }
            if (failedCount > 0) {
                log.warn("打包下载完成，共跳过 {} 个失败项目 (userId={})", failedCount, userId);
            }
        }
    }

    public static void downloadZipBlocked(List<Long> folderIds, List<Long> fileIds,
                                          HttpServletRequest request, HttpServletResponse response,
                                          BlockException ex) {
        throw new BusinessException("下载过于频繁，请稍后再试");
    }

    private String resolveZipName(List<Long> folderIds, List<Long> fileIds, long userId) {
        if (folderIds != null && folderIds.size() == 1) {
            return fileService.getFolderName(folderIds.get(0), userId) + ".zip";
        }
        if (folderIds != null && folderIds.size() > 1) {
            return "打包下载.zip";
        }
        if (fileIds != null && !fileIds.isEmpty()) {
            try {
                String name = fileService.getOwnedOrShared(fileIds.get(0), userId).getFileName();
                int lastSep = name.lastIndexOf('/');
                if (lastSep >= 0) name = name.substring(lastSep + 1);
                int lastDot = name.lastIndexOf('.');
                return (lastDot > 0 ? name.substring(0, lastDot) : name) + ".zip";
            } catch (Exception e) {
                // fallback
            }
        }
        return "archive.zip";
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id, HttpServletRequest request) {
        long userId = AuthHelper.requireUserId(request);
        FileRecord file = fileService.getOwnedOrShared(id, userId);
        if (!fileService.isPreviewable(file.getFileType(), file.getFileName())) {
            return ResponseEntity.badRequest().build();
        }
        Resource resource = fileService.download(id, userId);
        MediaType mediaType = resolveMediaType(file.getFileType(), file.getFileName());
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Resource> thumbnail(@PathVariable Long id, HttpServletRequest request) {
        long userId = AuthHelper.requireUserId(request);
        Resource resource = fileService.loadThumbnail(id, userId);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(resource);
    }

    /** MinIO 预签名直链（设计文档 CDN/对象存储加速） */
    @GetMapping("/{id}/direct-url")
    public Map<String, Object> directUrl(@PathVariable Long id) {
        return fileService.presignedDownloadUrl(id, AuthService.currentUserId());
    }

    /**
     * 全文搜索接口（ElasticSearch）
     * 支持中文全文搜索 + 拼音搜索 + 高亮显示
     * ES 未启用时回退到 MySQL LIKE 查询
     */
    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String fileType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (fileSearchService != null) {
            return fileSearchService.search(AuthService.currentUserId(), keyword, fileType, page, size);
        }
        // ES 未启用时回退到 MySQL LIKE 查询
        return fileService.list(0L, page, size, keyword, fileType);
    }

    /** 安全解析 MediaType，避免非法 MIME 字符串导致 500 */
    private MediaType resolveMediaType(String mimeType, String fileName) {
        if (FileTypeUtils.isTextFile(mimeType, fileName)) {
            return new MediaType("text", "plain", StandardCharsets.UTF_8);
        }
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
