package com.numberbomb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.numberbomb.dto.CreateRoomDTO;
import com.numberbomb.entity.Room;
import com.numberbomb.entity.RoomPlayer;
import com.numberbomb.entity.User;
import com.numberbomb.mapper.RoomMapper;
import com.numberbomb.mapper.RoomPlayerMapper;
import com.numberbomb.mapper.UserMapper;
import com.numberbomb.utils.TempUserUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {
    
    private final RoomMapper roomMapper;
    private final RoomPlayerMapper roomPlayerMapper;
    private final UserMapper userMapper;
    private final WebSocketService webSocketService;
    
    @Value("${game.room.expire-minutes:15}")
    private Integer expireMinutes;
    
    /**
     * 获取或创建临时用户
     * 
     * 重要：为了确保每个浏览器session都有独立的用户，我们为每个新的tempUserId创建新用户。
     * 前端已经确保每个session都生成唯一的tempUserId（包含时间戳、随机数、crypto随机值等），
     * 所以这里应该总是创建新用户，而不是查找已存在的用户。
     * 
     * 这样可以避免同一台电脑的不同浏览器session（包括无痕模式）共享同一个用户。
     */
    @Transactional
    public Long getOrCreateTempUser(String tempUserId, HttpServletRequest request) {
        // 由于前端已经确保每个session都有唯一的tempUserId，我们总是创建新用户
        // 这样可以确保：
        // 1. 无痕模式和普通模式有独立的用户
        // 2. 不同浏览器有独立的用户
        // 3. 同一浏览器的不同标签页（如果sessionStorage独立）有独立的用户
        
        // 注意：如果将来需要支持"刷新页面保持同一用户"的功能，可以在这里添加查找逻辑
        // 但当前为了确保多session支持，我们选择总是创建新用户
        
        // 创建新的临时用户
        // 优先使用前端传来的临时昵称，否则生成默认昵称
        String nickname;
        String tempNickname = (String) request.getAttribute("tempNickname");
        if (tempNickname != null && !tempNickname.isEmpty()) {
            nickname = tempNickname;
        } else {
            nickname = TempUserUtil.generateTempNickname(tempUserId);
        }
        
        // 检查昵称是否已存在，如果存在则添加随机后缀
        LambdaQueryWrapper<User> nicknameWrapper = new LambdaQueryWrapper<>();
        nicknameWrapper.eq(User::getNickname, nickname);
        User existingNickname = userMapper.selectOne(nicknameWrapper);
        
        if (existingNickname != null) {
            // 如果昵称已存在，添加随机后缀
            Random random = new Random();
            nickname = nickname + "_" + random.nextInt(1000);
        }
        
        User tempUser = new User();
        tempUser.setOpenid(tempUserId);
        tempUser.setNickname(nickname);
        tempUser.setAvatarUrl(""); // 临时用户没有头像
        tempUser.setTotalGames(0);
        tempUser.setWinGames(0);
        tempUser.setRankScore(0);
        tempUser.setRankLevel(1);
        tempUser.setMaxStreak(0);
        tempUser.setCurrentStreak(0);
        tempUser.setLastLogin(LocalDateTime.now());
        
        userMapper.insert(tempUser);
        System.out.println("创建新的临时用户: " + tempUser.getId() + ", 昵称: " + nickname);
        return tempUser.getId();
    }
    
    @Transactional
    public Map<String, Object> createRoom(Long userId, CreateRoomDTO dto) {
        // 生成6位房间号
        String roomCode = generateRoomCode();
        
        // 创建房间
        Room room = new Room();
        room.setRoomCode(roomCode);
        room.setName(dto.getName());
        room.setOwnerId(userId);
        // 猜数字游戏不需要范围设置（保留字段以兼容数据库，但不使用）
        // room.setMinRange(1);
        // room.setMaxRange(9999);
        room.setPunishmentType(dto.getPunishmentType());
        room.setPunishmentContent(dto.getPunishmentContent());
        room.setStatus(0); // 等待中
        room.setExpiredAt(LocalDateTime.now().plusMinutes(expireMinutes));
        roomMapper.insert(room);
        
        // 添加房主为玩家
        RoomPlayer owner = new RoomPlayer();
        owner.setRoomId(room.getId());
        owner.setUserId(userId);
        owner.setIsOwner(1);
        owner.setIsReady(0);
        roomPlayerMapper.insert(owner);
        
        return convertRoomToMap(room);
    }
    
    @Transactional
    public Map<String, Object> joinRoom(Long userId, String roomCode) {
        // 查找房间
        LambdaQueryWrapper<Room> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Room::getRoomCode, roomCode)
                .eq(Room::getStatus, 0);
        Room room = roomMapper.selectOne(wrapper);
        
        if (room == null) {
            throw new RuntimeException("房间不存在或已开始");
        }
        
        if (room.getExpiredAt() != null && room.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("房间已过期");
        }
        
        // 检查是否已加入
        LambdaQueryWrapper<RoomPlayer> playerWrapper = new LambdaQueryWrapper<>();
        playerWrapper.eq(RoomPlayer::getRoomId, room.getId())
                .eq(RoomPlayer::getUserId, userId);
        RoomPlayer existing = roomPlayerMapper.selectOne(playerWrapper);
        
        if (existing != null) {
            throw new RuntimeException("您已在房间中");
        }
        
        // 检查房间人数
        LambdaQueryWrapper<RoomPlayer> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(RoomPlayer::getRoomId, room.getId());
        long playerCount = roomPlayerMapper.selectCount(countWrapper);
        
        if (playerCount >= 2) {
            throw new RuntimeException("房间已满");
        }
        
        // 加入房间
        RoomPlayer player = new RoomPlayer();
        player.setRoomId(room.getId());
        player.setUserId(userId);
        player.setIsOwner(0);
        player.setIsReady(0);
        roomPlayerMapper.insert(player);
        
        // 获取玩家信息用于推送
        User user = userMapper.selectById(userId);
        Map<String, Object> playerInfo = new HashMap<>();
        if (user != null) {
            playerInfo.put("id", user.getId());
            playerInfo.put("nickname", user.getNickname());
            playerInfo.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
        }
        
        // 通过WebSocket通知房间内其他玩家
        webSocketService.notifyPlayerJoined(room.getId(), playerInfo);
        
        // 返回房间信息和玩家列表
        Map<String, Object> result = new HashMap<>();
        result.put("room", convertRoomToMap(room));
        result.put("players", getRoomPlayers(room.getId()));
        
        return result;
    }
    
    public void setReady(Long roomId, Long userId, Boolean isReady) {
        LambdaQueryWrapper<RoomPlayer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, userId);
        RoomPlayer player = roomPlayerMapper.selectOne(wrapper);
        
        if (player == null) {
            throw new RuntimeException("您不在该房间中");
        }
        
        // 只有设置了数字才能准备
        if (isReady && (player.getSecretNumber() == null || player.getSecretNumber().isEmpty())) {
            throw new RuntimeException("请先设置您的数字");
        }
        
        player.setIsReady(isReady ? 1 : 0);
        roomPlayerMapper.updateById(player);
        
        // 通过WebSocket通知房间内其他玩家
        webSocketService.notifyPlayerReady(roomId, userId, isReady);
        
        // 检查是否全部准备，如果是则自动开始游戏
        if (isReady) {
            checkAndAutoStart(roomId);
        }
    }
    
    public void setPlayerSecret(Long roomId, Long userId, String secretNumber) {
        LambdaQueryWrapper<RoomPlayer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, userId);
        RoomPlayer player = roomPlayerMapper.selectOne(wrapper);
        
        if (player == null) {
            throw new RuntimeException("您不在该房间中");
        }
        
        // 验证数字格式（4位不重复）
        if (secretNumber == null || secretNumber.length() != 4) {
            throw new RuntimeException("请输入4位数字");
        }
        
        if (!secretNumber.matches("^[1-9]\\d{3}$")) {
            throw new RuntimeException("第一位不能是0");
        }
        
        // 检查是否有重复数字
        Set<Character> digits = new HashSet<>();
        for (char c : secretNumber.toCharArray()) {
            if (!digits.add(c)) {
                throw new RuntimeException("数字不能重复");
            }
        }
        
        // 检查是否与其他玩家相同
        LambdaQueryWrapper<RoomPlayer> otherWrapper = new LambdaQueryWrapper<>();
        otherWrapper.eq(RoomPlayer::getRoomId, roomId)
                    .ne(RoomPlayer::getUserId, userId)
                    .eq(RoomPlayer::getSecretNumber, secretNumber);
        RoomPlayer otherPlayer = roomPlayerMapper.selectOne(otherWrapper);
        if (otherPlayer != null) {
            throw new RuntimeException("不能与对方数字相同");
        }
        
        player.setSecretNumber(secretNumber);
        roomPlayerMapper.updateById(player);
        
        // 通过WebSocket通知房间内其他玩家
        webSocketService.notifySecretSet(roomId, userId);
    }
    
    public void setTurnOrder(Long roomId, Long userId, Integer turnOrder) {
        LambdaQueryWrapper<RoomPlayer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, userId);
        RoomPlayer player = roomPlayerMapper.selectOne(wrapper);
        
        if (player == null) {
            throw new RuntimeException("您不在该房间中");
        }
        
        // 验证turnOrder值（1=先手, 2=后手）
        if (turnOrder == null || (turnOrder != 1 && turnOrder != 2)) {
            throw new RuntimeException("先手/后手值无效，必须是1（先手）或2（后手）");
        }
        
        // 检查是否与其他玩家冲突
        LambdaQueryWrapper<RoomPlayer> otherWrapper = new LambdaQueryWrapper<>();
        otherWrapper.eq(RoomPlayer::getRoomId, roomId)
                    .ne(RoomPlayer::getUserId, userId)
                    .eq(RoomPlayer::getTurnOrder, turnOrder);
        RoomPlayer otherPlayer = roomPlayerMapper.selectOne(otherWrapper);
        if (otherPlayer != null) {
            throw new RuntimeException("对方已选择" + (turnOrder == 1 ? "先手" : "后手") + "，请选择另一个");
        }
        
        player.setTurnOrder(turnOrder);
        roomPlayerMapper.updateById(player);
        
        // 通过WebSocket通知房间内其他玩家
        webSocketService.notifyTurnOrderSet(roomId, userId, turnOrder);
        
        // 检查是否全部准备（包括先手/后手都选择）
        checkAndAutoStart(roomId);
    }
    
    /**
     * 检查并自动开始游戏（当人数达到2人且都准备时）
     */
    private void checkAndAutoStart(Long roomId) {
        List<Map<String, Object>> players = getRoomPlayers(roomId);
        
        // 检查是否满足自动开始条件
        if (players.size() == 2) {
            // 检查是否都已设置数字
            boolean allSetNumber = players.stream()
                    .allMatch(p -> {
                        Boolean hasSetNumber = (Boolean) p.get("hasSetNumber");
                        return hasSetNumber != null && hasSetNumber;
                    });
            
            // 检查是否都已选择先手/后手
            boolean allSetTurnOrder = players.stream()
                    .allMatch(p -> {
                        Integer turnOrder = (Integer) p.get("turnOrder");
                        return turnOrder != null && (turnOrder == 1 || turnOrder == 2);
                    });
            
            // 检查是否都已准备
            boolean allReady = players.stream()
                    .allMatch(p -> {
                        Boolean isReady = (Boolean) p.get("isReady");
                        return isReady != null && isReady;
                    });
            
            // 检查先手/后手是否冲突
            boolean hasConflict = false;
            if (allSetTurnOrder) {
                List<Integer> turnOrders = players.stream()
                        .map(p -> (Integer) p.get("turnOrder"))
                        .collect(Collectors.toList());
                hasConflict = turnOrders.get(0).equals(turnOrders.get(1));
            }
            
            // 如果全部满足条件且没有冲突，通知开始倒计时
            if (allSetNumber && allSetTurnOrder && allReady && !hasConflict) {
                Room room = roomMapper.selectById(roomId);
                if (room != null && room.getStatus() == 0) {
                    // 通过WebSocket通知前端开始倒计时
                    webSocketService.notifyStartCountdown(roomId, 3);
                }
            }
        }
    }
    
    public Map<String, Object> getRoomInfo(Long roomId, Long userId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("room", convertRoomToMap(room));
        List<Map<String, Object>> players = getRoomPlayers(roomId);
        result.put("players", players);
        
        // 返回当前用户ID（如果已识别）
        if (userId != null) {
            result.put("currentUserId", userId);
        }
        
        // 检查是否全部准备（需要同时满足：人数=2，都已设置数字，都已准备）
        boolean allReady = players.size() == 2 && players.stream()
                .allMatch(p -> {
                    Boolean isReady = (Boolean) p.get("isReady");
                    Boolean hasSetNumber = (Boolean) p.get("hasSetNumber");
                    return isReady != null && isReady && hasSetNumber != null && hasSetNumber;
                });
        result.put("isAllReady", allReady);
        
        return result;
    }
    
    @Transactional
    public void startGame(Long roomId, Long userId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        
        // 允许自动开始（不需要房主权限）
        // if (!room.getOwnerId().equals(userId)) {
        //     throw new RuntimeException("只有房主可以开始游戏");
        // }
        
        // 检查是否全部准备
        List<Map<String, Object>> players = getRoomPlayers(roomId);
        if (players.size() < 2) {
            throw new RuntimeException("房间人数不足");
        }
        
        boolean allReady = players.stream().allMatch(p -> {
            Boolean isReady = (Boolean) p.get("isReady");
            Boolean hasSetNumber = (Boolean) p.get("hasSetNumber");
            return isReady != null && isReady && hasSetNumber != null && hasSetNumber;
        });
        if (!allReady) {
            throw new RuntimeException("还有玩家未准备或未设置数字");
        }
        
        // 猜数字游戏不需要生成炸弹数字，每个玩家都有自己的数字
        room.setStatus(1); // 游戏中
        roomMapper.updateById(room);
        
        // 通过WebSocket通知游戏开始（实际游戏ID由GameService创建后推送）
        // 这里先推送房间状态更新
        Map<String, Object> roomInfo = new HashMap<>();
        roomInfo.put("status", 1);
        webSocketService.notifyRoomUpdated(roomId, roomInfo);
    }
    
    private String generateRoomCode() {
        Random random = new Random();
        String code;
        do {
            code = String.format("%06d", random.nextInt(900000) + 100000);
        } while (roomMapper.selectOne(new LambdaQueryWrapper<Room>()
                .eq(Room::getRoomCode, code)) != null);
        return code;
    }
    
    private Map<String, Object> convertRoomToMap(Room room) {
        Map<String, Object> map = new HashMap<>();
        map.put("roomId", room.getId());
        map.put("roomCode", room.getRoomCode());
        map.put("name", room.getName());
        map.put("ownerId", room.getOwnerId());
        // 猜数字游戏不需要范围设置（保留字段以兼容数据库，但不返回）
        // map.put("minRange", room.getMinRange());
        // map.put("maxRange", room.getMaxRange());
        map.put("punishmentType", room.getPunishmentType());
        map.put("punishmentContent", room.getPunishmentContent());
        map.put("status", room.getStatus());
        map.put("expiredAt", room.getExpiredAt());
        return map;
    }
    
    public List<Map<String, Object>> getRoomPlayers(Long roomId) {
        LambdaQueryWrapper<RoomPlayer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoomPlayer::getRoomId, roomId)
                .orderByAsc(RoomPlayer::getId); // 按照ID排序，确保顺序一致
        List<RoomPlayer> players = roomPlayerMapper.selectList(wrapper);
        
        return players.stream().map(player -> {
            User user = userMapper.selectById(player.getUserId());
            Map<String, Object> playerMap = new HashMap<>();
            if (user != null) {
                playerMap.put("id", user.getId());
                playerMap.put("nickname", user.getNickname() != null ? user.getNickname() : "游客");
                playerMap.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
                playerMap.put("isOwner", player.getIsOwner() == 1);
                playerMap.put("isReady", player.getIsReady() == 1);
                playerMap.put("hasSetNumber", player.getSecretNumber() != null && !player.getSecretNumber().isEmpty());
                playerMap.put("turnOrder", player.getTurnOrder()); // 先手/后手：1=先手, 2=后手
                // secretNumber不返回给前端，前端自己存储
            }
            return playerMap;
        }).collect(Collectors.toList());
    }
}
