package com.numberbomb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String openid;
    
    private String unionid;
    
    private String nickname;
    
    private String avatarUrl;
    
    private Integer totalGames;
    
    private Integer winGames;
    
    private Integer rankScore;
    
    private Integer rankLevel;
    
    private Integer maxStreak;
    
    private Integer currentStreak;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    private LocalDateTime lastLogin;
    
    @TableLogic
    private Integer deleted;
}
