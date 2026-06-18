package com.clouddisk.security;

import cn.hutool.core.util.IdUtil;
import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final long TTL_SECONDS = 300;

    private final CacheService cacheService;

    public Map<String, Object> create() {
        int a = ThreadLocalRandom.current().nextInt(1, 10);
        int b = ThreadLocalRandom.current().nextInt(1, 10);
        String id = IdUtil.simpleUUID();
        cacheService.set(key(id), String.valueOf(a + b), TTL_SECONDS);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("question", a + " + " + b + " = ?");
        return m;
    }

    public void verify(String id, String answer) {
        if (!StringUtils.hasText(id) || !StringUtils.hasText(answer)) {
            throw new BusinessException("请完成验证码");
        }
        String expected = cacheService.get(key(id));
        cacheService.delete(key(id));
        if (expected == null) {
            throw new BusinessException("验证码已过期，请刷新");
        }
        if (!expected.trim().equals(answer.trim())) {
            throw new BusinessException("验证码错误");
        }
    }

    private String key(String id) {
        return "captcha:" + id;
    }
}
