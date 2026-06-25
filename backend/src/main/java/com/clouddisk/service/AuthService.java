package com.clouddisk.service;



import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.clouddisk.auth.SystemRole;
import com.clouddisk.common.UserStatus;

import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.dto.LoginRequest;
import com.clouddisk.dto.ProfileUpdateRequest;
import com.clouddisk.dto.RegisterRequest;
import com.clouddisk.util.UsernameValidator;
import com.clouddisk.entity.User;
import com.clouddisk.common.BusinessException;
import com.clouddisk.security.CaptchaService;
import com.clouddisk.security.LoginProtectionService;
import com.clouddisk.util.ClientIpUtil;
import com.clouddisk.util.AuthHelper;

import com.clouddisk.mapper.UserMapper;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;

import org.springframework.util.StringUtils;



import java.util.HashMap;

import java.util.List;

import java.util.Locale;

import java.util.Map;



@Service
@DependsOn("databaseInitializer")

@RequiredArgsConstructor

public class AuthService {



    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final UserCacheService userCacheService;

    private final AuditLogService auditLogService;

    private final com.clouddisk.storage.StorageService storageService;

    private final LoginProtectionService loginProtection;

    private final CaptchaService captchaService;

    private final CloudDiskProperties cloudDiskProperties;

    private final NotificationDispatcher notificationDispatcher;

    private final StoragePathService storagePathService;

    private final AuthHelper authHelper;



    @PostConstruct

