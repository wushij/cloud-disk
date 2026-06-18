package com.clouddisk.service;



import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.clouddisk.common.BusinessException;

import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.dto.LoginRequest;
import com.clouddisk.dto.ProfileUpdateRequest;
import com.clouddisk.dto.RegisterRequest;
import com.clouddisk.entity.User;
import com.clouddisk.security.CaptchaService;
import com.clouddisk.security.LoginProtectionService;
import com.clouddisk.util.ClientIpUtil;

import com.clouddisk.mapper.UserMapper;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;



import java.util.HashMap;

import java.util.Map;



@Service

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



    @PostConstruct

    public void initAdmin() {

        Long count = userMapper.selectCount(null);

        if (count == 0) {

            User admin = new User();

            admin.setUsername("admin");

            admin.setPassword(passwordEncoder.encode("admin123"));

            admin.setNickname("管理员");

            admin.setStatus(1);

            admin.setRole("ADMIN");

            userMapper.insert(admin);

            return;

        }

        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));

        if (admin != null && (admin.getRole() == null || admin.getRole().isBlank())) {

            admin.setRole("ADMIN");

            userMapper.updateById(admin);

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

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
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

        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername()));

        if (exists > 0) {

            throw new BusinessException("用户名已存在");

        }

        User user = new User();

        user.setUsername(req.getUsername());

        user.setPassword(passwordEncoder.encode(req.getPassword()));

        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());

        user.setStatus(1);

        user.setRole("USER");

        userMapper.insert(user);

        StpUtil.login(user.getId());

        return buildAuthResponse(user);

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

        String path = "avatars/" + userId + ".jpg";

        storageService.store(file.getInputStream(), path, file.getSize(), contentType);

        User user = userMapper.selectById(userId);

        if (user == null) throw new BusinessException("用户不存在");

        user.setAvatar(path);

        userMapper.updateById(user);

        userCacheService.evict(userId);

        Map<String, Object> m = new HashMap<>();

        m.put("avatarUrl", "/api/auth/avatar/view");

        return m;

    }



    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> loadAvatar(
            jakarta.servlet.http.HttpServletRequest request) {
        Long userId = com.clouddisk.util.AuthHelper.requireUserId(request);

        User user = userMapper.selectById(userId);

        if (user == null || user.getAvatar() == null) {

            throw new BusinessException("头像不存在");

        }

        var resource = storageService.loadAsResource(user.getAvatar());

        return org.springframework.http.ResponseEntity.ok()

                .contentType(org.springframework.http.MediaType.IMAGE_JPEG)

                .body(resource);

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

