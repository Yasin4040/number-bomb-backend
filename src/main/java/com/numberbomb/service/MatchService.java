package com.numberbomb.service;

import com.numberbomb.entity.User;
import com.numberbomb.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MatchService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;
    
    public Map<String, Object> startMatch(Long userId, String mode, Integer rankLevel) {
        String matchId = "match_" + userId + "_" + System.currentTimeMillis();
        
        // 加入匹配队列
        String queueKey = "match:queue:" + mode + ":" + (rankLevel != null ? rankLevel : "all");
        redisTemplate.opsForList().rightPush(queueKey, userId);
        redisTemplate.expire(queueKey, 60, TimeUnit.SECONDS);
        
        // 保存匹配信息
        Map<String, Object> matchInfo = new HashMap<>();
        matchInfo.put("matchId", matchId);
        matchInfo.put("userId", userId);
        matchInfo.put("mode", mode);
        matchInfo.put("rankLevel", rankLevel);
        matchInfo.put("status", "waiting");
        matchInfo.put("startTime", System.currentTimeMillis());
        
        String matchKey = "match:" + matchId;
        redisTemplate.opsForValue().set(matchKey, matchInfo, 60, TimeUnit.SECONDS);
        
        // 尝试匹配
        tryMatch(mode, rankLevel);
        
        Map<String, Object> result = new HashMap<>();
        result.put("matchId", matchId);
        result.put("estimatedTime", 15);
        result.put("onlineCount", getOnlineCount());
        
        return result;
    }
    
    public void cancelMatch(Long userId, String matchId) {
        String matchKey = "match:" + matchId;
        Map<String, Object> matchInfo = (Map<String, Object>) redisTemplate.opsForValue().get(matchKey);
        
        if (matchInfo != null && userId.equals(matchInfo.get("userId"))) {
            // 从队列中移除
            String mode = (String) matchInfo.get("mode");
            Integer rankLevel = (Integer) matchInfo.get("rankLevel");
            String queueKey = "match:queue:" + mode + ":" + (rankLevel != null ? rankLevel : "all");
            redisTemplate.opsForList().remove(queueKey, 1, userId);
            
            // 删除匹配信息
            redisTemplate.delete(matchKey);
        }
    }
    
    public Map<String, Object> getMatchStatus(Long userId, String matchId) {
        String matchKey = "match:" + matchId;
        Map<String, Object> matchInfo = (Map<String, Object>) redisTemplate.opsForValue().get(matchKey);
        
        if (matchInfo == null) {
            throw new RuntimeException("匹配不存在或已过期");
        }
        
        String status = (String) matchInfo.get("status");
        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        
        if ("matched".equals(status)) {
            Long opponentId = (Long) matchInfo.get("opponentId");
            if (opponentId != null) {
                User opponent = userMapper.selectById(opponentId);
                if (opponent != null) {
                    Map<String, Object> opponentInfo = new HashMap<>();
                    opponentInfo.put("id", opponent.getId());
                    opponentInfo.put("nickname", opponent.getNickname());
                    opponentInfo.put("avatarUrl", opponent.getAvatarUrl());
                    opponentInfo.put("rankLevel", opponent.getRankLevel());
                    opponentInfo.put("winRate", opponent.getTotalGames() > 0 
                        ? String.format("%.0f%%", (opponent.getWinGames() * 100.0 / opponent.getTotalGames()))
                        : "0%");
                    result.put("opponent", opponentInfo);
                }
            }
            result.put("countdown", matchInfo.get("countdown"));
        } else {
            result.put("estimatedTime", 15);
            result.put("onlineCount", getOnlineCount());
        }
        
        return result;
    }
    
    private void tryMatch(String mode, Integer rankLevel) {
        String queueKey = "match:queue:" + mode + ":" + (rankLevel != null ? rankLevel : "all");
        List<Object> queue = redisTemplate.opsForList().range(queueKey, 0, -1);
        
        if (queue != null && queue.size() >= 2) {
            Long player1 = (Long) queue.get(0);
            Long player2 = (Long) queue.get(1);
            
            // 从队列中移除
            redisTemplate.opsForList().leftPop(queueKey);
            redisTemplate.opsForList().leftPop(queueKey);
            
            // 创建匹配成功信息
            String matchId1 = "match_" + player1 + "_" + System.currentTimeMillis();
            String matchId2 = "match_" + player2 + "_" + System.currentTimeMillis();
            
            Map<String, Object> matchInfo1 = new HashMap<>();
            matchInfo1.put("matchId", matchId1);
            matchInfo1.put("userId", player1);
            matchInfo1.put("opponentId", player2);
            matchInfo1.put("status", "matched");
            matchInfo1.put("countdown", 3);
            
            Map<String, Object> matchInfo2 = new HashMap<>();
            matchInfo2.put("matchId", matchId2);
            matchInfo2.put("userId", player2);
            matchInfo2.put("opponentId", player1);
            matchInfo2.put("status", "matched");
            matchInfo2.put("countdown", 3);
            
            redisTemplate.opsForValue().set("match:" + matchId1, matchInfo1, 300, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set("match:" + matchId2, matchInfo2, 300, TimeUnit.SECONDS);
        }
    }
    
    private int getOnlineCount() {
        // 简单实现：返回随机数（实际应该统计在线用户数）
        return new Random().nextInt(2000) + 1000;
    }
}
