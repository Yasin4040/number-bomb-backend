package com.numberbomb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 语音房间实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("voice_room")
public class VoiceRoom {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 房间号（6位数字）
     */
    private String roomNumber;
    
    /**
     * 创建者ID
     */
    private String creatorId;
    
    /**
     * 创建者数字
     */
    private String creatorNumber;
    
    /**
     * 对手ID
     */
    private String opponentId;
    
    /**
     * 对手数字
     */
    private String opponentNumber;
    
    /**
     * 房间状态：WAITING-等待中，PLAYING-游戏中，ENDED-已结束
     */
    private String status;
    
    /**
     * 获胜者ID
     */
    private String winnerId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 开始时间
     */
    private LocalDateTime startedAt;
    
    /**
     * 结束时间
     */
    private LocalDateTime endedAt;
    
    /**
     * 游戏时长（秒）
     */
    private Integer duration;
    
    /**
     * 房间类型：1-普通对战，2-语音对战
     */
    private Integer roomType;
}