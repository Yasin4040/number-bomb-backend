package com.numberbomb.controller;

import com.numberbomb.service.RoomService;
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
    private final RoomService roomService;
    
    @GetMapping("/info")
    public Result<?> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        // 如果是游客用户，先获取或创建对应的用户记录
        if (userId == null && tempUserId != null && !tempUserId.isEmpty()) {
            try {
                userId = roomService.getOrCreateTempUser(tempUserId, request);
            } catch (Exception e) {
                System.out.println("❌ [getUserInfo] 获取游客用户信息失败: " + e.getMessage());
                return Result.error(401, "获取用户信息失败");
            }
        }
        
        if (userId == null) {
            return Result.error(401, "未授权");
        }
        
        return Result.success(userService.getUserInfo(userId));
    }
    
    @GetMapping("/records")
    public Result<?> getUserRecords(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        // 如果是游客用户，先获取或创建对应的用户记录
        if (userId == null && tempUserId != null && !tempUserId.isEmpty()) {
            try {
                userId = roomService.getOrCreateTempUser(tempUserId, request);
                System.out.println("✅ [getUserRecords] 游客用户转换: tempUserId=" + tempUserId + " -> userId=" + userId);
            } catch (Exception e) {
                System.out.println("❌ [getUserRecords] 游客用户转换失败: " + e.getMessage());
                return Result.error(401, "获取用户记录失败");
            }
        }
        
        if (userId == null) {
            return Result.error(401, "未授权");
        }
        
        return Result.success(userService.getUserRecords(userId, page, size));
    }
    
    @PostMapping("/update-nickname")
    public Result<?> updateNickname(@RequestBody UpdateNicknameDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        // 如果是临时用户，先获取或创建用户
        if (userId == null && tempUserId != null && !tempUserId.isEmpty()) {
            try {
                userId = roomService.getOrCreateTempUser(tempUserId, request);
            } catch (Exception e) {
                return Result.error(400, "临时用户信息获取失败");
            }
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
