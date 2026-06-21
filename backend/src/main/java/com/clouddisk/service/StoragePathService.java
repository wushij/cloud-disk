package com.clouddisk.service;

import com.clouddisk.entity.User;
import com.clouddisk.mapper.UserMapper;
import com.clouddisk.util.StoragePathHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StoragePathService {

    private final UserMapper userMapper;

    public String buildUserFilePath(long userId, String fileName) {
        return StoragePathHelper.userFilePath(resolveUsername(userId), fileName);
    }

    public String buildUserAvatarPath(long userId) {
        return StoragePathHelper.userAvatarPath(resolveUsername(userId));
    }

    public String buildTeamAvatarPath(String teamName, long spaceId) {
        return StoragePathHelper.teamAvatarPath(teamName, spaceId);
    }

    private String resolveUsername(long userId) {
        User user = userMapper.selectById(userId);
        if (user != null && StringUtils.hasText(user.getUsername())) {
            return user.getUsername();
        }
        return "user_" + userId;
    }
}
