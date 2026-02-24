package com.numberbomb.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String code;
    private UserInfoDTO userInfo;
    
    @Data
    public static class UserInfoDTO {
        private String nickName;
        private String avatarUrl;
    }
}
