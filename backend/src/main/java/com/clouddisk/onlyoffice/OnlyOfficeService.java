package com.clouddisk.onlyoffice;

import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.service.FileService;
import com.clouddisk.storage.StorageService;
import com.clouddisk.service.TeamAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.Locale;

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
    private final OnlyOfficeJwtHelper onlyOfficeJwtHelper;
    private final TeamAccessService teamAccessService;

    public Map<String, Object> buildEditorConfig(Long fileId, long userId, String username) {
        return buildEditorConfig(fileId, userId, username, properties.getOnlyoffice().getEditMode());
    }

    public Map<String, Object> buildEditorConfig(Long fileId, long userId, String username, String mode) {
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
        String finalMode = mode != null ? mode : properties.getOnlyoffice().getEditMode();
        if ("edit".equalsIgnoreCase(finalMode)) {
            teamAccessService.requireModifyFile(file, userId);
        }
        editorConfig.put("mode", finalMode);
        editorConfig.put("user", user);

        Map<String, Object> permissions = new LinkedHashMap<>();
        boolean canEdit = "edit".equalsIgnoreCase(finalMode);
        permissions.put("edit", canEdit);
        permissions.put("comment", canEdit);
        permissions.put("review", canEdit);
        permissions.put("fillForms", canEdit);
        permissions.put("modifyFilter", canEdit);
        permissions.put("modifyContentControl", canEdit);
        editorConfig.put("permissions", permissions);

        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("forcesave", true);
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
        config.put("documentType", docType);
        config.put("document", document);
        config.put("editorConfig", editorConfig);

        onlyOfficeJwtHelper.signConfig(config);

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

    public Map<String, Object> handleCallback(Map<String, Object> body, String authorizationHeader) {
        ensureEnabled();
        Map<String, Object> payload = onlyOfficeJwtHelper.unwrapCallback(body, authorizationHeader);
        int status = payload.get("status") instanceof Number n ? n.intValue() : 0;
        Map<String, Object> result = Map.of("error", 0);
        if (status == 2 || status == 6) {
            String url = (String) payload.get("url");
            Object keyObj = payload.get("key");
            if (!StringUtils.hasText(url) || keyObj == null) return result;
            try {
                assertAllowedDownloadUrl(url);
                long fileId = Long.parseLong(String.valueOf(keyObj).split("_")[0]);
                FileRecord file = fileMapper.selectById(fileId);
                if (file == null) return result;
                byte[] data = downloadRemote(url);
                storageService.store(new java.io.ByteArrayInputStream(data), file.getStoragePath(), data.length,
                        file.getFileType());
                log.info("OnlyOffice 文档已保存 fileId={}", fileId);
            } catch (Exception e) {
                log.error("OnlyOffice 回调保存失败", e);
                return Map.of("error", 1);
            }
        }
        return result;
    }

    private void assertAllowedDownloadUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new BusinessException("OnlyOffice 回调 URL 无效");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BusinessException("OnlyOffice 回调 URL 无效");
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        Set<String> allowedHosts = collectAllowedHosts();
        if (!allowedHosts.contains(hostLower)) {
            throw new BusinessException("OnlyOffice 回调 URL 来源未授权");
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isPrivateOrLinkLocal(addr) && !allowedHosts.contains(hostLower)) {
                    throw new BusinessException("OnlyOffice 回调 URL 指向内网地址");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("OnlyOffice 回调 URL 解析失败");
        }
    }

    private Set<String> collectAllowedHosts() {
        Set<String> hosts = new HashSet<>();
        addAllowedHost(hosts, properties.getOnlyoffice().getDocumentServerUrl());
        addAllowedHost(hosts, properties.getOnlyoffice().getInternalDocumentServerUrl());
        hosts.add("onlyoffice");
        return hosts;
    }

    private void addAllowedHost(Set<String> hosts, String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return;
        }
        try {
            String host = URI.create(baseUrl).getHost();
            if (host != null) {
                hosts.add(host.toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isPrivateOrLinkLocal(InetAddress addr) {
        return addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress();
    }

    private byte[] downloadRemote(String url) throws Exception {
        String downloadUrl = url;
        String internalUrl = properties.getOnlyoffice().getInternalDocumentServerUrl();
        if (StringUtils.hasText(internalUrl)) {
            String docServerUrl = properties.getOnlyoffice().getDocumentServerUrl();
            if (url.startsWith(docServerUrl)) {
                downloadUrl = url.replace(docServerUrl, internalUrl);
            } else {
                try {
                    URI originalUri = URI.create(url);
                    URI docServerUri = URI.create(docServerUrl);
                    URI internalUri = URI.create(internalUrl);
                    boolean hostMatch = originalUri.getHost().equalsIgnoreCase(docServerUri.getHost())
                            && originalUri.getPort() == docServerUri.getPort();
                    boolean localMatch = (originalUri.getHost().equalsIgnoreCase("localhost") || originalUri.getHost().equals("127.0.0.1"))
                            && (docServerUri.getHost().equalsIgnoreCase("localhost") || docServerUri.getHost().equals("127.0.0.1"))
                            && originalUri.getPort() == docServerUri.getPort();
                    if (hostMatch || localMatch) {
                        downloadUrl = new URI(
                                internalUri.getScheme(),
                                internalUri.getAuthority(),
                                originalUri.getPath(),
                                originalUri.getQuery(),
                                originalUri.getFragment()
                        ).toString();
                    }
                } catch (Exception e) {
                    log.warn("OnlyOffice 下载 URL 重写失败，使用原始 URL: {}, error: {}", url, e.getMessage());
                }
            }
        } else {
            try {
                URI originalUri = URI.create(url);
                String host = originalUri.getHost();
                if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
                    try {
                        java.net.InetAddress.getByName("onlyoffice");
                        downloadUrl = new URI(
                                originalUri.getScheme(),
                                "onlyoffice",
                                originalUri.getPath(),
                                originalUri.getQuery(),
                                originalUri.getFragment()
                        ).toString();
                        log.info("检测到 Docker 环境，自动将 OnlyOffice 下载 URL 替换为 internal onlyoffice: {}", downloadUrl);
                    } catch (java.net.UnknownHostException ignored) {
                    }
                }
            } catch (Exception e) {
                log.warn("OnlyOffice 自动 URL 重写检查失败: {}", e.getMessage());
            }
        }

        log.info("OnlyOffice 下载远程文件, 实际请求URL={}", downloadUrl);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("下载编辑结果失败 HTTP " + resp.statusCode());
        }
        return resp.body();
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
