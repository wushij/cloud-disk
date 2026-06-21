package com.clouddisk.util;

import com.clouddisk.common.BusinessException;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class UsernameValidator {

    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 12;
    public static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private UsernameValidator() {
    }

    public static String normalize(String username) {
        return username != null ? username.trim() : "";
    }

    public static void validate(String username) {
        String u = normalize(username);
        if (!StringUtils.hasText(u)) {
            throw new BusinessException("用户名不能为空");
        }
        if (u.length() < MIN_LENGTH || u.length() > MAX_LENGTH) {
            throw new BusinessException("用户名长度 4-12 位");
        }
        if (!PATTERN.matcher(u).matches()) {
            throw new BusinessException("用户名只能包含字母、数字和下划线");
        }
    }
}
