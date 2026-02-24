package com.numberbomb.controller;

import com.numberbomb.service.UserService;
import com.numberbomb.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    @GetMapping("/info")
    public Result<?> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(userService.getUserInfo(userId));
    }
    
    @GetMapping("/records")
    public Result<?> getUserRecords(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(userService.getUserRecords(userId, page, size));
    }
    
    @PostMapping("/update-nickname")
    public Result<?> updateNickname(@RequestBody UpdateNicknameDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        // 如果是临时用户，需要先获取或创建用户
        if (userId == null && tempUserId != null && !tempUserId.isEmpty()) {
            // 这里需要注入 RoomService，但为了避免循环依赖，我们直接在 UserService 中处理
            // 或者通过 RoomService 来处理
            return Result.error(400, "临时用户需要通过房间服务更新昵称");
        }
        
        if (userId == null) {
            return Result.error(401, "未授权");
        }
        
        userService.updateNickname(userId, dto.getNickname());
        return Result.success();
    }
    
    @lombok.Data
    public static class UpdateNicknameDTO {
        private String nickname;
    }
}
