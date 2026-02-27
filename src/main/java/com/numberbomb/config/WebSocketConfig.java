package com.numberbomb.config;

import com.numberbomb.websocket.GameWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 * 只支持原生WebSocket（用于小程序等环境）
 * 移除STOMP协议支持，避免双协议冲突
 */
@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final WebSocketInterceptor webSocketInterceptor;
    
    public WebSocketConfig(@Lazy GameWebSocketHandler gameWebSocketHandler, 
                          WebSocketInterceptor webSocketInterceptor) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.webSocketInterceptor = webSocketInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册原生WebSocket处理器（用于小程序等环境）
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketInterceptor);
        
        log.info("原生WebSocket处理器已注册: /ws/game");
    }
}
