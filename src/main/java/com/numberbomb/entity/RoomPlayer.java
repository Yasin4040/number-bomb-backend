package com.numberbomb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("room_players")
public class RoomPlayer {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long roomId;
    
    private Long userId;
    
    private Integer isOwner;
    
    private Integer isReady;
    
    private String secretNumber; // 玩家设置的4位数字
    
    private Integer turnOrder; // 先手/后手：1=先手, 2=后手
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinedAt;
    
    @TableLogic
    private Integer deleted;
}
