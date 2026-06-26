package com.clouddisk.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * 媒体资源 HTTP 响应头：允许浏览器/WebView 缓存，并支持 Range 流式播放。
 */
public final class MediaResponseHeaders {

    private static final String CACHE_CONTROL = "private, max-age=3600";

    private MediaResponseHeaders() {
    }

    public static ResponseEntity.BodyBuilder ok() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes");
    }
}
