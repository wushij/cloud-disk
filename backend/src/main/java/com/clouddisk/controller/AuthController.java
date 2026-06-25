package com.clouddisk.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.clouddisk.auth.FederatedAuthService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.dto.LoginRequest;
import com.clouddisk.dto.ProfileUpdateRequest;
import com.clouddisk.dto.RegisterRequest;
import com.clouddisk.security.CaptchaService;
import com.clouddisk.security.LoginProtectionService;
import com.clouddisk.security.MediaAccessTokenService;
import com.clouddisk.security.WebSocketTicketService;
import com.clouddisk.service.AuthService;
import com.clouddisk.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final FederatedAuthService federatedAuthService;
    private final CaptchaService captchaService;
    private final LoginProtectionService loginProtection;
    private final WebSocketTicketService webSocketTicketService;
    private final MediaAccessTokenService mediaAccessTokenService;

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        return federatedAuthService.authProviders();
    }

    @GetMapping("/captcha")
    public Map<String, Object> captcha() {
        return captchaService.create();
    }

    @GetMapping("/captcha/required")
    public Map<String, Object> captchaRequired(HttpServletRequest request) {
        String ip = ClientIpUtil.resolve(request);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("required", loginProtection.captchaRequired(ip));
        return m;
    }

    @PostMapping("/login")
    @SentinelResource(value = "auth_login", blockHandler = "loginBlocked")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    public static Map<String, Object> loginBlocked(LoginRequest req, BlockException ex) {
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

    @PostMapping("/register")
    @SentinelResource(value = "auth_register", blockHandler = "registerBlocked")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    public static Map<String, Object> registerBlocked(RegisterRequest req, BlockException ex) {
        throw new BusinessException("请求过于频繁，请稍后再试", "RATE_LIMITED");
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
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
    public Map<String, Object> updateProfile(@Valid @RequestBody ProfileUpdateRequest req) {
        return authService.updateProfile(req);
    }

    @PostMapping("/logout")
    public Map<String, String> logout() {
        authService.logout();
        return Map.of("message", "已退出");
    }

    @PostMapping("/avatar")
    public Map<String, Object> uploadAvatar(@RequestParam("file") org.springframework.web.multipart.MultipartFile file)
            throws Exception {
        return authService.uploadAvatar(file);
    }

    @GetMapping("/avatar/view")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> avatarView(
            jakarta.servlet.http.HttpServletRequest request) {
        return authService.loadAvatar(request);
    }
}
