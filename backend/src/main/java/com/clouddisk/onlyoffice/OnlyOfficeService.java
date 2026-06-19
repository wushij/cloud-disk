package com.clouddisk.onlyoffice;

import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.service.FileService;
import com.clouddisk.storage.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlyOfficeService {

    private static final long OO_TOKEN_TTL = 3600;

    private final CloudDiskProperties properties;
    private final FileMapper fileMapper;
    private final FileService fileService;
    private final StorageService storageService;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> buildEditorConfig(Long fileId, long userId, String username) {
        ensureEnabled();
        FileRecord file = fileService.getOwnedOrShared(fileId, userId);
        if (!fileService.isOfficeFile(file.getFileType(), file.getFileName())) {
            throw new BusinessException("该文件不是 Office 格式文档");
        }
        String ext = extension(file.getFileName());
        String docType = documentType(ext);
        String ooToken = createOoToken(fileId, userId);
        String downloadUrl = properties.getOnlyoffice().getCallbackBaseUrl()
                + "/api/onlyoffice/files/" + fileId + "/download?ooToken=" + ooToken;
        String callbackUrl = properties.getOnlyoffice().getCallbackBaseUrl() + "/api/onlyoffice/callback";

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", ext);
        document.put("key", fileId + "_" + file.getUpdateTime().toString().hashCode());
        document.put("title", file.getFileName());
        document.put("url", downloadUrl);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", String.valueOf(userId));
        user.put("name", username != null ? username : "User");

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("lang", "zh-CN");
        editorConfig.put("mode", properties.getOnlyoffice().getEditMode());
        editorConfig.put("user", user);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("documentType", docType);
        config.put("document", document);
        config.put("editorConfig", editorConfig);

        signConfig(config);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentServerUrl", properties.getOnlyoffice().getDocumentServerUrl());
        result.put("config", config);
        return result;
    }

    public Resource loadForDocumentServer(Long fileId, String ooToken) {
        Long[] parsed = verifyOoToken(ooToken);
        if (parsed == null || !parsed[0].equals(fileId)) {
            throw new BusinessException("在线文档访问令牌无效");
        }
        FileRecord file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException("文件不存在");
        return storageService.loadAsResource(file.getStoragePath());
    }

    public Map<String, Object> handleCallback(Map<String, Object> body) {
        ensureEnabled();
        int status = body.get("status") instanceof Number n ? n.intValue() : 0;
        Map<String, Object> result = Map.of("error", 0);
        if (status == 2 || status == 6) {
            String url = (String) body.get("url");
            Object keyObj = body.get("key");
            if (!StringUtils.hasText(url) || keyObj == null) return result;
            try {
                long fileId = Long.parseLong(String.valueOf(keyObj).split("_")[0]);
                FileRecord file = fileMapper.selectById(fileId);
                if (file == null) return result;
                byte[] data = downloadRemote(url);
                storageService.store(new java.io.ByteArrayInputStream(data), file.getStoragePath(), data.length,
                        file.getFileType());
                log.info("OnlyOffice 文档已保存 fileId={}", fileId);
            } catch (Exception e) {
                log.error("OnlyOffice 回调保存失败: {}", e.getMessage());
                return Map.of("error", 1);
            }
        }
        return result;
    }

    private byte[] downloadRemote(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("下载编辑结果失败 HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private void signConfig(Map<String, Object> config) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(
                    properties.getOnlyoffice().getJwtSecret().getBytes(StandardCharsets.UTF_8));
            String json = objectMapper.writeValueAsString(config);
            String token = Jwts.builder().content(json, "UTF-8").signWith(key).compact();
            config.put("token", token);
        } catch (Exception e) {
            log.warn("OnlyOffice JWT 签名失败（若 Document Server 未启用 JWT 可忽略）: {}", e.getMessage());
        }
    }

    public String createOoToken(Long fileId, Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        cacheService.set("oo:" + token, fileId + ":" + userId, OO_TOKEN_TTL);
        return token;
    }

    private Long[] verifyOoToken(String token) {
        if (!StringUtils.hasText(token)) return null;
        String val = cacheService.get("oo:" + token);
        if (!StringUtils.hasText(val)) return null;
        String[] parts = val.split(":");
        return new Long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
    }

    private void ensureEnabled() {
        if (!properties.getOnlyoffice().isEnabled()) {
            throw new BusinessException("在线文档预览未启用");
        }
    }

    private String extension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i > 0 ? fileName.substring(i + 1).toLowerCase() : "docx";
    }

    private String documentType(String ext) {
        return switch (ext) {
            case "xls", "xlsx", "csv" -> "cell";
            case "ppt", "pptx" -> "slide";
            default -> "word";
        };
    }
}
