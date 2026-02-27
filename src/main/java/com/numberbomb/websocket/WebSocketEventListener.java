package com.numberbomb.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket事件监听器 - 已简化
 * 
 * 注意：已移除 STOMP 事件监听，因为我们已移除 STOMP 支持。
 * 心跳检测和离线处理现在由 GameWebSocketHandler 管理。
 * 
 * 如需重新启用 STOMP，请恢复原始代码。
 */
@Slf4j
@Component
public class WebSocketEventListener {
    
    public WebSocketEventListener() {
        log.info("WebSocketEventListener 已加载（STOMP 事件监听已禁用）");
    }
}
