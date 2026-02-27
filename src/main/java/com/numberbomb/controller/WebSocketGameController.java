package com.numberbomb.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

/**
 * WebSocket 游戏控制器 - 已禁用
 * 
 * 注意：此控制器已禁用，因为我们已移除 STOMP 支持。
 * 所有 WebSocket 消息现在通过 GameWebSocketHandler（原生 WebSocket）处理。
 * 
 * 如需重新启用 STOMP，请恢复原始代码并更新 WebSocketConfig。
 */
@Slf4j
@Controller
public class WebSocketGameController {
    
    public WebSocketGameController() {
        log.info("WebSocketGameController 已加载（STOMP 已禁用）");
    }
}
