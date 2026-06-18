package com.clouddisk.util;

import cn.dev33.satoken.stp.StpUtil;
import com.clouddisk.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;

public final class AuthHelper {

    private AuthHelper() {}

    public static long requireUserId(HttpServletRequest request) {
        String queryToken = request.getParameter("access_token");
        if (queryToken != null && !queryToken.isBlank()) {
            Object loginId = StpUtil.getLoginIdByToken(queryToken);
            if (loginId == null) throw new BusinessException("登录凭证无效");
            return Long.parseLong(loginId.toString());
        }
        if (!StpUtil.isLogin()) throw new BusinessException("未登录");
        return StpUtil.getLoginIdAsLong();
    }
}
