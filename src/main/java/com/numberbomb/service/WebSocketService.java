package com.numberbomb.service;

import com.numberbomb.websocket.GameWebSocketHandler;
import com.numberbomb.websocket.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket消息推送服务
 * 只使用原生WebSocket，移除STOMP支持
 */
@Slf4j
@Service
public class WebSocketService {
    
    private final GameWebSocketHandler gameWebSocketHandler;
    
    public WebSocketService(@Lazy GameWebSocketHandler gameWebSocketHandler) {
        this.gameWebSocketHandler = gameWebSocketHandler;
    }
    
    /**
     * 生成消息ID
     */
    private String generateMessageId() {
        return System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 构建完整的WebSocket消息
     */
    private WebSocketMessage buildMessage(WebSocketMessage.MessageType type, Map<String, Object> data, 
                                          Long gameId, Long roomId, String userId) {
        return WebSocketMessage.builder()
                .type(type)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .messageId(generateMessageId())
                .gameId(gameId)
                .roomId(roomId)
                .userId(userId)
                .build();
    }
    
    /**
     * 向房间内所有玩家广播消息
     */
    public void broadcastToRoom(Long roomId, WebSocketMessage message) {
        log.debug("广播消息到房间 {}: {}", roomId, message.getType());
        // 通过原生WebSocket广播
        gameWebSocketHandler.broadcastToRoom(roomId, message);
    }
    
    /**
     * 向特定用户发送消息
     */
    public void sendToUser(Long userId, WebSocketMessage message) {
        log.debug("发送消息给用户 {}: {}", userId, message.getType());
        gameWebSocketHandler.sendMessageToUser(userId.toString(), message);
    }
    
    /**
     * 向临时用户发送消息
     */
    public void sendToTempUser(String tempUserId, WebSocketMessage message) {
        log.debug("发送消息给临时用户 {}: {}", tempUserId, message.getType());
        gameWebSocketHandler.sendMessageToUser(tempUserId, message);
    }
    
    /**
     * 通知玩家加入房间
     */
    public void notifyPlayerJoined(Long roomId, Map<String, Object> playerInfo) {
        Map<String, Object> data = new HashMap<>();
        data.put("player", playerInfo);
        data.put("roomId", roomId);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.ROOM_JOINED, data, null, roomId, null);
        broadcastToRoom(roomId, message);
    }

