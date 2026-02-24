package com.numberbomb.config;

import com.numberbomb.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket拦截器
 * 处理连接认证和用户信息提取
 */
@Slf4j
@Component
public class WebSocketInterceptor implements HandshakeInterceptor, ChannelInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            
            // 从请求参数或Header中获取token或临时用户ID
            String token = getTokenFromRequest(httpRequest);
            String tempUserId = getTempUserIdFromRequest(httpRequest);
            String tempNickname = getTempNicknameFromRequest(httpRequest);
            
            // 验证并提取用户信息
            Long userId = null;
            if (token != null && !token.isEmpty()) {
                try {
                    // 验证JWT token
                    userId = JwtUtil.getUserIdFromToken(token);
                    log.info("WebSocket连接 - JWT用户: {}", userId);
                } catch (Exception e) {
                    log.warn("WebSocket连接 - JWT验证失败: {}", e.getMessage());
                }
            }
            
            // 如果没有userId，使用临时用户ID
            if (userId == null && tempUserId != null && !tempUserId.isEmpty()) {
                // 临时用户ID将在连接建立后通过ChannelInterceptor处理
                attributes.put("tempUserId", tempUserId);
                attributes.put("tempNickname", tempNickname);
                log.info("WebSocket连接 - 临时用户: {}", tempUserId);
            } else if (userId != null) {
                attributes.put("userId", userId);
            }
            
            // 存储认证信息到attributes，供后续使用
            if (token != null) {
                attributes.put("token", token);
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        // 握手后的处理
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 连接时，从Session中获取用户信息
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                Long userId = (Long) sessionAttributes.get("userId");
                String tempUserId = (String) sessionAttributes.get("tempUserId");
                
                if (userId != null) {
                    accessor.setUser((Principal) new StompPrincipal(userId.toString()));
                } else if (tempUserId != null) {
                    accessor.setUser((Principal) new StompPrincipal("temp_" + tempUserId));
                }
            }
        }
        
        return message;
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        // 从Header中获取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // 从查询参数中获取
        return request.getParameter("token");
    }

    private String getTempUserIdFromRequest(HttpServletRequest request) {
        // 优先从Header中获取 X-User-Id（前端发送的header名称）
        String tempUserId = request.getHeader("X-User-Id");
        // 兼容旧的 header 名称
        if (tempUserId == null || tempUserId.isEmpty()) {
            tempUserId = request.getHeader("X-Temp-User-Id");
        }
        if (tempUserId != null && !tempUserId.isEmpty()) {
            return tempUserId;
        }
        // 从查询参数中获取
        return request.getParameter("tempUserId");
    }

    private String getTempNicknameFromRequest(HttpServletRequest request) {
        // 优先从Header中获取 X-Guest-Nickname（前端发送的header名称）
        String tempNickname = request.getHeader("X-Guest-Nickname");
        // 兼容旧的 header 名称
        if (tempNickname == null || tempNickname.isEmpty()) {
            tempNickname = request.getHeader("X-Temp-Nickname");
        }
        if (tempNickname != null && !tempNickname.isEmpty()) {
            return tempNickname;
        }
        // 从查询参数中获取
        return request.getParameter("tempNickname");
    }

    /**
     * STOMP Principal实现
     */
    public static class StompPrincipal {
        private final String name;

        public StompPrincipal(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
