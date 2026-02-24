package com.numberbomb.dto;

import lombok.Data;

@Data
public class UsernameLoginRequest {
    private String username;
    private String password;
}
