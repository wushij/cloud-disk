package com.clouddisk.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 统一 API 错误响应体，前端通过 {@code error} 字段读取提示文案。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
    private String error;
    private String code;
    private int status;
    private String path;
    private Instant timestamp;

    public static ApiErrorResponse of(int status, String message, String code, String path) {
        return ApiErrorResponse.builder()
                .status(status)
                .error(message)
                .code(code)
                .path(path)
                .timestamp(Instant.now())
                .build();
    }
}
