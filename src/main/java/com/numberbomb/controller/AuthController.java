package com.numberbomb.controller;

import com.numberbomb.dto.LoginRequest;
import com.numberbomb.dto.LoginResponse;
import com.numberbomb.dto.RegisterRequest;
import com.numberbomb.dto.UsernameLoginRequest;
import com.numberbomb.service.AuthService;
import com.numberbomb.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    /**
     * 用户名密码登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody UsernameLoginRequest request) {
        return Result.success(authService.login(request));
    }
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }
    
    /**
     * 微信登录
     */
    @PostMapping("/wx-login")
    public Result<LoginResponse> wxLogin(@RequestBody LoginRequest request) {
        return Result.success(authService.wxLogin(request));
    }
    
    @PostMapping("/refresh")
    public Result<String> refreshToken(@RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request.getRefreshToken()));
    }
    
    /**
     * 模拟登录（用于开发测试）
     */
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
