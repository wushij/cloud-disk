package com.clouddisk.util;

import cn.dev33.satoken.stp.StpUtil;
import com.clouddisk.common.BusinessException;
import com.clouddisk.security.MediaAccessTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthHelper {

    private final MediaAccessTokenService mediaAccessTokenService;

    public long requireUserId(HttpServletRequest request) {
        String queryToken = request.getParameter("access_token");
        if (queryToken != null && !queryToken.isBlank()) {
            Long mediaUserId = mediaAccessTokenService.resolve(queryToken);
            if (mediaUserId != null) {
                return mediaUserId;
            }
        }
        if (!StpUtil.isLogin()) {
            throw new BusinessException("未登录或登录已过期");
        }
        return StpUtil.getLoginIdAsLong();
    }
}
