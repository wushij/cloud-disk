package com.clouddisk.websocket;

import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.security.WebSocketTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final UploadProgressHandler uploadProgressHandler;
    private final CloudDiskProperties properties;
    private final WebSocketTicketService webSocketTicketService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var patterns = properties.getCors().getAllowedOriginPatterns();
        registry.addHandler(uploadProgressHandler, "/ws/upload")
                .addInterceptors(new AuthHandshakeInterceptor(webSocketTicketService))
                .setAllowedOriginPatterns(patterns.toArray(String[]::new));
    }

    @RequiredArgsConstructor
    static class AuthHandshakeInterceptor implements HandshakeInterceptor {

        private final WebSocketTicketService webSocketTicketService;

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String ticket = queryParam(request.getURI().getQuery(), "ticket");
            if (!StringUtils.hasText(ticket)) {
                return false;
            }
            Long userId = webSocketTicketService.consume(ticket);
            if (userId == null) {
                return false;
            }
            attributes.put("userId", userId);
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }

        private static String queryParam(String query, String name) {
            if (!StringUtils.hasText(query)) {
                return null;
            }
            String prefix = name + "=";
            for (String part : query.split("&")) {
                if (part.startsWith(prefix)) {
                    return URLDecoder.decode(part.substring(prefix.length()), StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }
}
