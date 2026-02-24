package com.numberbomb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏记录实体（猜数字模式）
 * 支持本地游戏、在线房间、随机匹配
 */
@Data
@TableName("game_records")
public class GameRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long roomId; // 房间ID（在线房间模式）
    
    private Integer gameType; // 1本地 2在线房间 3随机匹配
    
    private Long player1Id; // 玩家1 ID
    
    private Long player2Id; // 玩家2 ID（双人模式）
    
    private String player1Secret; // 玩家1的4位数字（如 "7392"）
    
    private String player2Secret; // 玩家2的4位数字（如 "2845"）
    
    private Long winnerId; // 获胜者ID（猜中对方数字的玩家）
    
    private Long loserId; // 失败者ID
    
    private String punishmentContent; // 惩罚内容
    
    private LocalDateTime startedAt; // 游戏开始时间
    
    private LocalDateTime endedAt; // 游戏结束时间
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt; // 创建时间
    
    @TableLogic
    private Integer deleted; // 逻辑删除标记
    
    // 以下字段已废弃（数字炸弹模式，不再使用）
    // private Integer bombNumber;
    // private Integer minRange;
    // private Integer maxRange;
}