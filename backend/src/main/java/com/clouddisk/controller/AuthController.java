package com.clouddisk.controller;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.clouddisk.auth.FederatedAuthService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.dto.EmailLoginRequest;
import com.clouddisk.dto.EmailResetPasswordRequest;
import com.clouddisk.dto.EmailSendCodeRequest;
import com.clouddisk.dto.LoginRequest;
import com.clouddisk.dto.ProfileUpdateRequest;
import com.clouddisk.dto.RegisterRequest;
import com.clouddisk.security.CaptchaService;
import com.clouddisk.security.EmailCodeService;
import com.clouddisk.security.LoginProtectionService;
import com.clouddisk.security.MediaAccessTokenService;
import com.clouddisk.security.SecurityConfigService;
import com.clouddisk.security.WebSocketTicketService;
import com.clouddisk.service.AuthService;
import com.clouddisk.util.ClientIpUtil;
import com.clouddisk.vo.AuthTokenVO;
import com.clouddisk.vo.CaptchaRequiredVO;
import com.clouddisk.vo.CaptchaVO;
import com.clouddisk.vo.MessageVO;
import com.clouddisk.vo.OperationResultVO;
import com.clouddisk.vo.RegisterResultVO;
import com.clouddisk.vo.SecurityPublicConfigVO;
import com.clouddisk.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 会话密钥 clientId 长度下限 / 上限 */
    private static final int CLIENT_ID_MIN_LEN = 8;
    private static final int CLIENT_ID_MAX_LEN = 128;

    /**
     * 密码学安全随机数生成器（用于生成会话签名密钥 / SM4 加密密钥）。
     * 注意：禁止使用 RandomUtil.randomString（底层为 ThreadLocalRandom，非加密安全），
     * 否则对称密钥可被预测，直接威胁签名与加密体系。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthService authService;
    private final FederatedAuthService federatedAuthService;
    private final CaptchaService captchaService;
    private final LoginProtectionService loginProtection;
    private final WebSocketTicketService webSocketTicketService;
    private final MediaAccessTokenService mediaAccessTokenService;
    private final CloudDiskProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final SecurityConfigService securityConfigService;
    private final EmailCodeService emailCodeService;

    /** 公开接口：获取当前系统的安全特性配置（供 PC / 移动端初始化安全开关） */
    @GetMapping("/config")
    public SecurityPublicConfigVO config() {
        Map<String, Object> cfg = securityConfigService.getPublicConfig();
        return SecurityPublicConfigVO.builder()
                .timestampEnabled(Boolean.TRUE.equals(cfg.get("timestampEnabled")))
                .nonceEnabled(Boolean.TRUE.equals(cfg.get("nonceEnabled")))
                .sm3SignEnabled(Boolean.TRUE.equals(cfg.get("sm3SignEnabled")))
                .sm2SignEnabled(Boolean.TRUE.equals(cfg.get("sm2SignEnabled")))
                .sm4EncryptEnabled(Boolean.TRUE.equals(cfg.get("sm4EncryptEnabled")))
                .build();
    }

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        return federatedAuthService.authProviders();
    }

    @GetMapping("/captcha")
    public CaptchaVO captcha(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        Map<String, Object> m = captchaService.create();
        return CaptchaVO.builder()
                .id((String) m.get("id"))
                .img((String) m.get("img"))
                .build();
    }

    @GetMapping("/captcha/required")
    public CaptchaRequiredVO captchaRequired(HttpServletRequest request) {
        String ip = ClientIpUtil.resolve(request);
        return CaptchaRequiredVO.builder()
                .required(loginProtection.captchaRequired(ip))
                .build();
    }

    @PostMapping("/login")
    @SentinelResource(value = "auth_login", blockHandler = "loginBlocked")
    public AuthTokenVO login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    public static AuthTokenVO loginBlocked(LoginRequest req, BlockException ex) {
        throw new BusinessException("请求过于频繁，请稍后再试", "RATE_LIMITED");
    }

    @PostMapping("/ldap/login")
    @SentinelResource(value = "auth_login", blockHandler = "ldapLoginBlocked")
    public Map<String, Object> ldapLogin(@Valid @RequestBody LoginRequest req) {
        String ip = ClientIpUtil.current();
        loginProtection.checkAllowed(ip, req.getUsername());
        if (loginProtection.captchaRequired(ip)) {
            captchaService.verify(req.getCaptchaId(), req.getCaptchaAnswer());
        }
        try {
            Map<String, Object> result = federatedAuthService.loginWithLdap(req.getUsername(), req.getPassword());
            loginProtection.clearOnSuccess(ip, req.getUsername());
            return result;
        } catch (BusinessException e) {
            loginProtection.recordFailure(ip, req.getUsername());
            throw e;
        }
    }

    public static Map<String, Object> ldapLoginBlocked(LoginRequest req, BlockException ex) {
        throw new BusinessException("请求过于频繁，请稍后再试", "RATE_LIMITED");
    }

    @GetMapping("/sso/authorize")
    public Map<String, Object> ssoAuthorize() {
        return federatedAuthService.authProviders();
    }

    @GetMapping("/sso/callback")
    public void ssoCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {
        Map<String, String> result = federatedAuthService.loginWithSsoCode(code, state);
        response.sendRedirect(result.get("redirectUrl"));
    }

    @PostMapping("/sso/ticket")
    public Map<String, Object> ssoTicket(@RequestBody Map<String, String> body, HttpServletResponse response) {
        String ticket = body.get("ticket");
        if (ticket == null || ticket.isBlank()) {
            throw new BusinessException("缺少票据");
        }
        return federatedAuthService.loginWithSsoTicket(ticket, response);
    }

    @PostMapping("/email/send-code")
    public OperationResultVO sendEmailCode(@Valid @RequestBody EmailSendCodeRequest req) {
        emailCodeService.sendCode(req.getEmail(), req.getScene());
        return OperationResultVO.builder()
                .success(true)
                .message("验证码发送成功，请前往邮箱查看")
                .build();
    }

    @PostMapping("/email/login")
    public AuthTokenVO emailLogin(@Valid @RequestBody EmailLoginRequest req) {
        return authService.loginByEmailCode(req);
    }

    @PostMapping("/email/reset-password")
    public OperationResultVO resetPasswordByEmail(@Valid @RequestBody EmailResetPasswordRequest req) {
        authService.resetPasswordByEmail(req);
        return OperationResultVO.builder()
                .success(true)
                .message("密码重置成功，请使用新密码登录")
                .build();
    }

    @PostMapping("/register")
    @SentinelResource(value = "auth_register", blockHandler = "registerBlocked")
    public RegisterResultVO register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    public static RegisterResultVO registerBlocked(RegisterRequest req, BlockException ex) {
        throw new BusinessException("请求过于频繁，请稍后再试", "RATE_LIMITED");
    }

    @GetMapping("/me")
    public UserVO me() {
        return authService.me();
    }

    @PostMapping("/ws-ticket")
    public Map<String, String> wsTicket() {
        long userId = AuthService.currentUserId();
        return Map.of("ticket", webSocketTicketService.issue(userId));
    }

    @GetMapping("/media-token")
    public Map<String, Object> mediaToken() {
        long userId = AuthService.currentUserId();
        return mediaAccessTokenService.issue(userId);
    }

    @PutMapping("/profile")
    public UserVO updateProfile(@Valid @RequestBody ProfileUpdateRequest req) {
        return authService.updateProfile(req);
    }

    @PostMapping("/logout")
    public MessageVO logout() {
        authService.logout();
        return MessageVO.builder().message("已退出").build();
    }

    @PostMapping("/sync-cookie")
    public MessageVO syncCookie(HttpServletResponse response) {
        authService.syncSessionCookie(response);
        return MessageVO.builder().message("ok").build();
    }

    @PostMapping("/avatar")
    public UserVO uploadAvatar(@RequestParam("file") MultipartFile file) throws Exception {
        return authService.uploadAvatar(file);
    }

    @GetMapping("/avatar/view")
    public ResponseEntity<Resource> avatarView(HttpServletRequest request) {
        return authService.loadAvatar(request);
    }

    /**
     * 会话签名密钥初始化接口。
     * 前端每次页面加载后调用，获取本次会话的 HMAC-SM3 签名密钥和 SM4 加密密钥。
     */
    @PostMapping("/session-sign-init")
    @SentinelResource(value = "auth_session_sign_init", blockHandler = "sessionSignInitBlocked")
    public Map<String, Object> sessionSignInit(@RequestParam("clientId") String clientId) {
        if (StrUtil.isBlank(clientId)
                || clientId.length() < CLIENT_ID_MIN_LEN
                || clientId.length() > CLIENT_ID_MAX_LEN
                || !clientId.matches("[A-Za-z0-9\\-_]+")) {
            throw new BusinessException("clientId 格式非法");
        }

        boolean signActive = securityConfigService.isSm3SignEnabled();
        boolean sm4Active  = securityConfigService.isSm4EncryptEnabled();

        if (!signActive && !sm4Active) {
            return Map.of(
                "enabled", false,
                "sm3SignEnabled", false,
                "sm4EncryptEnabled", false
            );
        }

        long ttl = properties.getApiSecurity().getSessionSignTtlMinutes();

        if (!cn.dev33.satoken.stp.StpUtil.isLogin()) {
            return Map.of(
                "enabled", false,
                "sm3SignEnabled", false,
                "sm4EncryptEnabled", false,
                "requiresLogin", true
            );
        }

        Map<String, Object> result = new HashMap<>();
        result.put("enabled", true);
        result.put("ttlMinutes", ttl);
        result.put("sm3SignEnabled", signActive);
        result.put("sm4EncryptEnabled", sm4Active);

        try {
            long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
            stringRedisTemplate.opsForValue().set(
                    "security:session-sign:client-user:" + clientId,
                    String.valueOf(userId), ttl, TimeUnit.MINUTES);
        } catch (Exception ignored) {}

        if (signActive) {
            byte[] signBytes = new byte[32];
            SECURE_RANDOM.nextBytes(signBytes);
            String signKey = HexUtil.encodeHexStr(signBytes);
            stringRedisTemplate.opsForValue().set(
                    com.clouddisk.security.ApiSecurityFilter.SESSION_SIGN_KEY_PREFIX + clientId,
                    signKey, ttl, TimeUnit.MINUTES);
            result.put("sm3SignKey", signKey);
        }

        if (sm4Active) {
            byte[] sm4Bytes = new byte[16];
            SECURE_RANDOM.nextBytes(sm4Bytes);
            String sm4Key = HexUtil.encodeHexStr(sm4Bytes);
            stringRedisTemplate.opsForValue().set(
                    com.clouddisk.security.ApiSecurityFilter.SESSION_SM4_KEY_PREFIX + clientId,
                    sm4Key, ttl, TimeUnit.MINUTES);
            result.put("sm4Key", sm4Key);
        }

        return result;
    }

    public static Map<String, Object> sessionSignInitBlocked(String clientId, BlockException ex) {
        throw new BusinessException("会话初始化请求过于频繁，请稍后再试", "RATE_LIMITED");
    }
}
