package com.clouddisk.config;

import cn.dev33.satoken.stp.StpInterface;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.parseLong(loginId.toString()));
        if (user == null) return List.of();
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return List.of("admin:*", "file:*", "share:*");
        }
        return List.of("file:read", "file:write", "share:write");
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.parseLong(loginId.toString()));
        if (user == null || user.getRole() == null) {
            return List.of("USER");
        }
        return List.of(user.getRole());
    }
}
