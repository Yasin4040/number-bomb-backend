package com.numberbomb.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private UserVO user;
    
    @Data
    public static class UserVO {
        private Long id;
        private String nickname;
        private String avatarUrl;
        private Integer rankLevel;
        private Integer rankScore;
        private Integer totalGames;
        private Integer winGames;
    }
}