    public void initAdmin() {

        Long count = userMapper.selectCount(null);

        if (count == 0) {

            User admin = new User();

            admin.setUsername("admin");

            admin.setPassword(passwordEncoder.encode("admin123"));

            admin.setNickname("管理员");

            admin.setStatus(UserStatus.ACTIVE);

            admin.setRole(SystemRole.SUPER_ADMIN);

            admin.setStorageQuota(0L);

            userMapper.insert(admin);

            return;

        }

        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));

        if (admin != null) {
            boolean changed = false;
            if (admin.getRole() == null || admin.getRole().isBlank() || SystemRole.ADMIN.equals(admin.getRole())) {
                admin.setRole(SystemRole.SUPER_ADMIN);
                changed = true;
            }
            if (changed) {
                userMapper.updateById(admin);
            }
        }

    }



    public Map<String, Object> login(LoginRequest req) {
        String ip = ClientIpUtil.current();
        String username = req.getUsername();
        loginProtection.checkAllowed(ip, username);
        if (loginProtection.captchaRequired(ip)) {
            captchaService.verify(req.getCaptchaId(), req.getCaptchaAnswer());
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            loginProtection.recordFailure(ip, username);
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
        if (user.getStatus() != null && user.getStatus() == UserStatus.PENDING) {
            throw new BusinessException("您的账号尚未通过审核，请等待管理员批准后再登录");
        }

        loginProtection.clearOnSuccess(ip, username);
        StpUtil.login(user.getId());
        auditLogService.log(user.getId(), user.getUsername(), "LOGIN", "user", String.valueOf(user.getId()), "登录成功");
        return buildAuthResponse(user);
    }



    public Map<String, Object> register(RegisterRequest req) {
        if (cloudDiskProperties.getRateLimit().isCaptchaOnRegister()) {
            captchaService.verify(req.getCaptchaId(), req.getCaptchaAnswer());
        }

        String username = UsernameValidator.normalize(req.getUsername());
        UsernameValidator.validate(username);
        req.setUsername(username);

        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (existing != null) {
            if (existing.getStatus() != null && existing.getStatus() == UserStatus.PENDING) {
                throw new BusinessException("该用户名已提交注册申请，请等待管理员审核，无需重复注册");
            }
            throw new BusinessException("用户名已存在");
        }

        User user = new User();

        user.setUsername(username);

        user.setPassword(passwordEncoder.encode(req.getPassword()));

        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());

        user.setStatus(UserStatus.PENDING);

        user.setRole("USER");

        user.setStorageQuota(UserStatus.DEFAULT_USER_QUOTA_BYTES);

        userMapper.insert(user);

        notifyAdminsPendingRegistration(user);

        Map<String, Object> m = new HashMap<>();

        m.put("pending", true);

        m.put("title", "注册申请已提交");

        m.put("message", "管理员审核通过后您才能登录云盘，请耐心等待，无需重复注册。");

        return m;

    }

    private void notifyAdminsPendingRegistration(User user) {
        List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getRole, SystemRole.SUPER_ADMIN, SystemRole.ADMIN)
                .eq(User::getStatus, UserStatus.ACTIVE));
        String displayName = user.getNickname() != null ? user.getNickname() : user.getUsername();
        String content = displayName + "（" + user.getUsername() + "）申请注册账号，请审核是否通过";
        for (User admin : admins) {
            notificationDispatcher.dispatch(admin.getId(), "USER_REGISTER", "新用户注册",
                    content, String.valueOf(user.getId()));
        }
    }



    public Map<String, Object> me() {

        Long userId = StpUtil.getLoginIdAsLong();

        User user = userCacheService.getById(userId);

        if (user == null) throw new BusinessException("用户不存在");

        Map<String, Object> m = new HashMap<>();

        m.put("id", user.getId());

        m.put("username", user.getUsername());

        m.put("nickname", user.getNickname());

        m.put("avatar", user.getAvatar());

        m.put("email", user.getEmail());

        m.put("phone", user.getPhone());

        m.put("role", user.getRole() != null ? user.getRole() : "USER");

        return m;

    }



    public Map<String, Object> updateProfile(ProfileUpdateRequest req) {

        Long userId = StpUtil.getLoginIdAsLong();

        User user = userMapper.selectById(userId);

        if (user == null) throw new BusinessException("用户不存在");

        if (req.getNickname() != null) user.setNickname(req.getNickname());

        if (req.getEmail() != null) user.setEmail(req.getEmail());

        if (req.getPhone() != null) user.setPhone(req.getPhone());

        if (req.getAvatar() != null) user.setAvatar(req.getAvatar());

        userMapper.updateById(user);

        userCacheService.evict(userId);

        return me();

    }



    public void logout() {

        StpUtil.logout();

    }



    public Map<String, Object> uploadAvatar(org.springframework.web.multipart.MultipartFile file) throws Exception {

        Long userId = StpUtil.getLoginIdAsLong();

        if (file.isEmpty()) throw new BusinessException("文件为空");

        String contentType = file.getContentType();

        if (contentType == null || !contentType.startsWith("image/")) {

            throw new BusinessException("请上传图片文件");

        }

        String path = storagePathService.buildUserAvatarPath(userId);

        User user = userMapper.selectById(userId);

        if (user == null) throw new BusinessException("用户不存在");

        if (StringUtils.hasText(user.getAvatar()) && !path.equals(user.getAvatar())) {
            storageService.delete(user.getAvatar());
        }

        storageService.store(file.getInputStream(), path, file.getSize(), contentType);

        user.setAvatar(path);

        userMapper.updateById(user);

        userCacheService.evict(userId);

        return me();

    }



    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> loadAvatar(
            jakarta.servlet.http.HttpServletRequest request) {
        Long userId = authHelper.requireUserId(request);

        User user = userMapper.selectById(userId);

        if (user == null || user.getAvatar() == null) {

            throw new BusinessException("头像不存在");

        }

        var resource = storageService.loadAsResource(user.getAvatar());

        return org.springframework.http.ResponseEntity.ok()

                .contentType(resolveAvatarMediaType(user.getAvatar()))

                .body(resource);

    }

    private static org.springframework.http.MediaType resolveAvatarMediaType(String path) {
        if (path == null) {
            return org.springframework.http.MediaType.IMAGE_JPEG;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return org.springframework.http.MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return org.springframework.http.MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return org.springframework.http.MediaType.parseMediaType("image/webp");
        }
        return org.springframework.http.MediaType.IMAGE_JPEG;
    }



    public static long currentUserId() {

        return StpUtil.getLoginIdAsLong();

    }



    private Map<String, Object> buildAuthResponse(User user) {

        Map<String, Object> m = new HashMap<>();

        m.put("token", StpUtil.getTokenValue());

        m.put("username", user.getUsername());

        m.put("nickname", user.getNickname());

        m.put("role", user.getRole() != null ? user.getRole() : "USER");

        m.put("userId", user.getId());

        return m;

    }

}
