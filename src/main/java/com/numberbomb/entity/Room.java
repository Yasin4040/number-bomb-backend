package com.numberbomb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房间实体（猜数字模式）
 */
@Data
@TableName("rooms")
public class Room {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String roomCode;
    
    private String name;
    
    private Long ownerId;
    
    private Integer punishmentType;
    
    private String punishmentContent;
    
    private Integer status; // 0等待中 1游戏中 2已结束
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    private LocalDateTime expiredAt;
    
    private Long winnerId;
    
    @TableLogic
    private Integer deleted;
    
    /**
     * 房间类型：1-普通对战，2-语音对战
     */
    private Integer roomType;
    
    // 以下字段已废弃（数字炸弹模式，猜数字游戏不再使用，但保留以兼容数据库）
    // private Integer minRange;
    // private Integer maxRange;
    // private Integer bombNumber;
}
