package com.numberbomb.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * WebSocket消息格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage implements Serializable {
    
    /**
     * 消息类型
     */
    private MessageType type;
    
    /**
     * 消息数据
     */
    private Map<String, Object> data;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 错误信息（如果type为ERROR）
     */
    private String error;
    
    /**
     * 消息类型枚举
     */
    public enum MessageType {
        // 房间相关
        ROOM_JOINED,              // 玩家加入房间
        ROOM_LEFT,                // 玩家离开房间
        ROOM_PLAYER_READY,        // 玩家准备状态变化
        ROOM_SECRET_SET,          // 玩家设置数字
        ROOM_TURN_ORDER_SET,      // 玩家选择先手/后手
        ROOM_START_COUNTDOWN,     // 开始倒计时
        ROOM_GAME_STARTED,        // 游戏开始
        ROOM_UPDATED,             // 房间信息更新
        
        // 游戏相关
        GAME_TURN_CHANGED,        // 回合切换
        GAME_GUESS_RESULT,        // 猜测结果
        GAME_ENDED,               // 游戏结束
        GAME_UPDATED,             // 游戏状态更新
        
        // 系统消息
        PING,                     // 心跳
        PONG,                     // 心跳响应
        ERROR,                    // 错误消息
        NOTIFICATION              // 通知消息
    }
    
    /**
     * 创建成功消息
     */
    public static WebSocketMessage success(MessageType type, Map<String, Object> data) {
        return WebSocketMessage.builder()
                .type(type)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建错误消息
     */
    public static WebSocketMessage error(String errorMessage) {
        return WebSocketMessage.builder()
                .type(MessageType.ERROR)
                .error(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建心跳消息
     */
    public static WebSocketMessage ping() {
        return WebSocketMessage.builder()
                .type(MessageType.PING)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建心跳响应
     */
    public static WebSocketMessage pong() {
        return WebSocketMessage.builder()
                .type(MessageType.PONG)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