    /**
     * 通知玩家离开房间
     */
    public void notifyPlayerLeft(Long roomId, Long playerId) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerId", playerId);
        data.put("roomId", roomId);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.ROOM_LEFT, data, null, roomId, playerId != null ? playerId.toString() : null);
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
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.ROOM_PLAYER_READY, data, null, roomId, playerId != null ? playerId.toString() : null);
        broadcastToRoom(roomId, message);
    }

    /**
     * 通知玩家设置数字
     */
    public void notifySecretSet(Long roomId, Long playerId) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerId", playerId);
        data.put("roomId", roomId);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.ROOM_SECRET_SET, data, null, roomId, playerId != null ? playerId.toString() : null);
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
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.ROOM_TURN_ORDER_SET, data, null, roomId, playerId != null ? playerId.toString() : null);
        broadcastToRoom(roomId, message);
    }

    /**
     * 通知开始倒计时
     */
    public void notifyStartCountdown(Long roomId, Integer countdown) {
        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("countdown", countdown);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.ROOM_START_COUNTDOWN, data, null, roomId, null);
        broadcastToRoom(roomId, message);
    }

    /**
     * 通知游戏开始
     */
    public void notifyGameStarted(Long roomId, Long gameId) {
        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("gameId", gameId);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.ROOM_GAME_STARTED, data, gameId, roomId, null);
        broadcastToRoom(roomId, message);
    }

    /**
     * 通知房间信息更新
     */
    public void notifyRoomUpdated(Long roomId, Map<String, Object> roomInfo) {
        Map<String, Object> data = new HashMap<>(roomInfo);
        data.put("roomId", roomId);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.ROOM_UPDATED, data, null, roomId, null);
        broadcastToRoom(roomId, message);
    }
    
    /**
     * 通知匹配成功
     */
    public void notifyMatchSuccess(Long userId, Long opponentId, Map<String, Object> opponentInfo) {
        Map<String, Object> data = new HashMap<>();
        data.put("opponentId", opponentId);
        data.put("opponent", opponentInfo);
        data.put("countdown", 3);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.MATCH_SUCCESS, data, null, null, userId != null ? userId.toString() : null);

        // 直接发送给两个玩家（确保都能收到）
        sendToUser(userId, message);
        sendToUser(opponentId, message);

        log.debug("通知匹配成功: userId={}, opponentId={}", userId, opponentId);
    }

    /**
     * 通知匹配状态更新
     */
    public void notifyMatchStatusUpdated(Long userId, String matchId, Map<String, Object> status) {
        Map<String, Object> data = new HashMap<>(status);
        data.put("matchId", matchId);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.MATCH_STATUS_UPDATED, data, null, null, userId != null ? userId.toString() : null);
        sendToUser(userId, message);
        log.debug("通知匹配状态更新: userId={}, matchId={}", userId, matchId);
    }
    
    /**
     * 通知回合切换（同时发送给两个玩家）
     */
    public void notifyTurnChanged(Long gameId, Long currentPlayerId, Long opponentId, Integer countdown) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", gameId);
        data.put("currentPlayerId", currentPlayerId);
        data.put("countdown", countdown);
        data.put("timestamp", System.currentTimeMillis());
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_TURN_CHANGED, data, gameId, null, currentPlayerId != null ? currentPlayerId.toString() : null);

        log.info("【WebSocket广播】回合切换: gameId={}, currentPlayerId={}, opponentId={}, countdown={}",
            gameId, currentPlayerId, opponentId, countdown);

        // 直接发送给两个玩家
        sendToUser(currentPlayerId, message);
        if (opponentId != null) {
            sendToUser(opponentId, message);
        }

        // 同时广播到游戏房间
        gameWebSocketHandler.broadcastToGame(gameId, message);
    }

    /**
     * 通知猜测结果（同时发送给两个玩家）
     */
    public void notifyGuessResult(Long gameId, Long playerId, Long opponentId, String guess,
                                   Integer a, Integer b, Boolean isGameOver,
                                   String playerName, Integer round, Boolean isWin,
                                   Long nextPlayerId, Integer countdown) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", gameId);
        data.put("playerId", playerId);
        data.put("playerName", playerName != null ? playerName : "玩家" + playerId);
        data.put("guess", guess);
        data.put("round", round != null ? round : 1);
        // 构建 result 对象，符合前端期望格式
        Map<String, Integer> result = new HashMap<>();
        result.put("a", a);
        result.put("b", b);
        data.put("result", result);
        data.put("isWin", isWin != null ? isWin : (a == 4 && b == 0));
        data.put("isGameOver", isGameOver);
        data.put("nextPlayer", nextPlayerId);
        data.put("countdown", countdown != null ? countdown : 180);
        data.put("timestamp", System.currentTimeMillis());
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_GUESS_RESULT, data, gameId, null, playerId != null ? playerId.toString() : null);

        log.info("【WebSocket广播】猜测结果: gameId={}, playerId={}, opponentId={}, guess={}, result={}A{}B, nextPlayer={}",
            gameId, playerId, opponentId, guess, a, b, nextPlayerId);

        // 直接发送给两个玩家
        sendToUser(playerId, message);
        if (opponentId != null) {
            sendToUser(opponentId, message);
        }

        // 同时广播到游戏
        gameWebSocketHandler.broadcastToGame(gameId, message);
    }

    /**
     * 通知游戏结束（同时发送给两个玩家）
     */
    public void notifyGameEnded(Long gameId, Long winnerId, Long loserId, Map<String, Object> gameResult) {
        Map<String, Object> data = new HashMap<>(gameResult);
        data.put("gameId", gameId);
        data.put("winnerId", winnerId);
        data.put("loserId", loserId);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_ENDED, data, gameId, null, null);

        // 直接发送给两个玩家
        sendToUser(winnerId, message);
        sendToUser(loserId, message);

        // 同时广播到游戏
        gameWebSocketHandler.broadcastToGame(gameId, message);

        log.debug("通知游戏结束: gameId={}, winnerId={}, loserId={}", gameId, winnerId, loserId);
    }

    /**
     * 通知游戏状态更新
     */
    public void notifyGameUpdated(Long gameId, Map<String, Object> gameState) {
        Map<String, Object> data = new HashMap<>(gameState);
        data.put("gameId", gameId);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_UPDATED, data, gameId, null, null);
        gameWebSocketHandler.broadcastToGame(gameId, message);
    }
    
    /**
     * 发送错误消息
     */
    public void sendError(Long userId, String errorMessage) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.ERROR)
                .error(errorMessage)
                .timestamp(System.currentTimeMillis())
                .messageId(generateMessageId())
                .userId(userId != null ? userId.toString() : null)
                .build();
        sendToUser(userId, message);
    }

    /**
     * 通知再来一局请求
     */
    public void notifyRestartRequested(Long roomId, Long requesterId, String requesterName) {
        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("requesterId", requesterId);
        data.put("requesterName", requesterName);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_RESTART_REQUESTED, data, null, roomId, requesterId != null ? requesterId.toString() : null);
        broadcastToRoom(roomId, message);
        log.debug("通知再来一局请求: roomId={}, requesterId={}", roomId, requesterId);
    }

    /**
     * 通知再来一局被接受
     */
    public void notifyRestartAccepted(Long roomId, Long accepterId) {
        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("accepterId", accepterId);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_RESTART_ACCEPTED, data, null, roomId, accepterId != null ? accepterId.toString() : null);
        broadcastToRoom(roomId, message);
        log.debug("通知再来一局被接受: roomId={}, accepterId={}", roomId, accepterId);
    }

    /**
     * 通知再来一局被拒绝
     */
    public void notifyRestartRejected(Long roomId, Long rejecterId, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("rejecterId", rejecterId);
        data.put("reason", reason);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_RESTART_REJECTED, data, null, roomId, rejecterId != null ? rejecterId.toString() : null);
        broadcastToRoom(roomId, message);
        log.debug("通知再来一局被拒绝: roomId={}, rejecterId={}", roomId, rejecterId);
    }

    /**
     * 通知再来一局准备就绪
     */
    public void notifyRestartReady(Long roomId, Map<String, Object> roomInfo) {
        Map<String, Object> data = new HashMap<>(roomInfo);
        data.put("roomId", roomId);
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_RESTART_READY, data, null, roomId, null);
        broadcastToRoom(roomId, message);
        log.debug("通知再来一局准备就绪: roomId={}", roomId);
    }

    /**
     * 通知玩家回合超时
     */
    public void notifyTimeout(Long gameId, Long playerId, Map<String, Object> timeoutData) {
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.NOTIFICATION, timeoutData, gameId, null, playerId != null ? playerId.toString() : null);
        log.info("【WebSocket广播】通知超时: gameId={}, playerId={}", gameId, playerId);
        gameWebSocketHandler.broadcastToGame(gameId, message);
    }

    /**
     * 通知玩家离线
     */
    public void notifyPlayerOffline(Long gameId, Long playerId, Long opponentId) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", gameId);
        data.put("playerId", playerId);
        data.put("message", "对方已离线");
        data.put("timestamp", System.currentTimeMillis());
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_PLAYER_OFFLINE, data, gameId, null, playerId != null ? playerId.toString() : null);

        log.info("【WebSocket广播】通知玩家离线: gameId={}, playerId={}", gameId, playerId);

        // 发送给对手
        if (opponentId != null) {
            sendToUser(opponentId, message);
        }

        // 广播到游戏
        gameWebSocketHandler.broadcastToGame(gameId, message);
    }

    /**
     * 通知玩家重连
     */
    public void notifyPlayerReconnected(Long gameId, Long playerId, Long opponentId) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", gameId);
        data.put("playerId", playerId);
        data.put("message", "对方已重连");
        data.put("timestamp", System.currentTimeMillis());
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_PLAYER_RECONNECTED, data, gameId, null, playerId != null ? playerId.toString() : null);

        log.info("【WebSocket广播】通知玩家重连: gameId={}, playerId={}", gameId, playerId);

        // 发送给对手
        if (opponentId != null) {
            sendToUser(opponentId, message);
        }

        // 广播到游戏
        gameWebSocketHandler.broadcastToGame(gameId, message);
    }

    /**
     * 通知玩家放弃游戏
     */
    public void notifyPlayerGiveUp(Long gameId, Long playerId, Long opponentId) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", gameId);
        data.put("playerId", playerId);
        data.put("message", "对方已放弃游戏");
        data.put("timestamp", System.currentTimeMillis());
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_PLAYER_GIVEUP, data, gameId, null, playerId != null ? playerId.toString() : null);

        log.info("【WebSocket广播】通知玩家放弃: gameId={}, playerId={}, opponentId={}", gameId, playerId, opponentId);

        // 发送给放弃方（确认放弃成功）
        sendToUser(playerId, message);

        // 发送给对手（通知对方获胜）
        if (opponentId != null) {
            sendToUser(opponentId, message);
        }

        // 广播到游戏
        gameWebSocketHandler.broadcastToGame(gameId, message);
    }

    /**
     * 处理消息确认
     */
    public void handleMessageAck(Long userId, String messageId, String status) {
        log.debug("收到消息确认: userId={}, messageId={}, status={}", userId, messageId, status);
        // 这里可以添加消息确认的业务逻辑
    }
}
