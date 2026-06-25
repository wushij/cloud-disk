package com.clouddisk.onlyoffice;

import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnlyOfficeJwtHelper {

    private final CloudDiskProperties properties;
    private final ObjectMapper objectMapper;

    public void signConfig(Map<String, Object> config) {
        try {
            SecretKey key = secretKey();
            String json = objectMapper.writeValueAsString(config);
            String token = Jwts.builder().content(json, "UTF-8").signWith(key).compact();
            config.put("token", token);
        } catch (Exception e) {
            log.warn("OnlyOffice JWT 签名失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> unwrapCallback(Map<String, Object> body, String authorizationHeader) {
        String jwt = null;
        if (body != null && body.get("token") instanceof String bodyToken && StringUtils.hasText(bodyToken)) {
            jwt = bodyToken;
        } else if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7).trim();
        }
        if (!StringUtils.hasText(jwt)) {
            throw new BusinessException("OnlyOffice 回调缺少 JWT 签名");
        }
        try {
            byte[] payload = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedContent(jwt)
                    .getPayload();
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OnlyOffice 回调 JWT 校验失败: {}", e.getMessage());
            throw new BusinessException("OnlyOffice 回调签名无效");
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(
                properties.getOnlyoffice().getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }
}
