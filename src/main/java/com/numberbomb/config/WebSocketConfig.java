package com.numberbomb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket配置类
 * 使用STOMP协议进行消息传递
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的消息代理，用于向客户端发送消息
        // 客户端订阅以 /topic 开头的目的地
        config.enableSimpleBroker("/topic", "/queue");
        // 客户端发送消息到以 /app 开头的目的地
        config.setApplicationDestinationPrefixes("/app");
        // 用户专用目的地前缀
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册WebSocket端点，允许跨域
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // 支持SockJS作为后备方案
        
        // 原生WebSocket端点（不使用SockJS）
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
