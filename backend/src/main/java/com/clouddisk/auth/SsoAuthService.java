package com.clouddisk.auth;

import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "clouddisk.sso.enabled", havingValue = "true")
public class SsoAuthService {

    private static final long STATE_TTL = 600;

    private final CloudDiskProperties properties;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> buildAuthorizePayload() {
        CloudDiskProperties.Sso sso = properties.getSso();
        String state = UUID.randomUUID().toString().replace("-", "");
        cacheService.set("sso:state:" + state, "1", STATE_TTL);

        String url = UriComponentsBuilder.fromHttpUrl(sso.getAuthorizationUri())
                .queryParam("client_id", sso.getClientId())
                .queryParam("redirect_uri", sso.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", sso.getScope())
                .queryParam("state", state)
                .build(true)
                .toUriString();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerName", sso.getProviderName());
        result.put("authorizeUrl", url);
        result.put("state", state);
        return result;
    }

    public Map<String, String> exchangeCode(String code, String state) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("缺少授权码");
        }
        if (!StringUtils.hasText(state) || cacheService.get("sso:state:" + state) == null) {
            throw new BusinessException("单点登录状态无效或已过期");
        }
        cacheService.delete("sso:state:" + state);

        CloudDiskProperties.Sso sso = properties.getSso();
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", sso.getRedirectUri());
        body.add("client_id", sso.getClientId());
        body.add("client_secret", sso.getClientSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> tokenResp = restTemplate.exchange(
                sso.getTokenUri(), HttpMethod.POST, entity, String.class);
        if (!tokenResp.getStatusCode().is2xxSuccessful() || tokenResp.getBody() == null) {
            throw new BusinessException("单点登录令牌交换失败");
        }

        try {
            Map<String, Object> tokenMap = objectMapper.readValue(
                    tokenResp.getBody(), new TypeReference<>() {});
            String accessToken = String.valueOf(tokenMap.get("access_token"));
            return fetchUserProfile(accessToken, sso);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("SSO 回调处理失败: {}", e.getMessage());
            throw new BusinessException("单点登录失败");
        }
    }

    public String buildFrontendRedirectUrl(String ticket) {
        CloudDiskProperties.Sso sso = properties.getSso();
        return sso.getFrontendRedirect() + "?sso_ticket=" + urlEncode(ticket);
    }

    private Map<String, String> fetchUserProfile(String accessToken, CloudDiskProperties.Sso sso) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                sso.getUserInfoUri(), HttpMethod.GET, entity, String.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new BusinessException("单点登录用户信息获取失败");
        }
        Map<String, Object> userInfo = objectMapper.readValue(resp.getBody(), new TypeReference<>() {});

        Map<String, String> profile = new HashMap<>();
        profile.put("username", firstNonBlank(
                asString(userInfo.get("preferred_username")),
                asString(userInfo.get("email")),
                asString(userInfo.get("sub"))));
        profile.put("nickname", firstNonBlank(
                asString(userInfo.get("name")),
                profile.get("username")));
        profile.put("email", asString(userInfo.get("email")));
        return profile;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) return v;
        }
        throw new BusinessException("单点登录用户信息不完整");
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s != null ? s : "", StandardCharsets.UTF_8);
    }
}
