package com.numberbomb.service;

import com.numberbomb.websocket.GameWebSocketHandler;
import com.numberbomb.websocket.WebSocketMessage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket消息推送服务
 * 只使用原生WebSocket，移除STOMP支持
 * 增强消息可靠性：添加消息缓存、确认和重试机制
 */
@Slf4j
@Service
public class WebSocketService {
    
    private final GameWebSocketHandler gameWebSocketHandler;
    
    // 消息缓存：userId -> (messageId -> CachedMessage)
    private final Map<String, Map<String, CachedMessage>> messageCache = new ConcurrentHashMap<>();
    
    // 待确认消息：messageId -> PendingAckMessage
    private final Map<String, PendingAckMessage> pendingAckMessages = new ConcurrentHashMap<>();
    
    // 定时任务执行器
    private ScheduledExecutorService scheduler;
    
    // 配置常量
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL_MS = 2000;
    private static final long MESSAGE_CACHE_EXPIRY_MS = 60000; // 60秒过期
    private static final long ACK_TIMEOUT_MS = 5000; // 5秒确认超时
    
    public WebSocketService(@Lazy GameWebSocketHandler gameWebSocketHandler) {
        this.gameWebSocketHandler = gameWebSocketHandler;
    }
    
    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "websocket-retry-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 启动定时清理任务
        scheduler.scheduleAtFixedRate(this::cleanupExpiredMessages, 30, 30, TimeUnit.SECONDS);
        // 启动重试检查任务
        scheduler.scheduleAtFixedRate(this::retryPendingMessages, 1, 1, TimeUnit.SECONDS);
    }
    
    /**
     * 缓存消息（用于离线玩家重连后推送）
     */
    private void cacheMessage(String userId, WebSocketMessage message) {
        if (userId == null || message == null || message.getMessageId() == null) {
            return;
        }
        
        Map<String, CachedMessage> userCache = messageCache.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        userCache.put(message.getMessageId(), new CachedMessage(message, System.currentTimeMillis()));
        
        // 限制缓存大小，防止内存溢出
        if (userCache.size() > 100) {
            // 移除最旧的消息
            String oldestKey = userCache.entrySet().stream()
                .min(Map.Entry.comparingByValue((a, b) -> Long.compare(a.timestamp, b.timestamp)))
                .map(Map.Entry::getKey)
                .orElse(null);
            if (oldestKey != null) {
                userCache.remove(oldestKey);
            }
        }
    }
    
    /**
     * 获取并清除用户的缓存消息（重连后调用）
     */
    public Map<String, WebSocketMessage> getCachedMessages(String userId) {
        Map<String, CachedMessage> userCache = messageCache.get(userId);
        if (userCache == null || userCache.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, WebSocketMessage> result = new HashMap<>();
        long now = System.currentTimeMillis();
        
        userCache.forEach((msgId, cached) -> {
            if (now - cached.timestamp < MESSAGE_CACHE_EXPIRY_MS) {
                result.put(msgId, cached.message);
            }
        });
        
        // 清除已获取的消息
        userCache.keySet().removeAll(result.keySet());
        
        return result;
    }
    
    /**
     * 记录待确认消息
     */
    private void recordPendingAck(String userId, WebSocketMessage message) {
        if (message == null || message.getMessageId() == null) {
            return;
        }
        
        pendingAckMessages.put(message.getMessageId(), 
            new PendingAckMessage(userId, message, System.currentTimeMillis(), 0));
    }
    
    /**
     * 处理消息确认
     */
    public void handleMessageAck(String userId, String messageId) {
        log.debug("收到消息确认: userId={}, messageId={}", userId, messageId);
        pendingAckMessages.remove(messageId);
    }
    
    /**
     * 重试待确认消息
     */
    private void retryPendingMessages() {
        long now = System.currentTimeMillis();
        
        pendingAckMessages.forEach((msgId, pending) -> {
            if (now - pending.sendTime > ACK_TIMEOUT_MS && pending.retryCount < MAX_RETRY_COUNT) {
                log.warn("消息未确认，执行重试: messageId={}, retryCount={}", msgId, pending.retryCount + 1);
                pending.retryCount++;
                pending.sendTime = now;
                
                // 重新发送
                sendToUser(Long.parseLong(pending.userId), pending.message);
            } else if (pending.retryCount >= MAX_RETRY_COUNT) {
                log.error("消息重试次数耗尽，放弃重试: messageId={}", msgId);
                pendingAckMessages.remove(msgId);
            }
        });
    }
    
    /**
     * 清理过期消息
     */
    private void cleanupExpiredMessages() {
        long now = System.currentTimeMillis();
        
        // 清理消息缓存
        messageCache.values().forEach(userCache -> 
            userCache.entrySet().removeIf(entry -> 
                now - entry.getValue().timestamp > MESSAGE_CACHE_EXPIRY_MS));
        
        // 清理待确认消息
        pendingAckMessages.entrySet().removeIf(entry -> 
            now - entry.getValue().sendTime > ACK_TIMEOUT_MS * (entry.getValue().retryCount + 1) + 10000);
    }
    
    /**
     * 缓存消息内部类
     */
    private static class CachedMessage {
        final WebSocketMessage message;
        final long timestamp;
        
        CachedMessage(WebSocketMessage message, long timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * 待确认消息内部类
     */
    private static class PendingAckMessage {
        final String userId;
        final WebSocketMessage message;
        long sendTime;
        int retryCount;
        
        PendingAckMessage(String userId, WebSocketMessage message, long sendTime, int retryCount) {
            this.userId = userId;
            this.message = message;
            this.sendTime = sendTime;
            this.retryCount = retryCount;
        }
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
     * 向特定用户发送消息（带缓存和确认机制）
     */
    public void sendToUser(Long userId, WebSocketMessage message) {
        log.debug("发送消息给用户 {}: {}", userId, message.getType());
        
        String userIdStr = userId.toString();
        
        // 缓存消息（用于离线重连后推送）
        cacheMessage(userIdStr, message);
        
        // 发送消息
        gameWebSocketHandler.sendMessageToUser(userIdStr, message);
        
        // 对关键消息类型记录待确认状态
        if (isAckRequiredMessage(message.getType())) {
            recordPendingAck(userIdStr, message);
        }
    }
    
    /**
     * 判断消息类型是否需要确认
     */
    private boolean isAckRequiredMessage(WebSocketMessage.MessageType type) {
        return type == WebSocketMessage.MessageType.GAME_GUESS_RESULT ||
               type == WebSocketMessage.MessageType.GAME_ENDED ||
               type == WebSocketMessage.MessageType.GAME_TURN_CHANGED;
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
     * 通知猜测结果（同时发送给两个玩家，带可靠性保证）
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
        data.put("requiresAck", true); // 标记需要确认
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_GUESS_RESULT, data, gameId, null, playerId != null ? playerId.toString() : null);

        log.info("【WebSocket广播】猜测结果: gameId={}, playerId={}, opponentId={}, guess={}, result={}A{}B, nextPlayer={}",
            gameId, playerId, opponentId, guess, a, b, nextPlayerId);

        // 直接发送给两个玩家（带重试机制）
        sendToUserWithRetry(playerId, message, 3);
        if (opponentId != null) {
            sendToUserWithRetry(opponentId, message, 3);
        }

        // 同时广播到游戏（作为备份通道）
        gameWebSocketHandler.broadcastToGame(gameId, message);
    }
    
    /**
     * 带重试机制的发送
     */
    private void sendToUserWithRetry(Long userId, WebSocketMessage message, int maxRetries) {
        int attempts = 0;
        boolean sent = false;
        
        while (attempts < maxRetries && !sent) {
            try {
                sendToUser(userId, message);
                sent = true;
            } catch (Exception e) {
                attempts++;
                log.warn("发送消息失败，准备重试: userId={}, messageId={}, attempt={}/{}", 
                    userId, message.getMessageId(), attempts, maxRetries);
                if (attempts < maxRetries) {
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        if (!sent) {
            log.error("消息发送失败，重试次数耗尽: userId={}, messageId={}", userId, message.getMessageId());
        }
    }

    /**
     * 通知游戏结束（同时发送给两个玩家，带可靠性保证）
     */
    public void notifyGameEnded(Long gameId, Long winnerId, Long loserId, Map<String, Object> gameResult) {
        Map<String, Object> data = new HashMap<>(gameResult);
        data.put("gameId", gameId);
        data.put("winnerId", winnerId);
        data.put("loserId", loserId);
        data.put("timestamp", System.currentTimeMillis());
        data.put("requiresAck", true); // 标记需要确认
        WebSocketMessage message = buildMessage(WebSocketMessage.MessageType.GAME_ENDED, data, gameId, null, null);

        log.info("【WebSocket广播】游戏结束: gameId={}, winnerId={}, loserId={}", gameId, winnerId, loserId);

        // 直接发送给两个玩家（带重试机制）
        sendToUserWithRetry(winnerId, message, 3);
        sendToUserWithRetry(loserId, message, 3);

        // 同时广播到游戏（作为备份通道）
        gameWebSocketHandler.broadcastToGame(gameId, message);
        
        // 延迟再次发送（确保对方收到）
        scheduler.schedule(() -> {
            log.info("【WebSocket广播】游戏结束消息延迟重发: gameId={}", gameId);
            sendToUser(winnerId, message);
            sendToUser(loserId, message);
        }, 2, TimeUnit.SECONDS);
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
