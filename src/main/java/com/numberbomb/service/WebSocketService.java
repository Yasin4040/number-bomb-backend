package com.numberbomb.service;

import com.numberbomb.websocket.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket消息推送服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * 向房间内所有玩家广播消息
     */
    public void broadcastToRoom(Long roomId, WebSocketMessage message) {
        String destination = "/topic/room/" + roomId;
        log.debug("广播消息到房间 {}: {}", roomId, message.getType());
        messagingTemplate.convertAndSend(destination, message);
    }
    
    /**
     * 向特定用户发送消息
     */
    public void sendToUser(Long userId, WebSocketMessage message) {
        String destination = "/user/" + userId + "/queue/messages";
        log.debug("发送消息给用户 {}: {}", userId, message.getType());
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", message);
    }
    
    /**
     * 向临时用户发送消息
     */
    public void sendToTempUser(String tempUserId, WebSocketMessage message) {
        String destination = "/user/temp_" + tempUserId + "/queue/messages";
        log.debug("发送消息给临时用户 {}: {}", tempUserId, message.getType());
        messagingTemplate.convertAndSendToUser("temp_" + tempUserId, "/queue/messages", message);
    }
    
    /**
     * 通知玩家加入房间
     */
    public void notifyPlayerJoined(Long roomId, Map<String, Object> playerInfo) {
        Map<String, Object> data = new HashMap<>();
        data.put("player", playerInfo);
        data.put("roomId", roomId);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.ROOM_JOINED, data);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * 通知玩家离开房间
     */
    public void notifyPlayerLeft(Long roomId, Long playerId) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerId", playerId);
        data.put("roomId", roomId);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.ROOM_LEFT, data);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * 通知玩家准备状态变化
     */
    public void notifyPlayerReady(Long roomId, Long playerId, Boolean isReady) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerId", playerId);
        data.put("isReady", isReady);
        data.put("roomId", roomId);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.ROOM_PLAYER_READY, data);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * 通知玩家设置数字
     */
    public void notifySecretSet(Long roomId, Long playerId) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerId", playerId);
        data.put("roomId", roomId);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.ROOM_SECRET_SET, data);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * 通知玩家选择先手/后手
     */
    public void notifyTurnOrderSet(Long roomId, Long playerId, Integer turnOrder) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerId", playerId);
        data.put("turnOrder", turnOrder);
        data.put("roomId", roomId);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.ROOM_TURN_ORDER_SET, data);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * 通知开始倒计时
     */
    public void notifyStartCountdown(Long roomId, Integer countdown) {
        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("countdown", countdown);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.ROOM_START_COUNTDOWN, data);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * 通知游戏开始
     */
    public void notifyGameStarted(Long roomId, Long gameId) {
        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("gameId", gameId);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.ROOM_GAME_STARTED, data);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * 通知房间信息更新
     */
    public void notifyRoomUpdated(Long roomId, Map<String, Object> roomInfo) {
        Map<String, Object> data = new HashMap<>(roomInfo);
        data.put("roomId", roomId);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.ROOM_UPDATED, data);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * 通知回合切换
     */
    public void notifyTurnChanged(Long gameId, Long currentPlayerId, Integer countdown) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", gameId);
        data.put("currentPlayerId", currentPlayerId);
        data.put("countdown", countdown);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.GAME_TURN_CHANGED, data);
        String destination = "/topic/game/" + gameId;
        messagingTemplate.convertAndSend(destination, message);
    }
    
    /**
     * 通知猜测结果
     */
    public void notifyGuessResult(Long gameId, Long playerId, String guess, Integer a, Integer b, Boolean isGameOver) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", gameId);
        data.put("playerId", playerId);
        data.put("guess", guess);
        data.put("a", a);
        data.put("b", b);
        data.put("isGameOver", isGameOver);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.GAME_GUESS_RESULT, data);
        String destination = "/topic/game/" + gameId;
        messagingTemplate.convertAndSend(destination, message);
    }
    
    /**
     * 通知游戏结束
     */
    public void notifyGameEnded(Long gameId, Long winnerId, Map<String, Object> gameResult) {
        Map<String, Object> data = new HashMap<>(gameResult);
        data.put("gameId", gameId);
        data.put("winnerId", winnerId);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.GAME_ENDED, data);
        String destination = "/topic/game/" + gameId;
        messagingTemplate.convertAndSend(destination, message);
    }
    
    /**
     * 通知游戏状态更新
     */
    public void notifyGameUpdated(Long gameId, Map<String, Object> gameState) {
        Map<String, Object> data = new HashMap<>(gameState);
        data.put("gameId", gameId);
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.GAME_UPDATED, data);
        String destination = "/topic/game/" + gameId;
        messagingTemplate.convertAndSend(destination, message);
    }
    
    /**
     * 发送错误消息
     */
    public void sendError(Long userId, String errorMessage) {
        WebSocketMessage message = WebSocketMessage.error(errorMessage);
        sendToUser(userId, message);
    }
}
