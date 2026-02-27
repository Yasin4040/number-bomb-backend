package com.numberbomb.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.numberbomb.service.GameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 原生 WebSocket 处理器
 * 统一处理所有 WebSocket 消息，管理用户 session
 * 移除 STOMP 支持，避免双协议冲突
 */
@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final GameService gameService;

    public GameWebSocketHandler(ObjectMapper objectMapper, @Lazy GameService gameService) {
        this.objectMapper = objectMapper;
        this.gameService = gameService;
    }

    // 存储用户 session: userId -> WebSocketSession
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    // 存储用户订阅的游戏: userId -> gameId
    private final Map<String, Long> userGameSubscriptions = new ConcurrentHashMap<>();

    // 存储房间订阅: roomId -> Set<userId>
    private final Map<Long, Set<String>> roomSubscriptions = new ConcurrentHashMap<>();

    // 存储游戏订阅: gameId -> Set<userId>
    private final Map<Long, Set<String>> gameSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        String uri = session.getUri() != null ? session.getUri().toString() : "unknown";
        
        log.info("【WebSocket连接】连接建立: sessionId={}, uri={}", sessionId, uri);

        // 从 URL 参数中获取用户信息
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            // 关闭该用户的旧连接（如果有）
            WebSocketSession oldSession = userSessions.get(userId);
            if (oldSession != null && oldSession.isOpen() && !oldSession.getId().equals(sessionId)) {
                log.info("【WebSocket连接】关闭用户 {} 的旧连接: oldSessionId={}", userId, oldSession.getId());
                try {
                    oldSession.close(CloseStatus.NORMAL);
                } catch (Exception e) {
                    log.warn("关闭旧连接失败: {}", e.getMessage());
                }
            }
            
            // 存储新连接
            userSessions.put(userId, session);
            session.getAttributes().put("userId", userId);
            
            log.info("【WebSocket连接】用户 {} 已注册, sessionId={}", userId, sessionId);
            
            // 发送连接成功消息
            sendConnectedMessage(session, userId);
        } else {
            log.warn("【WebSocket连接】无法识别用户身份, sessionId={}", sessionId);
            sendErrorMessage(session, "无法识别用户身份");
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String sessionId = session.getId();
        String userId = getUserIdFromSession(session);
        
        log.debug("【WebSocket消息】收到消息: sessionId={}, userId={}, payload={}", sessionId, userId, payload);

        try {
            WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);
            handleWebSocketMessage(session, userId, wsMessage);
        } catch (Exception e) {
            log.error("【WebSocket消息】处理消息失败: {}", e.getMessage());
            sendErrorMessage(session, "消息格式错误: " + e.getMessage());
        }
    }

    private void handleWebSocketMessage(WebSocketSession session, String userId, WebSocketMessage message) throws IOException {
        if (userId == null) {
            log.warn("【WebSocket消息】无法获取用户ID");
            sendErrorMessage(session, "无法获取用户ID");
            return;
        }

        WebSocketMessage.MessageType type = message.getType();
        log.info("【WebSocket消息】处理消息: userId={}, type={}", userId, type);

        switch (type) {
            case CONNECT:
                handleConnect(session, userId, message);
                break;
            case DISCONNECT:
                handleDisconnect(userId, message);
                break;
            case PLAYER_MAKE_GUESS:
                handleGuess(userId, message);
                break;
            case GAME_STATE_SYNC:
                handleGameStateSync(session, userId, message);
                break;
            case PING:
                handlePing(session);
                break;
            case PLAYER_GIVE_UP:
                handleGiveUp(userId, message);
                break;
            default:
                log.warn("【WebSocket消息】未处理的消息类型: {}", type);
                sendErrorMessage(session, "未知消息类型: " + type);
        }
    }

    private void handleConnect(WebSocketSession session, String userId, WebSocketMessage message) throws IOException {
        Map<String, Object> data = message.getData();
        if (data == null) {
            sendErrorMessage(session, "消息数据为空");
            return;
        }

        // 处理游戏订阅
        if (data.containsKey("gameId")) {
            Long gameId = parseLong(data.get("gameId"));
            if (gameId != null) {
                userGameSubscriptions.put(userId, gameId);
                gameSubscriptions.computeIfAbsent(gameId, k -> new CopyOnWriteArraySet<>()).add(userId);
                log.info("【WebSocket订阅】用户 {} 订阅游戏 {}", userId, gameId);
                
                // 发送确认消息
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("gameId", gameId);
                responseData.put("status", "subscribed");
                responseData.put("userId", userId);
                
                WebSocketMessage response = WebSocketMessage.builder()
                        .type(WebSocketMessage.MessageType.CONNECT)
                        .data(responseData)
                        .timestamp(System.currentTimeMillis())
                        .messageId(message.getMessageId()) // 携带原 messageId
                        .gameId(gameId)
                        .userId(userId)
                        .roomId(message.getRoomId())
                        .build();
                sendMessage(session, response);
            }
        }

        // 处理房间订阅
        if (data.containsKey("roomId")) {
            Long roomId = parseLong(data.get("roomId"));
            if (roomId != null) {
                roomSubscriptions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(userId);
                log.info("【WebSocket订阅】用户 {} 订阅房间 {}", userId, roomId);
                
                // 发送确认消息
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("roomId", roomId);
                responseData.put("status", "subscribed");
                responseData.put("userId", userId);
                
                WebSocketMessage response = WebSocketMessage.builder()
                        .type(WebSocketMessage.MessageType.CONNECT)
                        .data(responseData)
                        .timestamp(System.currentTimeMillis())
                        .messageId(message.getMessageId()) // 携带原 messageId
                        .gameId(null) // 房间订阅没有 gameId
                        .userId(userId)
                        .roomId(roomId)
                        .build();
                sendMessage(session, response);
            }
        }
    }

    private void handleDisconnect(String userId, WebSocketMessage message) {
        Map<String, Object> data = message.getData();
        if (data == null) return;

        // 取消游戏订阅
        if (data.containsKey("gameId")) {
            Long gameId = parseLong(data.get("gameId"));
            if (gameId != null) {
                userGameSubscriptions.remove(userId);
                Set<String> subscribers = gameSubscriptions.get(gameId);
                if (subscribers != null) {
                    subscribers.remove(userId);
                }
                log.info("【WebSocket订阅】用户 {} 取消订阅游戏 {}", userId, gameId);
            }
        }

        // 取消房间订阅
        if (data.containsKey("roomId")) {
            Long roomId = parseLong(data.get("roomId"));
            if (roomId != null) {
                Set<String> subscribers = roomSubscriptions.get(roomId);
                if (subscribers != null) {
                    subscribers.remove(userId);
                }
                log.info("【WebSocket订阅】用户 {} 取消订阅房间 {}", userId, roomId);
            }
        }
    }

    private void handleGuess(String userId, WebSocketMessage message) {
        Map<String, Object> data = message.getData();
        if (data == null || !data.containsKey("guess")) {
            log.warn("【WebSocket消息】猜测消息缺少 guess 字段");
            WebSocketSession session = userSessions.get(userId);
            if (session != null) {
                try {
                    sendErrorMessage(session, "猜测消息缺少 guess 字段");
                } catch (IOException e) {
                    log.error("发送错误消息失败: {}", e.getMessage());
                }
            }
            return;
        }

        Long gameId = userGameSubscriptions.get(userId);
        if (gameId == null) {
            log.warn("【WebSocket消息】用户 {} 未订阅游戏", userId);
            WebSocketSession session = userSessions.get(userId);
            if (session != null) {
                try {
                    sendErrorMessage(session, "未订阅游戏");
                } catch (IOException e) {
                    log.error("发送错误消息失败: {}", e.getMessage());
                }
            }
            return;
        }

        String guess = data.get("guess").toString();
        Long userIdLong = parseLong(userId);

        try {
            log.info("【WebSocket消息】用户 {} 在游戏 {} 提交猜测: {}", userId, gameId, guess);
            gameService.makeGuess(gameId, userIdLong, guess);
        } catch (Exception e) {
            log.error("【WebSocket消息】处理猜测失败: {}", e.getMessage());
            WebSocketSession session = userSessions.get(userId);
            if (session != null) {
                try {
                    sendErrorMessage(session, "处理猜测失败: " + e.getMessage());
                } catch (IOException ex) {
                    log.error("发送错误消息失败: {}", ex.getMessage());
                }
            }
        }
    }

    private void handleGameStateSync(WebSocketSession session, String userId, WebSocketMessage message) throws IOException {
        Long gameId = userGameSubscriptions.get(userId);
        if (gameId == null) {
            sendErrorMessage(session, "未订阅游戏");
            return;
        }

        // 获取客户端传来的 lastHistoryIndex，用于增量同步
        Map<String, Object> requestData = message.getData();
        Integer lastHistoryIndex = null;
        if (requestData != null && requestData.containsKey("lastHistoryIndex")) {
            lastHistoryIndex = parseInt(requestData.get("lastHistoryIndex"));
        }

        // 获取精简版游戏状态
        Map<String, Object> gameState = gameService.getGameState(gameId, parseLong(userId), lastHistoryIndex);
        if (gameState != null) {
            // 构建精简响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("gameId", gameId);
            responseData.put("currentPlayer", gameState.get("currentPlayer"));
            responseData.put("countdown", gameState.get("countdown"));
            responseData.put("status", gameState.get("status"));
            responseData.put("history", gameState.get("history"));
            responseData.put("players", gameState.get("players"));
            
            // 只包含当前用户需要的敏感信息
            if (gameState.containsKey("mySecretNumber")) {
                responseData.put("mySecretNumber", gameState.get("mySecretNumber"));
            }
            
            WebSocketMessage response = WebSocketMessage.builder()
                    .type(WebSocketMessage.MessageType.GAME_STATE_SYNC)
                    .data(responseData)
                    .timestamp(System.currentTimeMillis())
                    .messageId(message.getMessageId()) // 携带原 messageId 便于前端匹配
                    .gameId(gameId)
                    .roomId(message.getRoomId())
                    .userId(userId)
                    .build();
            sendMessage(session, response);
        }
    }

    private void handlePing(WebSocketSession session) throws IOException {
        log.debug("【WebSocket心跳】收到 PING, sessionId={}", session.getId());
        WebSocketMessage pong = WebSocketMessage.pong();
        sendMessage(session, pong);
    }

    private void handleGiveUp(String userId, WebSocketMessage message) {
        Long gameId = userGameSubscriptions.get(userId);
        if (gameId == null) {
            log.warn("【WebSocket消息】用户 {} 未订阅游戏，无法放弃", userId);
            WebSocketSession session = userSessions.get(userId);
            if (session != null) {
                try {
                    sendErrorMessage(session, "未订阅游戏，无法放弃");
                } catch (IOException e) {
                    log.error("发送错误消息失败: {}", e.getMessage());
                }
            }
            return;
        }

        try {
            log.info("【WebSocket消息】用户 {} 放弃游戏 {}", userId, gameId);
            gameService.giveUpGame(gameId, parseLong(userId));
        } catch (Exception e) {
            log.error("【WebSocket消息】处理放弃游戏失败: {}", e.getMessage());
            WebSocketSession session = userSessions.get(userId);
            if (session != null) {
                try {
                    sendErrorMessage(session, "处理放弃游戏失败: " + e.getMessage());
                } catch (IOException ex) {
                    log.error("发送错误消息失败: {}", ex.getMessage());
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        String userId = getUserIdFromSession(session);
        
        if (userId != null) {
            // 只移除当前 session 的映射（如果是当前用户的连接）
            WebSocketSession currentSession = userSessions.get(userId);
            if (currentSession != null && currentSession.getId().equals(sessionId)) {
                userSessions.remove(userId);
                userGameSubscriptions.remove(userId);
                
                // 从所有房间订阅中移除
                roomSubscriptions.values().forEach(subscribers -> subscribers.remove(userId));
                // 从所有游戏订阅中移除
                gameSubscriptions.values().forEach(subscribers -> subscribers.remove(userId));
                
                log.info("【WebSocket连接】用户 {} 断开连接, sessionId={}, status={}", userId, sessionId, status);
            }
        } else {
            log.info("【WebSocket连接】未知用户断开连接, sessionId={}, status={}", sessionId, status);
        }
    }

    /**
     * 发送消息给特定用户
     */
    public void sendMessageToUser(String userId, WebSocketMessage message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                sendMessage(session, message);
                log.debug("【WebSocket发送】消息已发送给用户 {}: {}", userId, message.getType());
            } catch (IOException e) {
                log.error("【WebSocket发送】发送消息给用户 {} 失败: {}", userId, e.getMessage());
            }
        } else {
            log.warn("【WebSocket发送】用户 {} 不在线或连接已关闭", userId);
        }
    }

    /**
     * 广播消息给房间的所有玩家
     */
    public void broadcastToRoom(Long roomId, WebSocketMessage message) {
        Set<String> subscribers = roomSubscriptions.get(roomId);
        if (subscribers == null || subscribers.isEmpty()) {
            log.warn("【WebSocket广播】房间 {} 没有订阅者", roomId);
            return;
        }

        log.info("【WebSocket广播】广播消息到房间 {}: {}, 订阅者数量: {}", roomId, message.getType(), subscribers.size());
        
        for (String userId : subscribers) {
            sendMessageToUser(userId, message);
        }
    }

    /**
     * 广播消息给游戏的所有玩家
     */
    public void broadcastToGame(Long gameId, WebSocketMessage message) {
        Set<String> subscribers = gameSubscriptions.get(gameId);
        if (subscribers == null || subscribers.isEmpty()) {
            log.warn("【WebSocket广播】游戏 {} 没有订阅者", gameId);
            return;
        }

        log.info("【WebSocket广播】广播消息到游戏 {}: {}, 订阅者数量: {}", gameId, message.getType(), subscribers.size());
        
        for (String userId : subscribers) {
            sendMessageToUser(userId, message);
        }
    }

    /**
     * 发送消息到 session
     */
    private void sendMessage(WebSocketSession session, WebSocketMessage message) throws IOException {
        String payload = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(payload));
    }

    /**
     * 发送连接成功消息
     */
    private void sendConnectedMessage(WebSocketSession session, String userId) throws IOException {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("connectionId", session.getId());
        data.put("serverTime", System.currentTimeMillis());
        data.put("message", "连接成功");
        
        WebSocketMessage message = WebSocketMessage.success(WebSocketMessage.MessageType.CONNECTED, data);
        sendMessage(session, message);
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(WebSocketSession session, String error) throws IOException {
        WebSocketMessage message = WebSocketMessage.error(error);
        sendMessage(session, message);
    }

    private String getUserIdFromSession(WebSocketSession session) {
        // 1. 从 session attributes 获取
        Object userId = session.getAttributes().get("userId");
        if (userId != null) {
            return userId.toString();
        }

        // 2. 从 URL 查询参数获取
        if (session.getUri() != null) {
            String query = session.getUri().getQuery();
            if (query != null) {
                Map<String, String> params = parseQueryString(query);

                // 优先使用 userId 参数（登录用户）
                if (params.containsKey("userId")) {
                    return params.get("userId");
                }

                // 其次使用 tempUserId（游客用户）
                if (params.containsKey("tempUserId")) {
                    return params.get("tempUserId");
                }

                // 最后从 token 解析用户 ID
                if (params.containsKey("token")) {
                    String token = params.get("token");
                    String extractedUserId = extractUserIdFromToken(token);
                    if (extractedUserId != null) {
                        return extractedUserId;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 从 JWT token 中提取用户 ID
     */
    private String extractUserIdFromToken(String token) {
        try {
            // JWT token 格式: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            // 解析 payload (Base64Url 编码)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

            // 优先使用 sub 字段，其次使用 userId 字段
            Object userId = claims.get("sub");
            if (userId == null) {
                userId = claims.get("userId");
            }

            if (userId != null) {
                return userId.toString();
            }
        } catch (Exception e) {
            log.warn("从 token 解析用户 ID 失败: {}", e.getMessage());
        }
        return null;
    }

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
                    log.warn("解析 URL 参数失败: {}", pair);
                }
            }
        }
        return params;
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
