package com.clouddisk.onlyoffice;

import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.ShareRecord;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.service.FileService;
import com.clouddisk.service.ShareService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareOnlyOfficeService {

    private static final long OO_TOKEN_TTL = 3600;

    private final CloudDiskProperties properties;
    private final ShareService shareService;
    private final FileService fileService;
    private final FileMapper fileMapper;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> buildEditorConfig(String shareCode, String extractCode, Long fileId) {
        if (!properties.getOnlyoffice().isEnabled()) {
            throw new BusinessException("在线文档预览未启用");
        }
        ShareRecord share = shareService.getValidShare(shareCode);
        shareService.verifyExtractCode(share, extractCode);
        FileRecord file = shareService.resolveSharedFile(shareCode, extractCode, fileId);
        if (!fileService.isOfficeFile(file.getFileType(), file.getFileName())) {
            throw new BusinessException("该文件不是 Office 格式文档");
        }

        String ext = extension(file.getFileName());
        String ooToken = createOoToken(shareCode, fileId);
        String downloadUrl = properties.getOnlyoffice().getCallbackBaseUrl()
                + "/share/" + shareCode + "/onlyoffice/files/" + fileId + "/download?ooToken=" + ooToken;

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", ext);
        document.put("key", "share_" + fileId + "_" + file.getUpdateTime().toString().hashCode());
        document.put("title", file.getFileName());
        document.put("url", downloadUrl);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", "share_" + shareCode);
        user.put("name", "访客");

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("lang", "zh-CN");
        editorConfig.put("mode", "view");
        editorConfig.put("user", user);

        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("edit", false);
        permissions.put("comment", false);
        permissions.put("review", false);
        permissions.put("fillForms", false);
        permissions.put("modifyFilter", false);
        permissions.put("modifyContentControl", false);
        editorConfig.put("permissions", permissions);

        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("forcesave", false);
        customization.put("chat", false);
        customization.put("help", false);
        customization.put("goback", false);
        customization.put("plugins", false);
        customization.put("statusBar", false);
        customization.put("features", Map.of("spellcheck", Map.of("mode", false)));

        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("statusBar", false);
        customization.put("layout", layout);

        editorConfig.put("customization", customization);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("documentType", documentType(ext));
        config.put("document", document);
        config.put("editorConfig", editorConfig);
        signConfig(config);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentServerUrl", properties.getOnlyoffice().getDocumentServerUrl());
        result.put("config", config);
        return result;
    }

    public Resource loadForDocumentServer(String shareCode, Long fileId, String ooToken) {
        verifyOoToken(shareCode, fileId, ooToken);
        FileRecord file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException("文件不存在");
        return fileService.download(file.getId(), file.getUserId());
    }

    private void signConfig(Map<String, Object> config) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(
                    properties.getOnlyoffice().getJwtSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String json = objectMapper.writeValueAsString(config);
            String token = Jwts.builder().content(json, "UTF-8").signWith(key).compact();
            config.put("token", token);
        } catch (Exception e) {
            log.warn("OnlyOffice JWT 签名失败: {}", e.getMessage());
        }
    }

    private String createOoToken(String shareCode, Long fileId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        cacheService.set("oo:share:" + token, shareCode + ":" + fileId, OO_TOKEN_TTL);
        return token;
    }

    private void verifyOoToken(String shareCode, Long fileId, String token) {
        if (!StringUtils.hasText(token)) throw new BusinessException("在线文档访问令牌无效");
        String val = cacheService.get("oo:share:" + token);
        if (!StringUtils.hasText(val) || !val.equals(shareCode + ":" + fileId)) {
            throw new BusinessException("在线文档访问令牌无效");
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
