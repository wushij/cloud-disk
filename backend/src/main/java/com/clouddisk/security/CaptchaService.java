package com.clouddisk.security;



import cn.hutool.captcha.CaptchaUtil;

import cn.hutool.captcha.LineCaptcha;

import cn.hutool.core.util.IdUtil;

import com.clouddisk.cache.CacheService;

import com.clouddisk.common.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;



import java.awt.Color;

import java.util.LinkedHashMap;

import java.util.Map;



@Service

@RequiredArgsConstructor

public class CaptchaService {



    private static final long TTL_SECONDS = 300;



    private final CacheService cacheService;



    public Map<String, Object> create() {

        String id = IdUtil.simpleUUID();

        // 与 wu-admin 完全一致：LineCaptcha 130×48、4 位字符、50 条短线干扰

        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(130, 48, 4, 50);

        lineCaptcha.setBackground(Color.WHITE);

        String code = lineCaptcha.getCode();



        cacheService.set(key(id), code.toLowerCase(), TTL_SECONDS);

        Map<String, Object> m = new LinkedHashMap<>();

        m.put("id", id);

        m.put("img", lineCaptcha.getImageBase64());

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

        if (!expected.trim().equalsIgnoreCase(answer.trim())) {

            throw new BusinessException("验证码错误");

        }

    }



    private String key(String id) {

        return "captcha:" + id;

    }

}

