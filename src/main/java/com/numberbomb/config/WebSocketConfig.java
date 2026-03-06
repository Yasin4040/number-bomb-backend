package com.numberbomb.config;

import com.numberbomb.websocket.GameWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 * 统一使用GameWebSocketHandler处理所有WebSocket消息
 * 支持普通对战和语音对战
 * 移除STOMP协议支持，避免双协议冲突
 */
@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final WebSocketInterceptor webSocketInterceptor;
    
    @Autowired
    public WebSocketConfig(@Lazy GameWebSocketHandler gameWebSocketHandler,
                          WebSocketInterceptor webSocketInterceptor) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.webSocketInterceptor = webSocketInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册统一的WebSocket处理器（支持普通对战和语音对战）
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketInterceptor);
        
        log.info("统一WebSocket处理器已注册: /ws/game");
    }
}
