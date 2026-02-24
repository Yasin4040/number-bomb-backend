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
     * 先尝试根据 openid 查询用户是否存在，如果存在则直接返回。
     * 如果不存在，则创建新用户。如果创建时遇到唯一约束冲突（openid 已存在），
     * 则再次查询并返回已存在的用户。
     * 
     * 注意：数据库 openid 字段为 VARCHAR(64)，如果 tempUserId 超过 64 个字符会被截断。
     * 因此需要同时检查完整 tempUserId 和截断后的 tempUserId。
     * 
     * 这样可以避免：
     * 1. 数据库字段长度限制导致 openid 截断后的重复键错误
     * 2. 同一 tempUserId 被多次使用时重复创建用户
     */
    @Transactional
    public Long getOrCreateTempUser(String tempUserId, HttpServletRequest request) {
        // 调试日志：记录接收到的临时用户ID
        System.out.println("🔍 [getOrCreateTempUser] 接收到临时用户ID: " + tempUserId);
        System.out.println("🔍 [getOrCreateTempUser] 请求IP: " + getClientIp(request));
        
        // openid 字段最大长度为 64，如果 tempUserId 超过此长度会被截断
        final int MAX_OPENID_LENGTH = 64;
        String truncatedOpenid = tempUserId.length() > MAX_OPENID_LENGTH 
                ? tempUserId.substring(0, MAX_OPENID_LENGTH) 
                : tempUserId;
        
        // 先尝试根据完整的 openid 查询用户是否存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getOpenid, tempUserId);
        User existingUser = userMapper.selectOne(wrapper);
        
        // 如果完整查询没找到，且 tempUserId 可能被截断，尝试用截断后的值查询
        if (existingUser == null && tempUserId.length() > MAX_OPENID_LENGTH) {
            LambdaQueryWrapper<User> truncatedWrapper = new LambdaQueryWrapper<>();
            truncatedWrapper.eq(User::getOpenid, truncatedOpenid);
            existingUser = userMapper.selectOne(truncatedWrapper);
            if (existingUser != null) {
                System.out.println("⚠️ [getOrCreateTempUser] 使用截断后的 openid 找到用户: " + truncatedOpenid);
            }
        }
        
        if (existingUser != null) {
            // 用户已存在，检查是否需要更新昵称
            String tempNickname = (String) request.getAttribute("tempNickname");
            boolean needUpdate = false;
            
            // 如果前端传入了新昵称，且与数据库中的昵称不同，则更新
            if (tempNickname != null && !tempNickname.isEmpty() && 
                !tempNickname.equals(existingUser.getNickname())) {
                // 检查新昵称是否与其他用户冲突
                LambdaQueryWrapper<User> nicknameWrapper = new LambdaQueryWrapper<>();
                nicknameWrapper.eq(User::getNickname, tempNickname)
                              .ne(User::getId, existingUser.getId());
                User existingNickname = userMapper.selectOne(nicknameWrapper);
                
                if (existingNickname == null) {
                    // 新昵称可用，更新
                    existingUser.setNickname(tempNickname);
                    needUpdate = true;
                    System.out.println("✅ [getOrCreateTempUser] 更新用户昵称: " + existingUser.getNickname() + " -> " + tempNickname);
                } else {
                    System.out.println("⚠️ [getOrCreateTempUser] 昵称已存在，保持原昵称: " + existingUser.getNickname());
                }
            }
            
            // 更新最后登录时间
            existingUser.setLastLogin(LocalDateTime.now());
            userMapper.updateById(existingUser);
            System.out.println("✅ [getOrCreateTempUser] 找到已存在的用户: 数据库ID=" + existingUser.getId() + ", 临时ID=" + tempUserId + ", 昵称=" + existingUser.getNickname());
            return existingUser.getId();
        }
        
        // 用户不存在，创建新用户
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
        // 如果 tempUserId 超过 64 个字符，使用截断后的值
        tempUser.setOpenid(truncatedOpenid);
        tempUser.setNickname(nickname);
        tempUser.setAvatarUrl(""); // 临时用户没有头像
        tempUser.setTotalGames(0);
        tempUser.setWinGames(0);
        tempUser.setRankScore(0);
        tempUser.setRankLevel(1);
        tempUser.setMaxStreak(0);
        tempUser.setCurrentStreak(0);
        tempUser.setLastLogin(LocalDateTime.now());
        
        try {
            userMapper.insert(tempUser);
            System.out.println("✅ [getOrCreateTempUser] 创建新的临时用户: 数据库ID=" + tempUser.getId() + ", 临时ID=" + tempUserId + ", 存储的openid=" + truncatedOpenid + ", 昵称=" + nickname);
            return tempUser.getId();
        } catch (Exception e) {
            // 如果插入时遇到唯一约束冲突（可能是 openid 字段被截断导致），
            // 再次查询并返回已存在的用户
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                System.out.println("⚠️ [getOrCreateTempUser] 检测到重复键错误，尝试查询已存在的用户: " + e.getMessage());
                // 使用截断后的 openid 查询
                LambdaQueryWrapper<User> duplicateWrapper = new LambdaQueryWrapper<>();
                duplicateWrapper.eq(User::getOpenid, truncatedOpenid);
                User duplicateUser = userMapper.selectOne(duplicateWrapper);
                if (duplicateUser != null) {
                    // 检查是否需要更新昵称（与上面逻辑相同）
                    tempNickname = (String) request.getAttribute("tempNickname");
                    if (tempNickname != null && !tempNickname.isEmpty() && 
                        !tempNickname.equals(duplicateUser.getNickname())) {
                        nicknameWrapper = new LambdaQueryWrapper<>();
                        nicknameWrapper.eq(User::getNickname, tempNickname)
                                      .ne(User::getId, duplicateUser.getId());
                        existingNickname = userMapper.selectOne(nicknameWrapper);
                        
                        if (existingNickname == null) {
                            duplicateUser.setNickname(tempNickname);
                            System.out.println("✅ [getOrCreateTempUser] 更新用户昵称（重复键处理）: " + duplicateUser.getNickname() + " -> " + tempNickname);
                        }
                    }
                    
                    // 更新最后登录时间
                    duplicateUser.setLastLogin(LocalDateTime.now());
                    userMapper.updateById(duplicateUser);
                    System.out.println("✅ [getOrCreateTempUser] 找到已存在的用户（重复键处理）: 数据库ID=" + duplicateUser.getId() + ", 临时ID=" + tempUserId + ", 存储的openid=" + truncatedOpenid + ", 昵称=" + duplicateUser.getNickname());
                    return duplicateUser.getId();
                }
            }
            // 如果是其他异常，重新抛出
            throw new RuntimeException("创建临时用户失败: " + e.getMessage(), e);
        }
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
        
        Map<String, Object> result = convertRoomToMap(room);
        // 添加当前用户ID（用于前端识别）
        result.put("currentUserId", userId);
        
        return result;
    }

    @Transactional
    public Map<String, Object> joinRoom(Long userId, String roomCode) {
        // 查找房间
        LambdaQueryWrapper<Room> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Room::getRoomCode, roomCode)
                .in(Room::getStatus, 0,1);
        Room room = roomMapper.selectOne(wrapper);

        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        // 检查房间是否过期
        if (room.getExpiredAt() != null && room.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("房间已过期");
        }

        // 检查是否已加入
        LambdaQueryWrapper<RoomPlayer> playerWrapper = new LambdaQueryWrapper<>();
        playerWrapper.eq(RoomPlayer::getRoomId, room.getId())
                .eq(RoomPlayer::getUserId, userId);
        RoomPlayer existing = roomPlayerMapper.selectOne(playerWrapper);

        boolean isReconnect = false;
        if (existing != null) {
            // 用户已在房间中，视为断线重连
            isReconnect = true;
            System.out.println("ℹ️ [joinRoom] 用户已在房间中，执行重连逻辑: userId=" + userId + ", roomCode=" + roomCode);

            // 重连时更新房间过期时间（延长有效期）
            room.setExpiredAt(LocalDateTime.now().plusMinutes(expireMinutes));
            roomMapper.updateById(room);
            System.out.println("ℹ️ [joinRoom] 重连成功，更新房间过期时间: roomId=" + room.getId() + ", newExpiredAt=" + room.getExpiredAt());
        } else {
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

            // 通过WebSocket通知房间内其他玩家（仅首次加入时推送）
            webSocketService.notifyPlayerJoined(room.getId(), playerInfo);
            System.out.println("✅ [joinRoom] 玩家首次加入房间: userId=" + userId + ", roomCode=" + roomCode);
        }

        // 返回房间信息和玩家列表（重连和首次加入返回相同格式）
        Map<String, Object> result = new HashMap<>();
        result.put("room", convertRoomToMap(room));
        result.put("players", getRoomPlayers(room.getId()));
        // 添加当前用户ID（用于前端识别）
        result.put("currentUserId", userId);
        // 添加重连标识，方便前端区分是首次加入还是重连
        result.put("isReconnect", isReconnect);

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
    
    /**
     * 更新用户昵称
     */
    public void updateUserNickname(Long userId, String nickname) {
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
        System.out.println("✅ [updateUserNickname] 更新用户昵称: userId=" + userId + ", nickname=" + nickname);
    }
    
    /**
     * 获取客户端IP地址（用于调试）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个IP的情况，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "0.0.0.0";
    }
}
