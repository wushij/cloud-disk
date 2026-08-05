package com.clouddisk.security;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.symmetric.SM4;
import com.clouddisk.cache.CacheService;
import com.clouddisk.config.CloudDiskProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 全链路 API 安全防护过滤器
 * <p>
 * 支持：时间戳校验(5分钟窗口)、Nonce随机数防重放(Redis查重)、
 * HMAC-SM3数字签名 与 SM4-CBC 接口加解密。
 * <p>
 * 密钥优先从 Redis 取会话临时密钥（POST /api/auth/session-sign-init 协商），
 * 回退到 CloudDiskProperties 中配置的静态密钥（兼容旧客户端）。
 */
@Component
@RequiredArgsConstructor
public class ApiSecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiSecurityFilter.class);

    public static final String HEADER_TIMESTAMP       = "X-Timestamp";
    public static final String HEADER_NONCE           = "X-Nonce";
    public static final String HEADER_SIGNATURE       = "X-Signature";
    public static final String HEADER_ENCRYPTED       = "X-Encrypted";
    public static final String HEADER_ACCEPT_ENCRYPTED = "X-Accept-Encrypted";
    public static final String HEADER_CLIENT_ID       = "X-Client-Id";

    /** 会话签名密钥 Redis 前缀 */
    public static final String SESSION_SIGN_KEY_PREFIX = "security:session-sign:";
    /** 会话 SM4 加密密钥 Redis 前缀 */
    public static final String SESSION_SM4_KEY_PREFIX  = "security:session-sm4:";

    /**
     * SM4-CBC 带认证标签加密格式的前缀标记。
     * 完整格式：V1 + IV(32Hex) + MAC(64Hex) + CBC_CT(Hex)
     * MAC = HMAC-SM3(IV_hex + CBC_CT_hex, sm4Key)，用于检测密文篡改（防 bit-flipping）。
     * 无此前缀的密文按旧版纯 CBC（IV + CT）兼容解密。
     */
    private static final String SM4_AUTH_PREFIX = "V1";

    /** 时间戳校验窗口：5 分钟 */
    private static final long MAX_TIMESTAMP_DIFF_MS = 5 * 60 * 1000L;

    /** 完全跳过本 Filter 的路径前缀（静态资源、文件流等） */
    private static final Set<String> EXCLUDE_PATH_PREFIXES = Set.of(
            "/swagger-ui",
            "/v3/api-docs",
            "/doc.html",
            "/webjars",
            "/favicon.ico"
    );

    /** 公开认证接口（无需签名，但时间戳/Nonce 照常验） */
    private static final Set<String> PUBLIC_AUTH_URIS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/captcha",
            "/api/auth/captcha/required",
            "/api/auth/captcha/image",
            "/api/auth/config",
            "/api/auth/providers",
            "/api/auth/ldap/login",
            "/api/auth/session-sign-init",
            "/api/auth/sso/ticket",
            "/api/auth/sync-cookie"
    );

    private boolean isPublicAuthUri(String servletPath) {
        if (StrUtil.isBlank(servletPath)) return false;
        if (PUBLIC_AUTH_URIS.contains(servletPath)) return true;
        return servletPath.startsWith("/api/auth/captcha")
                || servletPath.startsWith("/api/auth/config")
                || servletPath.startsWith("/api/auth/providers");
    }

    /** 分享类接口：无登录态，跳过签名校验（时间戳/Nonce 仍然执行） */
    private static final String SHARE_PATH_PREFIX = "/share/";

    private final CloudDiskProperties properties;
    private final SecurityConfigService securityConfigService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) return true;
        
        // 1. 文档与静态资源
        for (String prefix : EXCLUDE_PATH_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }

        // 2. WebSocket 长连接握手放行
        if (uri.startsWith("/ws/") || uri.equals("/ws")) return true;

        // 3. 浏览器原生 <img>/<video> 标签加载的头像、媒体流、文件下载与预览接口放行（不走 Axios 无法携带自定义 X-Header）
        //    使用精确路径段匹配（而非子串 contains），避免误放行 /downloadLogs、/previewHistory 等无关接口
        if (matchesMediaPath(uri)) {
            return true;
        }

        return false;
    }

    /** 需要被浏览器原生标签（<img>/<video>）访问的媒体/文件流路径段集合 */
    private static final Set<String> MEDIA_PATH_SEGMENTS = Set.of(
            "avatar", "download", "preview", "stream", "thumbnail"
    );

    /**
     * 精确路径段匹配（区别于子串 contains）：
     * 仅当 URI 中某个路径段正好等于 avatar/download/preview/stream 之一时放行。
     * 例如 /api/files/{id}/download 放行，而 /api/files/downloadLogs 或 /api/downloadAgent 不放行。
     */
    private boolean matchesMediaPath(String uri) {
        if (StrUtil.isBlank(uri)) return false;
        String[] segments = uri.split("/");
        for (String seg : segments) {
            if (MEDIA_PATH_SEGMENTS.contains(seg)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String servletPath = request.getServletPath();

        // 安全配置自身修改接口：避免"既改安全配置又要求安全配置通过"的死锁。
        // 该接口有管理员 Sa-Token 强鉴权，本 Filter 跳过签名/SM4/Body 包装等全部处理，原 Request 直接放行。
        if ("/api/admin/security/config".equals(servletPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String contentType = request.getContentType();
        boolean isMultipart = contentType != null && contentType.startsWith("multipart/form-data");

        boolean timestampEnabled   = securityConfigService.isTimestampEnabled();
        boolean nonceEnabled       = securityConfigService.isNonceEnabled();
        boolean signEnabled        = securityConfigService.isSm3SignEnabled();
        boolean sm4EncryptEnabled  = securityConfigService.isSm4EncryptEnabled();

        String uri = request.getRequestURI();
        // 分享页面接口：跳过签名（无登录态，无 clientId），但时间戳/Nonce 照常
        boolean isSharePath = uri != null && (uri.startsWith(SHARE_PATH_PREFIX)
                || uri.startsWith("/api" + SHARE_PATH_PREFIX));

        // ── 1. 时间戳校验 ──────────────────────────────────────────────
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);
        if (timestampEnabled) {
            if (StrUtil.isBlank(timestampStr)) {
                writeError(response, 403, "请求已被拒绝：缺失 X-Timestamp 请求头");
                return;
            }
            try {
                long ts = Long.parseLong(timestampStr);
                long windowMs = securityConfigService.getTimestampWindowMs();
                long now = System.currentTimeMillis();
                // 过去方向允许 windowMs（默认5分钟），未来方向严格限制在 30 秒内（防提前预签囤积）
                if ((now - ts) > windowMs || (ts - now) > 30000L) {
                    writeError(response, 403, "请求已被拒绝：时间戳无效或已过期");
                    return;
                }
            } catch (NumberFormatException e) {
                writeError(response, 403, "请求已被拒绝：非法时间戳格式");
                return;
            }
        }

        // ── 2. Nonce 防重放校验 ─────────────────────────────────────
        String nonce = request.getHeader(HEADER_NONCE);
        if (nonceEnabled) {
            if (StrUtil.isBlank(nonce)) {
                writeError(response, 403, "请求已被拒绝：缺失 X-Nonce 请求头");
                return;
            }
            try {
                String clientIdForNonce = request.getHeader(HEADER_CLIENT_ID);
                String nonceRedisKey = StrUtil.isNotBlank(clientIdForNonce)
                        ? "security:nonce:" + clientIdForNonce + ":" + nonce
                        : "security:nonce:" + nonce;
                long windowMs = securityConfigService.getTimestampWindowMs();
                long nonceTtlSeconds = Math.max(10L, (windowMs * 2) / 1000L);
                Boolean ok = stringRedisTemplate.opsForValue()
                        .setIfAbsent(nonceRedisKey, "1", nonceTtlSeconds, TimeUnit.SECONDS);
                if (Boolean.FALSE.equals(ok)) {
                    writeError(response, 403, "请求已被拒绝：检测到重复请求 (Anti-Replay)");
                    return;
                }
            } catch (Exception e) {
                log.warn("Nonce Redis 校验异常，降级通过: {}", e.getMessage());
            }
        }

        // 若是 multipart 文件上传，不处理 Body 加解密，其他校验完成后直接放行
        if (isMultipart) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── 3. 读取原始 Body ───────────────────────────────────────────
        byte[] rawBodyBytes = request.getInputStream().readAllBytes();
        String rawBodyString = new String(rawBodyBytes, StandardCharsets.UTF_8);
        String effectiveBodyStr = rawBodyString.trim();
        if (effectiveBodyStr.startsWith("\"") && effectiveBodyStr.endsWith("\"")
                && effectiveBodyStr.length() > 2) {
            effectiveBodyStr = effectiveBodyStr.substring(1, effectiveBodyStr.length() - 1);
        }

        // ── 4. HMAC-SM3 签名校验 ───────────────────────────────────────
        String signature = request.getHeader(HEADER_SIGNATURE);
        if (signEnabled && !isSharePath) {
            servletPath = StrUtil.nullToEmpty(request.getServletPath());
            boolean isPublic = isPublicAuthUri(servletPath);

            if (StrUtil.isBlank(signature)) {
                if (!isPublic) {
                    writeError(response, 403, "请求已被拒绝：缺失 X-Signature 签名头");
                    return;
                }
            } else {
                String signKey = resolveSignKey(request);
                if (StrUtil.isBlank(signKey)) {
                    if (!isPublic) {
                        writeError(response, 403,
                                "请求已被拒绝：签名密钥缺失或已过期，请重新初始化会话");
                        return;
                    }
                } else {
                    try {
                        String queryString = request.getQueryString();

                        // 与前端保持一致：去掉 /api 前缀，前端系一任做了 substring(4)
                        String normalizedPath = servletPath;
                        if (normalizedPath.startsWith("/api/")) {
                            normalizedPath = normalizedPath.substring(4); // 去掉 "/api"，保留 "/xxx"
                        } else if (normalizedPath.startsWith("/api") && normalizedPath.length() == 4) {
                            normalizedPath = "/";
                        }

                        String fullPath = normalizedPath +
                                (StrUtil.isNotBlank(queryString) ? "?" + queryString : "");
                        try { fullPath = URLDecoder.decode(fullPath, StandardCharsets.UTF_8); }
                        catch (Exception ignored) {}

                        String clientIdHeader = StrUtil.nullToEmpty(request.getHeader(HEADER_CLIENT_ID));
                        String tokenHeader = StrUtil.nullToEmpty(request.getHeader("Authorization"));
                        String tokenSummary = tokenHeader.length() > 16
                                ? tokenHeader.substring(tokenHeader.length() - 16)
                                : tokenHeader;

                        String signContent = request.getMethod() + "\n"
                                + fullPath + "\n"
                                + StrUtil.nullToEmpty(timestampStr) + "\n"
                                + StrUtil.nullToEmpty(nonce) + "\n"
                                + clientIdHeader + "\n"
                                + tokenSummary + "\n"
                                + effectiveBodyStr;

                        byte[] keyBytes = signKey.getBytes(StandardCharsets.UTF_8);
                        HMac hmac = SmUtil.hmacSm3(keyBytes);
                        String expected = hmac.digestHex(signContent);

                        // 恒定时间比较，避免 equalsIgnoreCase 逐字符短路导致的时序侧信道
                        boolean sigMatch = signature != null
                                && MessageDigest.isEqual(
                                        expected.toLowerCase().getBytes(StandardCharsets.UTF_8),
                                        signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
                        if (!sigMatch) {
                            // 诊断：打印签名内容前 200 字符、expected 前 8 字符、received 前 8 字符
                            log.warn("签名验证失败 path={}, sigContentPrefix={}, expectedPrefix={}, receivedPrefix={}",
                                    request.getRequestURI(),
                                    signContent.length() > 200 ? signContent.substring(0, 200) + "..." : signContent,
                                    expected.length() >= 8 ? expected.substring(0, 8) : expected,
                                    signature != null && signature.length() >= 8 ? signature.substring(0, 8) : signature);
                            writeError(response, 403, "请求已被拒绝：数字签名验证失败");
                            return;
                        }
                    } catch (Exception e) {
                        // 打印完整堆栈，便于定位签名验证内部异常
                        log.error("签名验证异常 path={}, sigLength={}", request.getRequestURI(),
                                signature == null ? -1 : signature.length(), e);
                        writeError(response, 403, "请求已被拒绝：数字签名校验异常");
                        return;
                    }
                }
            }
        }

        // ── 5. SM4-CBC 解密请求体（带 HMAC-SM3 认证标签校验完整性）──────
        byte[] dispatchBodyBytes = rawBodyBytes;
        String encryptedHeader = request.getHeader(HEADER_ENCRYPTED);
        boolean isRequestEncrypted = "1".equals(encryptedHeader) || "true".equalsIgnoreCase(encryptedHeader);
        if (isRequestEncrypted && StrUtil.isNotBlank(rawBodyString)) {
            String sm4Key = resolveEncryptKey(request);
            if (StrUtil.isBlank(sm4Key)) {
                writeError(response, 403, "请求已被拒绝：服务端加密密钥未配置");
                return;
            }
            try {
                String cipherText = rawBodyString.trim();
                if (cipherText.startsWith("\"") && cipherText.endsWith("\"") && cipherText.length() > 2) {
                    cipherText = cipherText.substring(1, cipherText.length() - 1);
                }
                byte[] keyBytes = toSm4KeyBytes(sm4Key);

                String decryptedBody;
                if (cipherText.startsWith(SM4_AUTH_PREFIX)) {
                    // 新版带认证标签格式：V1 + IV(32Hex) + MAC(64Hex) + CBC_CT(Hex)
                    decryptedBody = sm4CbcDecryptWithAuth(cipherText, keyBytes);
                } else {
                    // 旧版兼容格式：IV(32Hex) + CBC_CT(Hex)
                    if (cipherText.length() <= 32 || !cipherText.substring(0, 32).matches("^[0-9a-fA-F]{32}$")) {
                        writeError(response, 403, "请求已被拒绝：SM4 密文格式非法");
                        return;
                    }
                    byte[] ivBytes = HexUtil.decodeHex(cipherText.substring(0, 32));
                    SM4 cbcSm4 = new SM4(Mode.CBC, Padding.PKCS5Padding, keyBytes, ivBytes);
                    decryptedBody = cbcSm4.decryptStr(cipherText.substring(32));
                }
                dispatchBodyBytes = decryptedBody.getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.error("SM4-CBC 请求体解密失败: {}", e.getMessage());
                writeError(response, 403, "请求已被拒绝：SM4 请求解密失败");
                return;
            }
        }

        // ── 6. 重构可重读的 HttpServletRequest ────────────────────────
        byte[] finalBodyBytes = dispatchBodyBytes;
        HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                ByteArrayInputStream bais = new ByteArrayInputStream(finalBodyBytes);
                return new ServletInputStream() {
                    @Override public boolean isFinished() { return bais.available() == 0; }
                    @Override public boolean isReady()    { return true; }
                    @Override public void setReadListener(ReadListener rl) {}
                    @Override public int read() { return bais.read(); }
                };
            }
        };

        // ── 7. SM4-CBC 加密响应体 ──────────────────────────────────────
        String acceptEncHeader = request.getHeader(HEADER_ACCEPT_ENCRYPTED);
        boolean wantsEncResp = "1".equals(acceptEncHeader) || "true".equalsIgnoreCase(acceptEncHeader);
        boolean isSessionSignInit = "/api/auth/session-sign-init".equals(servletPath);
        boolean shouldEncResp = sm4EncryptEnabled && !isSessionSignInit && (isRequestEncrypted || wantsEncResp);

        if (shouldEncResp && !response.isCommitted()) {
            ContentCachingResponseWrapper wrappedResp = new ContentCachingResponseWrapper(response);
            filterChain.doFilter(wrappedRequest, wrappedResp);

            String respContentType = wrappedResp.getContentType();
            byte[] respContent = wrappedResp.getContentAsByteArray();
            String sm4Key = resolveEncryptKey(request);

            // 仅对未提交、响应大小在 5MB 以内且 Content-Type 为 JSON 的响应体进行加密，防止大文件 OOM 和二进制破损
            boolean isJsonResp = respContentType != null && (respContentType.contains("application/json") || respContentType.contains("text/json"));
            if (respContent.length > 0 && respContent.length <= 5 * 1024 * 1024 && isJsonResp && StrUtil.isNotBlank(sm4Key) && !response.isCommitted()) {
                try {
                    String plainResp = new String(respContent, StandardCharsets.UTF_8);
                    byte[] keyBytes = toSm4KeyBytes(sm4Key);
                    // 带认证标签的加密格式：V1 + IV(32Hex) + MAC(64Hex) + CBC_CT(Hex)，保证响应完整性
                    String encryptedRespStr = sm4CbcEncryptWithAuth(plainResp, keyBytes);
                    byte[] encryptedRespBytes = encryptedRespStr.getBytes(StandardCharsets.UTF_8);

                    response.setHeader(HEADER_ENCRYPTED, "1");
                    response.setContentType("application/json;charset=UTF-8");
                    response.setContentLength(encryptedRespBytes.length);
                    response.getOutputStream().write(encryptedRespBytes);
                } catch (Exception e) {
                    log.error("SM4 响应加密失败: {}", e.getMessage());
                    wrappedResp.copyBodyToResponse();
                }
            } else {
                wrappedResp.copyBodyToResponse();
            }
        } else {
            filterChain.doFilter(wrappedRequest, response);
        }
    }

    /**
     * 校验 clientId 所属用户是否与当前 Sa-Token 登录用户一致（Fail-Closed 原则）。
     * <p>
     * 安全前提：session-sign-init 仅在「已登录」时签发密钥并建立 client-user 绑定，
     * 因此已登录用户发起的签名请求必然存在绑定记录且 userId 一致。
     * 绑定缺失/不匹配说明 clientId 异常（可能被伪造或串用），直接拒绝，杜绝冒充。
     * <p>
     * 未登录时返回 true：公开接口（login/captcha/session-sign-init 等）不走签名，
     * 不依赖本校验，因此不会误伤。
     */
    private boolean validateClientUserBinding(String clientId) {
        if (cn.dev33.satoken.stp.StpUtil.isLogin()) {
            try {
                String boundUserId = stringRedisTemplate.opsForValue().get("security:session-sign:client-user:" + clientId);
                if (StrUtil.isBlank(boundUserId)) {
                    log.warn("ClientId {} 缺失 client-user 绑定记录，拒绝签名请求", clientId);
                    return false;
                }
                long currentUserId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
                boolean matched = String.valueOf(currentUserId).equals(boundUserId);
                if (!matched) {
                    log.warn("ClientId {} 绑定用户与当前 Token 用户不匹配，拒绝签名请求", clientId);
                }
                return matched;
            } catch (Exception e) {
                log.error("校验 clientId-User 绑定记录出现异常，拒绝签名请求: {}", e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * 解析 HMAC-SM3 签名密钥：
     * 优先从 Redis 取会话临时密钥（X-Client-Id）；若提供了 ClientId 但校验失败或已过期，直接返回 null（拒绝回退静态密钥）。
     */
    private String resolveSignKey(HttpServletRequest request) {
        String clientId = request.getHeader(HEADER_CLIENT_ID);
        if (StrUtil.isNotBlank(clientId) && clientId.length() >= 8 && clientId.length() <= 128) {
            if (!validateClientUserBinding(clientId)) {
                log.warn("ClientId {} 归属用户与当前 Token 用户不匹配，已拒绝提供签名密钥", clientId);
                return null;
            }
            try {
                String sessionKey = stringRedisTemplate.opsForValue().get(SESSION_SIGN_KEY_PREFIX + clientId);
                if (StrUtil.isNotBlank(sessionKey)) return sessionKey;
            } catch (Exception e) {
                log.warn("获取 Redis 签名密钥异常: {}", e.getMessage());
            }
            // 显式带了 ClientId 的请求，若会话密钥不存在，不回退静态密钥，直接返回 null
            return null;
        }
        return properties.getApiSecurity().getSm3SignKey();
    }

    /**
     * 解析 SM4 密钥：
     * 优先从 Redis 取会话临时密钥；若提供了 ClientId 但校验失败或已过期，直接返回 null（拒绝回退静态密钥）。
     */
    private String resolveEncryptKey(HttpServletRequest request) {
        String clientId = request.getHeader(HEADER_CLIENT_ID);
        if (StrUtil.isNotBlank(clientId) && clientId.length() >= 8 && clientId.length() <= 128) {
            if (!validateClientUserBinding(clientId)) {
                log.warn("ClientId {} 归属用户与当前 Token 用户不匹配，已拒绝提供 SM4 密钥", clientId);
                return null;
            }
            try {
                String sessionKey = stringRedisTemplate.opsForValue().get(SESSION_SM4_KEY_PREFIX + clientId);
                if (StrUtil.isNotBlank(sessionKey)) return sessionKey;
            } catch (Exception e) {
                log.warn("获取 Redis SM4 密钥异常: {}", e.getMessage());
            }
            return null;
        }
        return properties.getApiSecurity().getSm4SecretKey();
    }

    /**
     * SM4-CBC 带认证标签加密（响应侧）。
     * 输出格式：V1 + IV(32Hex) + MAC(64Hex) + CBC_CT(Hex)
     * MAC = HMAC-SM3(IV_hex + CBC_CT_hex, keyBytes)，随密文一起返回，保证完整性。
     */
    private static String sm4CbcEncryptWithAuth(String plainText, byte[] keyBytes) {
        byte[] ivBytes = new byte[16];
        new SecureRandom().nextBytes(ivBytes);
        SM4 cbcSm4 = new SM4(Mode.CBC, Padding.PKCS5Padding, keyBytes, ivBytes);
        String ivHex = HexUtil.encodeHexStr(ivBytes);
        String cipherHex = cbcSm4.encryptHex(plainText);
        String mac = hmacSm3Hex(ivHex + cipherHex, keyBytes);
        return SM4_AUTH_PREFIX + ivHex + mac + cipherHex;
    }

    /**
     * SM4-CBC 带认证标签解密（请求侧）。
     * 输入格式：V1 + IV(32Hex) + MAC(64Hex) + CBC_CT(Hex)
     * 先解密，再校验 HMAC-SM3，MAC 不符说明密文被篡改 → 抛异常拒绝。
     */
    private static String sm4CbcDecryptWithAuth(String payload, byte[] keyBytes) {
        if (payload.length() <= SM4_AUTH_PREFIX.length() + 32 + 64) {
            throw new IllegalArgumentException("SM4 认证密文格式非法（长度不足）");
        }
        String body = payload.substring(SM4_AUTH_PREFIX.length());
        if (!body.substring(0, 32).matches("^[0-9a-fA-F]{32}$")) {
            throw new IllegalArgumentException("SM4 认证密文 IV 格式非法");
        }
        String ivHex = body.substring(0, 32);
        String macReceived = body.substring(32, 32 + 64);
        String cipherHex = body.substring(32 + 64);
        if (cipherHex.length() == 0 || !cipherHex.matches("^[0-9a-fA-F]+$")) {
            throw new IllegalArgumentException("SM4 认证密文正文格式非法");
        }

        // 校验完整性标签（恒定时间比较），防止 CBC bit-flipping 篡改
        String macExpected = hmacSm3Hex(ivHex + cipherHex, keyBytes);
        if (!MessageDigest.isEqual(
                macReceived.toLowerCase().getBytes(StandardCharsets.UTF_8),
                macExpected.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("SM4 密文完整性校验失败，数据可能已被篡改");
        }

        byte[] ivBytes = HexUtil.decodeHex(ivHex);
        SM4 cbcSm4 = new SM4(Mode.CBC, Padding.PKCS5Padding, keyBytes, ivBytes);
        return cbcSm4.decryptStr(cipherHex);
    }

    /** 计算 HMAC-SM3 十六进制摘要 */
    private static String hmacSm3Hex(String content, byte[] keyBytes) {
        HMac hmac = SmUtil.hmacSm3(keyBytes);
        return hmac.digestHex(content);
    }

    /**
     * SM4 密钥字符串转 16 字节 byte[]。
     * 32 位 Hex 串（会话临时密钥）→ HexUtil.decodeHex；
     * 其他（16字符 UTF-8）→ 直接取字节。
     */
    private static byte[] toSm4KeyBytes(String key) {
        if (key.length() == 32 && key.matches("[0-9a-fA-F]{32}")) {
            return HexUtil.decodeHex(key);
        }
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private void writeError(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        // 注意：Map.of 不允许 null 值（Objects.requireNonNull），若 msg 为 null 或 data 为 null 会抛 NPE，
        // 导致原始安全错误被 500 覆盖。这里改用允许 null 的 HashMap。
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("code", status);
        body.put("message", msg);
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
