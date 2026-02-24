package com.numberbomb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.numberbomb.entity.GameRecord;
import com.numberbomb.entity.User;
import com.numberbomb.mapper.GameRecordMapper;
import com.numberbomb.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserMapper userMapper;
    private final GameRecordMapper gameRecordMapper;
    
    public Map<String, Object> getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("nickname", user.getNickname());
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("rankLevel", user.getRankLevel());
        result.put("rankScore", user.getRankScore());
        result.put("totalGames", user.getTotalGames());
        result.put("winGames", user.getWinGames());
        result.put("winRate", user.getTotalGames() > 0 
            ? String.format("%.0f%%", (user.getWinGames() * 100.0 / user.getTotalGames()))
            : "0%");
        result.put("maxStreak", user.getMaxStreak());
        result.put("currentStreak", user.getCurrentStreak());
        
        return result;
    }
    
    public Map<String, Object> getUserRecords(Long userId, Integer page, Integer size) {
        Page<GameRecord> pageParam = new Page<>(page, size);
        
        LambdaQueryWrapper<GameRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(GameRecord::getPlayer1Id, userId)
                .or()
                .eq(GameRecord::getPlayer2Id, userId));
        wrapper.orderByDesc(GameRecord::getCreatedAt);
        
        Page<GameRecord> recordsPage = gameRecordMapper.selectPage(pageParam, wrapper);
        
        List<Map<String, Object>> list = recordsPage.getRecords().stream().map(record -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", record.getId());
            
            // 获取对手信息
            Long opponentId = record.getPlayer1Id().equals(userId) 
                ? record.getPlayer2Id() 
                : record.getPlayer1Id();
            
            if (opponentId != null) {
                User opponent = userMapper.selectById(opponentId);
                if (opponent != null) {
                    Map<String, Object> opponentInfo = new HashMap<>();
                    opponentInfo.put("id", opponent.getId());
                    opponentInfo.put("nickname", opponent.getNickname());
                    opponentInfo.put("avatarUrl", opponent.getAvatarUrl());
                    item.put("opponent", opponentInfo);
                }
            }
            
            boolean isWinner = record.getWinnerId() != null && record.getWinnerId().equals(userId);
            item.put("result", isWinner ? "win" : "lose");
            
            // 猜数字模式：返回获胜者和失败者的数字
            if (record.getPlayer1Secret() != null && record.getPlayer2Secret() != null) {
                // 确定获胜者和失败者的数字
                String winnerSecret = record.getWinnerId() != null && record.getWinnerId().equals(record.getPlayer1Id())
                    ? record.getPlayer1Secret()
                    : record.getPlayer2Secret();
                String loserSecret = record.getLoserId() != null && record.getLoserId().equals(record.getPlayer1Id())
                    ? record.getPlayer1Secret()
                    : record.getPlayer2Secret();
                
                item.put("winnerSecret", winnerSecret);
                item.put("loserSecret", loserSecret);
            }
            
            item.put("createdAt", record.getCreatedAt());
            
            return item;
        }).collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("total", recordsPage.getTotal());
        result.put("list", list);
        result.put("page", page);
        result.put("size", size);
        
        return result;
    }
}
