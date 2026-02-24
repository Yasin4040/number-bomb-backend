package com.numberbomb.controller;

import com.numberbomb.dto.CreateRoomDTO;
import com.numberbomb.service.RoomService;
import com.numberbomb.utils.TempUserUtil;
import com.numberbomb.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/room")
@RequiredArgsConstructor
public class RoomController {
    
    private final RoomService roomService;
    
    @PostMapping("/create")
    public Result<?> createRoom(@RequestBody CreateRoomDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        // 如果是临时用户，创建或获取临时用户
        if (userId == null) {
            if (tempUserId != null && !tempUserId.isEmpty()) {
                try {
                    userId = roomService.getOrCreateTempUser(tempUserId, request);
                } catch (Exception e) {
                    return Result.error(500, "创建临时用户失败: " + e.getMessage());
                }
            } else {
                // 如果没有临时用户ID，返回错误
                return Result.error(401, "未登录且未提供临时用户ID");
            }
        }
        
        if (userId == null) {
            return Result.error(401, "无法获取用户ID");
        }
        
        try {
            Map<String, Object> roomData = roomService.createRoom(userId, dto);
            
            // 验证返回数据是否有效
            if (roomData == null) {
                System.err.println("❌ [RoomController] createRoom 返回 null, userId=" + userId);
                return Result.error(500, "创建房间失败：服务器返回数据为空");
            }
            
            // 验证必要字段是否存在
            if (!roomData.containsKey("roomId") || roomData.get("roomId") == null) {
                System.err.println("❌ [RoomController] createRoom 返回数据缺少 roomId, data=" + roomData);
                return Result.error(500, "创建房间失败：返回数据格式错误，缺少房间ID");
            }
            
            if (!roomData.containsKey("roomCode") || roomData.get("roomCode") == null) {
                System.err.println("❌ [RoomController] createRoom 返回数据缺少 roomCode, data=" + roomData);
                return Result.error(500, "创建房间失败：返回数据格式错误，缺少房间号");
            }
            
            System.out.println("✅ [RoomController] 创建房间成功: roomId=" + roomData.get("roomId") + ", roomCode=" + roomData.get("roomCode"));
            return Result.success(roomData);
        } catch (Exception e) {
            System.err.println("❌ [RoomController] 创建房间异常: " + e.getMessage());
            e.printStackTrace();
            return Result.error(500, "创建房间失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/join")
    public Result<?> joinRoom(@RequestBody JoinRoomDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        // 如果是临时用户，创建或获取临时用户
        if (userId == null && tempUserId != null) {
            userId = roomService.getOrCreateTempUser(tempUserId, request);
        }
        
        return Result.success(roomService.joinRoom(userId, dto.getRoomCode()));
    }
    
    @PostMapping("/ready")
    public Result<?> setReady(@RequestBody ReadyDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        // 如果是临时用户，创建或获取临时用户
        if (userId == null && tempUserId != null) {
            userId = roomService.getOrCreateTempUser(tempUserId, request);
        }
        
        if (userId == null) {
            return Result.error(401, "无法获取用户ID");
        }
        
        roomService.setReady(dto.getRoomId(), userId, dto.getIsReady());
        return Result.success();
    }
    
    @GetMapping("/info")
    public Result<?> getRoomInfo(@RequestParam Long roomId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        // 如果是临时用户，创建或获取临时用户
        if (userId == null && tempUserId != null && !tempUserId.isEmpty()) {
            try {
                userId = roomService.getOrCreateTempUser(tempUserId, request);
            } catch (Exception e) {
                // 如果创建临时用户失败，仍然允许获取房间信息（userId可以为null）
                System.err.println("创建临时用户失败: " + e.getMessage());
            }
        }
        
        // getRoomInfo 允许 userId 为 null（未登录用户也可以查看房间信息）
        return Result.success(roomService.getRoomInfo(roomId, userId));
    }
    
    @PostMapping("/start")
    public Result<?> startGame(@RequestBody StartGameDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        roomService.startGame(dto.getRoomId(), userId);
        return Result.success();
    }
    
    @PostMapping("/set-secret")
    public Result<?> setPlayerSecret(@RequestBody SetSecretDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        if (userId == null && tempUserId != null) {
            userId = roomService.getOrCreateTempUser(tempUserId, request);
        }
        
        if (userId == null) {
            return Result.error(401, "无法获取用户ID");
        }
        
        roomService.setPlayerSecret(dto.getRoomId(), userId, dto.getSecretNumber());
        return Result.success();
    }
    
    @PostMapping("/set-turn-order")
    public Result<?> setTurnOrder(@RequestBody SetTurnOrderDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        if (userId == null && tempUserId != null) {
            userId = roomService.getOrCreateTempUser(tempUserId, request);
        }
        
        if (userId == null) {
            return Result.error(401, "无法获取用户ID");
        }
        
        roomService.setTurnOrder(dto.getRoomId(), userId, dto.getTurnOrder());
        return Result.success();
    }
    
    @PostMapping("/emoji")
    public Result<?> sendEmoji(@RequestBody EmojiDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        if (userId == null && tempUserId != null) {
            userId = roomService.getOrCreateTempUser(tempUserId, request);
        }
        
        if (userId == null) {
            return Result.error(401, "无法获取用户ID");
        }
        
        // 这里可以通过WebSocket发送表情给其他玩家
        // 暂时只返回成功
        return Result.success();
    }
    
    @PostMapping("/update-nickname")
    public Result<?> updateNickname(@RequestBody UpdateNicknameDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String tempUserId = (String) request.getAttribute("tempUserId");
        
        // 如果是临时用户，先获取或创建用户
        if (userId == null && tempUserId != null && !tempUserId.isEmpty()) {
            userId = roomService.getOrCreateTempUser(tempUserId, request);
        }
        
        if (userId == null) {
            return Result.error(401, "无法获取用户ID");
        }
        
        if (dto.getNickname() == null || dto.getNickname().trim().isEmpty()) {
            return Result.error(400, "昵称不能为空");
        }
        
        if (dto.getNickname().length() > 12) {
            return Result.error(400, "昵称不能超过12个字符");
        }
        
        roomService.updateUserNickname(userId, dto.getNickname().trim());
        return Result.success();
    }
    
    @lombok.Data
    public static class JoinRoomDTO {
        private String roomCode;
    }
    
    @lombok.Data
    public static class ReadyDTO {
        private Long roomId;
        private Boolean isReady;
    }
    
    @lombok.Data
    public static class StartGameDTO {
        private Long roomId;
    }
    
    @lombok.Data
    public static class SetSecretDTO {
        private Long roomId;
        private String secretNumber;
    }
    
    @lombok.Data
    public static class SetTurnOrderDTO {
        private Long roomId;
        private Integer turnOrder; // 1=先手, 2=后手
    }
    
    @lombok.Data
    public static class EmojiDTO {
        private Long roomId;
        private String emoji;
    }
    
    @lombok.Data
    public static class UpdateNicknameDTO {
        private String nickname;
    }
}
