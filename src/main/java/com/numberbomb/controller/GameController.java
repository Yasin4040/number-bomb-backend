package com.numberbomb.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.numberbomb.entity.RoomPlayer;
import com.numberbomb.mapper.RoomPlayerMapper;
import com.numberbomb.service.GameService;
import com.numberbomb.service.RoomService;
import com.numberbomb.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {
    
    private final GameService gameService;
    private final RoomService roomService;
    private final RoomPlayerMapper roomPlayerMapper;
    
    /**
     * 从request中获取userId（支持临时用户）
     */
    private Long getUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        
        // 如果是临时用户，通过tempUserId获取对应的userId
        if (userId == null) {
            String tempUserId = (String) request.getAttribute("tempUserId");
            if (tempUserId != null && !tempUserId.isEmpty()) {
                // 通过临时用户ID获取或创建用户
                userId = roomService.getOrCreateTempUser(tempUserId, request);
                System.out.println("临时用户转换为userId: " + tempUserId + " -> " + userId);
            }
        }
        
        return userId;
    }
    
    @PostMapping("/start")
    public Result<?> startGame(@RequestBody StartGameDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return Result.error(401, "未授权，请检查临时用户ID");
        }
        return Result.success(gameService.startGame(dto.getRoomId(), userId));
    }
    
    @PostMapping("/guess")
    public Result<?> makeGuess(@RequestBody GuessDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return Result.error(401, "未授权，请检查临时用户ID");
        }
        return Result.success(gameService.makeGuess(dto.getGameId(), userId, dto.getGuess()));
    }
    
    @GetMapping("/status")
    public Result<?> getGameStatus(@RequestParam Long gameId, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return Result.error(401, "未授权，请检查临时用户ID");
        }
        return Result.success(gameService.getGameStatus(gameId, userId));
    }
    
    @PostMapping("/end")
    public Result<?> endGame(@RequestBody EndGameDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return Result.error(401, "未授权，请检查临时用户ID");
        }
        return Result.success(gameService.endGame(dto.getGameId(), dto.getLoserId(), userId));
    }
    
    @Data
    public static class StartGameDTO {
        private Long roomId;
    }
    
    @Data
    public static class GuessDTO {
        private Long gameId;
        private String guess; // 4位数字字符串，如 "7392"
    }
    
    @Data
    public static class EndGameDTO {
        private Long gameId;
        private Long loserId;
    }
}
