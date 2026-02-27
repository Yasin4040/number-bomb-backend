package com.numberbomb.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket拦截器
 * 处理连接认证和用户信息提取
 * 只支持原生 WebSocket，移除 STOMP 相关逻辑
 */
@Slf4j
@Component
public class WebSocketInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            
            // 从查询字符串中解析参数
            String queryString = httpRequest.getQueryString();
            Map<String, String> queryParams = parseQueryString(queryString);
            
            // 从请求参数中获取token或临时用户ID
            String token = queryParams.get("token");
            String tempUserId = queryParams.get("tempUserId");
            String tempNickname = queryParams.get("tempNickname");
            String userId = queryParams.get("userId");
            
            // 存储用户信息到 attributes
            if (userId != null && !userId.isEmpty()) {
                attributes.put("userId", userId);
                log.debug("WebSocket握手 - 从URL参数获取userId: {}", userId);
            } else if (tempUserId != null && !tempUserId.isEmpty()) {
                attributes.put("userId", tempUserId);
                attributes.put("tempUserId", tempUserId);
                log.debug("WebSocket握手 - 从URL参数获取tempUserId: {}", tempUserId);
            }
            
            if (tempNickname != null && !tempNickname.isEmpty()) {
                attributes.put("tempNickname", tempNickname);
            }
            
            if (token != null && !token.isEmpty()) {
                attributes.put("token", token);
            }
            
            log.info("WebSocket握手 - userId={}, tempUserId={}, uri={}", 
                userId, tempUserId, request.getURI());
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        // 握手后的处理
        if (exception != null) {
            log.error("WebSocket握手失败: {}", exception.getMessage());
        }
    }

    /**
     * 解析查询字符串为键值对
     */
    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                try {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception e) {
                    log.warn("解析查询参数失败: {}", pair);
                }
            }
        }
        return params;
    }
}
