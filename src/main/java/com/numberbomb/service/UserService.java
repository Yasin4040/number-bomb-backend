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
        System.out.println("🔍 [getUserRecords] 查询用户记录: userId=" + userId + ", page=" + page + ", size=" + size);
        
        Page<GameRecord> pageParam = new Page<>(page, size);
        
        LambdaQueryWrapper<GameRecord> wrapper = new LambdaQueryWrapper<>();
        // 查询该用户参与的游戏
        wrapper.and(w -> w.eq(GameRecord::getPlayer1Id, userId)
                .or()
                .eq(GameRecord::getPlayer2Id, userId));
        // 只查询已结束的游戏
        wrapper.isNotNull(GameRecord::getEndedAt);
        // 按创建时间倒序
        wrapper.orderByDesc(GameRecord::getCreatedAt);
        
        Page<GameRecord> recordsPage = gameRecordMapper.selectPage(pageParam, wrapper);
        
        System.out.println("📊 [getUserRecords] 查询结果: total=" + recordsPage.getTotal() + ", records=" + recordsPage.getRecords().size());
        
        List<Map<String, Object>> list = recordsPage.getRecords().stream().map(record -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", record.getId());
            item.put("gameType", record.getGameType());
            item.put("roomId", record.getRoomId());
            
            // 获取对手信息
            Long opponentId = record.getPlayer1Id().equals(userId) 
                ? record.getPlayer2Id() 
                : record.getPlayer1Id();
            
            System.out.println("🎮 [getUserRecords] 处理记录: recordId=" + record.getId() + ", opponentId=" + opponentId);
            
            if (opponentId != null) {
                User opponent = userMapper.selectById(opponentId);
                if (opponent != null) {
                    Map<String, Object> opponentInfo = new HashMap<>();
                    opponentInfo.put("id", opponent.getId());
                    opponentInfo.put("nickname", opponent.getNickname());
                    opponentInfo.put("avatarUrl", opponent.getAvatarUrl());
                    item.put("opponent", opponentInfo);
                    System.out.println("  👤 对手信息: " + opponent.getNickname());
                } else {
                    System.out.println("  ⚠️ 对手用户不存在: " + opponentId);
                    Map<String, Object> opponentInfo = new HashMap<>();
                    opponentInfo.put("id", opponentId);
                    opponentInfo.put("nickname", "未知对手");
                    opponentInfo.put("avatarUrl", null);
                    item.put("opponent", opponentInfo);
                }
            }
            
            // 判断胜负
            boolean isWinner = record.getWinnerId() != null && record.getWinnerId().equals(userId);
            item.put("result", isWinner ? "win" : "lose");
            System.out.println("  🏆 结果: " + (isWinner ? "胜利" : "失败") + ", winnerId=" + record.getWinnerId() + ", userId=" + userId);
            
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
                item.put("mySecret", record.getPlayer1Id().equals(userId) ? record.getPlayer1Secret() : record.getPlayer2Secret());
                item.put("opponentSecret", record.getPlayer1Id().equals(userId) ? record.getPlayer2Secret() : record.getPlayer1Secret());
            }
            
            item.put("createdAt", record.getCreatedAt());
            item.put("endedAt", record.getEndedAt());
            
            return item;
        }).collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        // 使用 list 的大小作为 total（因为可能有过滤条件）
        result.put("total", list.size());
        result.put("list", list);
        result.put("page", page);
        result.put("size", size);
        
        System.out.println("✅ [getUserRecords] 返回结果: total=" + list.size() + ", listSize=" + list.size());
        
        return result;
    }
    
    /**
     * 更新用户昵称
     */
    public void updateNickname(Long userId, String nickname) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查昵称是否与其他用户冲突
        LambdaQueryWrapper<User> nicknameWrapper = new LambdaQueryWrapper<>();
        nicknameWrapper.eq(User::getNickname, nickname)
                      .ne(User::getId, userId);
        User existingNickname = userMapper.selectOne(nicknameWrapper);
        
        if (existingNickname != null) {
            throw new RuntimeException("昵称已被使用");
        }
        
        // 更新昵称
        user.setNickname(nickname);
        userMapper.updateById(user);
        System.out.println("✅ [updateNickname] 更新用户昵称: userId=" + userId + ", nickname=" + nickname);
    }
}
