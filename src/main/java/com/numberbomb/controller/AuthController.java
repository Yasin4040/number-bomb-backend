package com.numberbomb.controller;

import com.numberbomb.dto.LoginRequest;
import com.numberbomb.dto.LoginResponse;
import com.numberbomb.service.AuthService;
import com.numberbomb.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(authService.wxLogin(request));
    }
    
    @PostMapping("/refresh")
    public Result<String> refreshToken(@RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request.getRefreshToken()));
    }
    
    @PostMapping("/mock-login")
    public Result<LoginResponse> mockLogin(@RequestBody MockLoginRequest request) {
        return Result.success(authService.mockLogin(request.getNickname(), request.getAvatarUrl()));
    }
    
    @lombok.Data
    public static class MockLoginRequest {
        private String nickname;
        private String avatarUrl;
    }
    
    @lombok.Data
    public static class RefreshTokenRequest {
        private String refreshToken;
    }
}
