package com.clouddisk.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void businessException_returnsUnifiedBody() {
        ApiErrorResponse resp = handler.handleBusiness(new BusinessException("用户名或密码错误"), request);
        assertEquals("用户名或密码错误", resp.getError());
        assertEquals("BUSINESS_ERROR", resp.getCode());
        assertEquals(400, resp.getStatus());
        assertEquals("/api/test", resp.getPath());
        assertNotNull(resp.getTimestamp());
    }

    @Test
    void unreadableBody_returnsBadRequest() {
        ApiErrorResponse resp = handler.handleUnreadable(mock(HttpMessageNotReadableException.class), request);
        assertEquals("请求体格式错误", resp.getError());
        assertEquals("BAD_REQUEST_BODY", resp.getCode());
        assertEquals(400, resp.getStatus());
    }

    @Test
    void internalError_hidesDetails() {
        ApiErrorResponse resp = handler.handleOther(new RuntimeException("jdbc secret"), request);
        assertEquals("服务器内部错误，请稍后重试", resp.getError());
        assertEquals("INTERNAL_ERROR", resp.getCode());
        assertEquals(500, resp.getStatus());
    }
}
