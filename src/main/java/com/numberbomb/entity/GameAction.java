package com.numberbomb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏操作记录实体（猜数字模式）
 * 记录每次猜测和AB结果
 */
@Data
@TableName("game_actions")
public class GameAction {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long gameId; // 游戏ID
    
    private Integer roundNumber; // 回合数（第几轮）
    
    private Long playerId; // 玩家ID（谁猜的）
    
    private String guess; // 猜测的4位数字（如 "7392"）
    
    private Integer resultA; // A值（数字和位置都对的数量）
    
    private Integer resultB; // B值（数字对但位置错的数量）
    
    private Boolean isWin; // 是否获胜（4A0B）
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt; // 创建时间
    
    // 以下字段已废弃（数字炸弹模式，不再使用）
    // private Integer guessNumber; // 猜测的数字（整数）
    // private Integer result; // 1太小 2太大 3命中
    // private Integer currentMin; // 当前最小范围
    // private Integer currentMax; // 当前最大范围
}