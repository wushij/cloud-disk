package com.clouddisk.controller;

import com.clouddisk.dto.ShareCreateRequest;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.util.FileTypeUtils;
import com.clouddisk.onlyoffice.ShareOnlyOfficeService;
import com.clouddisk.service.FileService;
import com.clouddisk.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final FileService fileService;
    private final ShareOnlyOfficeService shareOnlyOfficeService;

    @PostMapping("/api/share")
    public Map<String, Object> create(@RequestBody ShareCreateRequest req) {
        return shareService.create(req);
    }

    @GetMapping("/api/share/mine")
    public List<Map<String, Object>> listMine() {
        return shareService.listMine();
    }

    @DeleteMapping("/api/share/{id}")
    public Map<String, String> cancel(@PathVariable Long id) {
        shareService.cancel(id);
        return Map.of("message", "已取消分享");
    }

    @GetMapping("/share/{code}")
    public Map<String, Object> info(@PathVariable String code) {
        return shareService.getShareInfo(code);
    }

    @GetMapping("/share/{code}/items")
    public Map<String, Object> folderItems(
            @PathVariable String code,
            @RequestParam(required = false) String extractCode,
            @RequestParam(required = false) Long folderId) {
        return shareService.listFolderShareItemsMeta(code, extractCode, folderId);
    }

    @PostMapping("/share/{code}/access")
    public Map<String, Object> access(@PathVariable String code, @RequestBody Map<String, String> body) {
        var share = shareService.getValidShare(code);
        shareService.verifyExtractCode(share, body.get("extractCode"));
        if (share.isFolderShare()) {
            return Map.of("shareType", "FOLDER", "ok", true);
        }
        FileRecord file = shareService.resolveSharedFile(code, body.get("extractCode"), null);
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("fileId", file.getId());
        m.put("fileName", file.getFileName());
        m.put("fileSize", file.getFileSize());
        m.put("mimeType", file.getFileType());
        m.put("previewable", fileService.isPreviewable(file.getFileType(), file.getFileName()));
        return m;
    }

    @GetMapping("/share/{code}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String code,
            @RequestParam(required = false) String extractCode,
            @RequestParam(required = false) Long fileId) {
        FileRecord file = shareService.verifyAndGetFile(code, extractCode, fileId);
        Resource resource = fileService.download(file.getId(), file.getUserId());
        String encoded = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/share/{code}/preview")
    public ResponseEntity<Resource> preview(
            @PathVariable String code,
            @RequestParam(required = false) String extractCode,
            @RequestParam(required = false) Long fileId) {
        FileRecord file = shareService.resolveSharedFile(code, extractCode, fileId);
        if (!fileService.isPreviewable(file.getFileType(), file.getFileName())) {
            return ResponseEntity.badRequest().build();
        }
        Resource resource = fileService.download(file.getId(), file.getUserId());
        MediaType mediaType = FileTypeUtils.isTextFile(file.getFileType(), file.getFileName())
                ? new MediaType("text", "plain", StandardCharsets.UTF_8)
                : (file.getFileType() != null
                ? MediaType.parseMediaType(file.getFileType())
                : MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    @GetMapping("/share/{code}/direct-url")
    public Map<String, Object> shareDirectUrl(
            @PathVariable String code,
            @RequestParam Long fileId,
            @RequestParam(required = false) String extractCode) {
        return shareService.sharePresignedUrl(code, extractCode, fileId);
    }

    @GetMapping("/share/{code}/onlyoffice/{fileId}")
    public Map<String, Object> shareOnlyOffice(
            @PathVariable String code,
            @PathVariable Long fileId,
            @RequestParam(required = false) String extractCode) {
        return shareOnlyOfficeService.buildEditorConfig(code, extractCode, fileId);
    }

    @GetMapping("/share/{code}/onlyoffice/files/{fileId}/download")
    public ResponseEntity<Resource> shareOnlyOfficeDownload(
            @PathVariable String code,
            @PathVariable Long fileId,
            @RequestParam String ooToken) {
        Resource resource = shareOnlyOfficeService.loadForDocumentServer(code, fileId, ooToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
