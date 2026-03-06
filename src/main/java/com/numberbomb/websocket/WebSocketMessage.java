package com.numberbomb.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * WebSocket消息格式
 * 与前端消息类型保持一致
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
     * 消息唯一ID，用于确认和去重
     */
    private String messageId;
    
    /**
     * 关联的游戏ID
     */
    private Long gameId;
    
    /**
     * 关联的房间ID
     */
    private Long roomId;
    
    /**
     * 发送者ID
     */
    private String userId;
    
    /**
     * 消息类型枚举
     * 与前端 WebSocketMessageType 保持一致
     */
    public enum MessageType {
        // 连接相关
        CONNECT,                  // 连接/订阅游戏或房间
        CONNECTED,                // 连接成功
        DISCONNECT,               // 断开连接/取消订阅
        ERROR,                    // 错误消息

        // 心跳
        PING,                     // 心跳请求
        PONG,                     // 心跳响应

        // 游戏相关
        GAME_STATE_SYNC,          // 游戏状态同步（完整状态）
        GAME_TURN_CHANGED,        // 回合切换
        GAME_GUESS_RESULT,        // 猜测结果
        GAME_ENDED,               // 游戏结束
        GAME_UPDATED,             // 游戏状态更新
        GAME_PLAYER_OFFLINE,      // 玩家离线
        GAME_PLAYER_RECONNECTED,  // 玩家重连
        GAME_PLAYER_GIVEUP,       // 玩家放弃游戏
        
        // 再来一局相关
        GAME_RESTART_REQUESTED,   // 请求再来一局
        GAME_RESTART_ACCEPTED,    // 同意再来一局
        GAME_RESTART_REJECTED,    // 拒绝再来一局
        GAME_RESTART_READY,       // 再来一局准备就绪

        // 房间相关
        ROOM_JOINED,              // 玩家加入房间
        ROOM_LEFT,                // 玩家离开房间
        ROOM_PLAYER_READY,        // 玩家准备状态变化
        ROOM_SECRET_SET,          // 玩家设置数字
        ROOM_TURN_ORDER_SET,      // 玩家选择先手/后手
        ROOM_START_COUNTDOWN,     // 开始倒计时
        ROOM_GAME_STARTED,        // 游戏开始
        ROOM_UPDATED,             // 房间信息更新
        
        // 语音对战相关（复用普通对战逻辑）
        VOICE_PLAYER_READY,       // 语音对战玩家准备（触发倒计时）
        VOICE_GUESS_MADE,         // 语音对战猜测提交
        VOICE_GAME_OVER,          // 语音对战游戏结束
        VOICE_OPPONENT_READY,

        // 匹配相关
        MATCH_SUCCESS,            // 匹配成功
        MATCH_STATUS_UPDATED,     // 匹配状态更新
        MATCH_CANCELLED,          // 匹配取消

        // 玩家操作（客户端发送）
        PLAYER_MAKE_GUESS,        // 玩家提交猜测
        PLAYER_GIVE_UP,           // 玩家放弃游戏
        
        // 系统消息
        NOTIFICATION,             // 通知消息
        MESSAGE_ACK               // 消息确认
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
