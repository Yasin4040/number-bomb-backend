package com.numberbomb.controller;

import com.numberbomb.vo.Result;
import com.numberbomb.entity.VoiceRoom;
import com.numberbomb.mapper.VoiceRoomMapper;
import com.numberbomb.service.TRTCService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 语音房间控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/voice-room")
public class VoiceRoomController {
    
    @Autowired
    private VoiceRoomMapper voiceRoomMapper;
    
    @Autowired
    private TRTCService trtcService;
    
    /**
     * 创建房间（支持普通对战和语音对战）
     * roomType: 1-普通对战，2-语音对战
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> createRoom(@RequestBody Map<String, Object> params) {
        String userId = (String) params.get("userId");
        String userNumber = (String) params.get("userNumber");
        Integer roomType = (Integer) params.getOrDefault("roomType", 1);
        
        if (userId == null || userNumber == null) {
            return Result.error("参数错误");
        }
        
        // 生成6位房间号
        String roomNumber = generateRoomNumber();
        
        // 创建房间
        VoiceRoom room = VoiceRoom.builder()
                .roomNumber(roomNumber)
                .creatorId(userId)
                .creatorNumber(userNumber)
                .status("WAITING")
                .roomType(roomType)
                .createdAt(LocalDateTime.now())
                .build();
        
        voiceRoomMapper.insert(room);
        
        Map<String, Object> result = new HashMap<>();
        result.put("roomNumber", roomNumber);
        result.put("roomId", room.getId());
        result.put("roomType", roomType);
        
        return Result.success(result);
    }
    
    /**
     * 加入语音房间
     */
    @PostMapping("/join")
    public Result<Map<String, Object>> joinRoom(@RequestBody Map<String, String> params) {
        String roomNumber = params.get("roomNumber");
        String userId = params.get("userId");
        
        if (roomNumber == null || userId == null) {
            return Result.error("参数错误");
        }
        
        VoiceRoom room = voiceRoomMapper.findByRoomNumber(roomNumber);
        if (room == null) {
            return Result.error("房间不存在");
        }
        
        if ("ENDED".equals(room.getStatus())) {
            return Result.error("房间已结束");
        }
        
        if (room.getOpponentId() != null && !room.getOpponentId().equals(userId)) {
            return Result.error("房间已满");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("roomId", room.getId());
        result.put("roomNumber", room.getRoomNumber());
        result.put("status", room.getStatus());
        result.put("creatorId", room.getCreatorId());
        result.put("roomType", room.getRoomType());
        
        return Result.success(result);
    }
    
    /**
     * 设置数字（加入者）
     */
    @PostMapping("/set-number")
    public Result<Void> setNumber(@RequestBody Map<String, String> params) {
        String roomNumber = params.get("roomNumber");
        String userId = params.get("userId");
        String userNumber = params.get("userNumber");
        
        if (roomNumber == null || userId == null || userNumber == null) {
            return Result.error("参数错误");
        }
        
        VoiceRoom room = voiceRoomMapper.findByRoomNumber(roomNumber);
        if (room == null) {
            return Result.error("房间不存在");
        }
        
        // 更新对手数字
        if (userId.equals(room.getOpponentId())) {
            room.setOpponentNumber(userNumber);
            voiceRoomMapper.updateById(room);
        }
        
        return Result.success();
    }
    
    /**
     * 获取TRTC用户签名
     */
    @GetMapping("/trtc-signature")
    public Result<Map<String, Object>> getTRTCSignature(
            @RequestParam String userId,
            @RequestParam String roomNumber) {
        
        try {
            String userSig = trtcService.generateUserSig(userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("sdkAppId", trtcService.getSdkAppId());
            result.put("userSig", userSig);
            result.put("roomId", Integer.parseInt(roomNumber));
            result.put("userId", userId);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("生成TRTC签名失败", e);
            return Result.error("生成签名失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取房间信息
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getRoomInfo(@RequestParam String roomNumber) {
        VoiceRoom room = voiceRoomMapper.findByRoomNumber(roomNumber);
        if (room == null) {
            return Result.error("房间不存在");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("roomNumber", room.getRoomNumber());
        result.put("status", room.getStatus());
        result.put("creatorId", room.getCreatorId());
        result.put("opponentId", room.getOpponentId());
        result.put("roomType", room.getRoomType());
        
        return Result.success(result);
    }
    
    /**
     * 生成6位房间号
     */
    private String generateRoomNumber() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }
}