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
 * 语音对战猜测记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("voice_guess_record")
public class VoiceGuessRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 房间ID
     */
    private Long roomId;
    
    /**
     * 猜测者ID
     */
    private String userId;
    
    /**
     * 轮次
     */
    private Integer round;
    
    /**
     * 猜测的数字
     */
    private String guessNumber;
    
    /**
     * 结果（几个A）
     */
    private Integer resultA;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}