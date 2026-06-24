package com.clouddisk.config;

import cn.dev33.satoken.stp.StpInterface;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.UserMapper;
import com.clouddisk.service.AdminAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;
    private final AdminAccessService adminAccessService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.parseLong(loginId.toString()));
        if (user == null) return List.of();
        return new ArrayList<>(adminAccessService.resolvePermissions(user));
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
