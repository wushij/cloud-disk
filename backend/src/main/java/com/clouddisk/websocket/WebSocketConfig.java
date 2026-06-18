package com.clouddisk.websocket;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UploadProgressHandler uploadProgressHandler;

    public WebSocketConfig(UploadProgressHandler uploadProgressHandler) {
        this.uploadProgressHandler = uploadProgressHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(uploadProgressHandler, "/ws/upload")
                .addInterceptors(new AuthHandshakeInterceptor())
                .setAllowedOrigins("*");
    }

    static class AuthHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String query = request.getURI().getQuery();
            if (query == null) return false;
            for (String part : query.split("&")) {
                if (part.startsWith("token=")) {
                    String token = part.substring(6);
                    Object loginId = StpUtil.getLoginIdByToken(token);
                    if (loginId != null) {
                        attributes.put("userId", Long.parseLong(loginId.toString()));
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }
}
