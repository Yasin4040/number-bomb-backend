package com.numberbomb.controller;

import com.numberbomb.vo.Result;
import com.numberbomb.websocket.GameWebSocketHandler;
import com.numberbomb.websocket.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 测试控制器
 * 用于测试广播功能
 */
@Slf4j
@RestController
@RequestMapping("/api/test/websocket")
@RequiredArgsConstructor
public class WebSocketTestController {

    private final GameWebSocketHandler gameWebSocketHandler;

    /**
     * 测试广播消息到游戏
     */
    @PostMapping("/broadcast/game/{gameId}")
    public Result<String> broadcastToGame(
            @PathVariable Long gameId,
            @RequestBody Map<String, Object> messageData) {
        
        log.info("【测试API】广播消息到游戏 {}: {}", gameId, messageData);
        
        String messageType = (String) messageData.getOrDefault("type", "GAME_UPDATED");
        WebSocketMessage.MessageType type = WebSocketMessage.MessageType.valueOf(messageType);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) messageData.getOrDefault("data", new HashMap<>());
        data.put("gameId", gameId);
        data.put("timestamp", System.currentTimeMillis());
        data.put("test", true);
        
        WebSocketMessage message = WebSocketMessage.success(type, data);
        gameWebSocketHandler.broadcastToGame(gameId, message);
        
        return Result.success("广播消息已发送到游戏 " + gameId);
    }

    /**
     * 测试广播消息到房间
     */
    @PostMapping("/broadcast/room/{roomId}")
    public Result<String> broadcastToRoom(
            @PathVariable Long roomId,
            @RequestBody Map<String, Object> messageData) {
        
        log.info("【测试API】广播消息到房间 {}: {}", roomId, messageData);
        
        String messageType = (String) messageData.getOrDefault("type", "ROOM_UPDATED");
        WebSocketMessage.MessageType type = WebSocketMessage.MessageType.valueOf(messageType);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) messageData.getOrDefault("data", new HashMap<>());
        data.put("roomId", roomId);
        data.put("timestamp", System.currentTimeMillis());
        data.put("test", true);
        
        WebSocketMessage message = WebSocketMessage.success(type, data);
        gameWebSocketHandler.broadcastToRoom(roomId, message);
        
        return Result.success("广播消息已发送到房间 " + roomId);
    }

    /**
     * 测试发送消息给特定用户
     */
    @PostMapping("/send/user/{userId}")
    public Result<String> sendToUser(
            @PathVariable String userId,
            @RequestBody Map<String, Object> messageData) {
        
        log.info("【测试API】发送消息给用户 {}: {}", userId, messageData);
        
        String messageType = (String) messageData.getOrDefault("type", "NOTIFICATION");
        WebSocketMessage.MessageType type = WebSocketMessage.MessageType.valueOf(messageType);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) messageData.getOrDefault("data", new HashMap<>());
        data.put("timestamp", System.currentTimeMillis());
        data.put("test", true);
        
        WebSocketMessage message = WebSocketMessage.success(type, data);
        gameWebSocketHandler.sendMessageToUser(userId, message);
        
        return Result.success("消息已发送给用户 " + userId);
    }

    /**
     * 获取当前连接统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("message", "WebSocket 测试接口正常工作");
        stats.put("timestamp", System.currentTimeMillis());
        return Result.success(stats);
    }
}
