package com.numberbomb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.numberbomb.entity.*;
import com.numberbomb.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GameService {
    
    private final GameRecordMapper gameRecordMapper;
    private final GameActionMapper gameActionMapper;
    private final RoomMapper roomMapper;
    private final RoomPlayerMapper roomPlayerMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WebSocketService webSocketService;
    
    /**
     * 处理消息确认
     */
    public void handleMessageAck(String userId, String messageId) {
        webSocketService.handleMessageAck(userId, messageId);
    }
    
    @Transactional
    public Map<String, Object> startGame(Long roomId, Long userId) {
        System.out.println("🎮 开始游戏: roomId=" + roomId + ", userId=" + userId);
        
        // 幂等性校验：检查是否正在开始游戏
        String lockKey = "startGame:lock:" + roomId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        if (!locked) {
            System.out.println("⚠️ 游戏正在开始中，请勿重复操作: roomId=" + roomId);
            throw new RuntimeException("游戏正在开始中，请勿重复操作");
        }
        
        try {
            return doStartGame(roomId, userId);
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }
    
    private Map<String, Object> doStartGame(Long roomId, Long userId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        
        // 获取玩家列表（按ID排序，确保顺序一致）
        LambdaQueryWrapper<RoomPlayer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoomPlayer::getRoomId, roomId)
                .orderByAsc(RoomPlayer::getId); // 按照ID排序，确保顺序一致
        List<RoomPlayer> players = roomPlayerMapper.selectList(wrapper);
        
        if (players.size() < 2) {
            throw new RuntimeException("房间人数不足");
        }
        
        // 检查是否所有玩家都已准备
        boolean allReady = players.stream().allMatch(p -> 
            p.getIsReady() != null && p.getIsReady() == 1 && 
            p.getSecretNumber() != null && !p.getSecretNumber().isEmpty()
        );
        
        if (!allReady) {
            throw new RuntimeException("还有玩家未准备或未设置数字");
        }
        
        // 如果房间状态不是游戏中，更新为游戏中
        if (room.getStatus() != 1) {
            room.setStatus(1);
            roomMapper.updateById(room);
            System.out.println("✅ 更新房间状态为游戏中: " + roomId);
        }
        
        // 创建新的游戏记录（猜数字模式）
        GameRecord gameRecord = new GameRecord();
        gameRecord.setRoomId(roomId);
        gameRecord.setGameType(2); // 在线房间
        gameRecord.setStartedAt(LocalDateTime.now());
        gameRecord.setPlayer1Id(players.get(0).getUserId());
        gameRecord.setPlayer2Id(players.get(1).getUserId());
        gameRecord.setPlayer1Secret(players.get(0).getSecretNumber());
        gameRecord.setPlayer2Secret(players.get(1).getSecretNumber());
        gameRecordMapper.insert(gameRecord);
        System.out.println("✅ 创建新的游戏记录: gameId=" + gameRecord.getId());
        
        // 获取玩家信息（用于返回）
        User player1 = userMapper.selectById(players.get(0).getUserId());
        User player2 = userMapper.selectById(players.get(1).getUserId());
        
        // 构建玩家列表
        List<Map<String, Object>> playerList = new ArrayList<>();
        Map<String, Object> p1 = new HashMap<>();
        p1.put("id", player1.getId());
        p1.put("nickname", player1.getNickname());
        p1.put("avatarUrl", player1.getAvatarUrl() != null ? player1.getAvatarUrl() : "");
        playerList.add(p1);
        
        Map<String, Object> p2 = new HashMap<>();
        p2.put("id", player2.getId());
        p2.put("nickname", player2.getNickname());
        p2.put("avatarUrl", player2.getAvatarUrl() != null ? player2.getAvatarUrl() : "");
        playerList.add(p2);
        
        // 构建玩家数字映射（每个玩家的4位数字）
        Map<String, String> playerSecrets = new HashMap<>();
        playerSecrets.put(String.valueOf(players.get(0).getUserId()), players.get(0).getSecretNumber());
        playerSecrets.put(String.valueOf(players.get(1).getUserId()), players.get(1).getSecretNumber());
        
        // 确定先手玩家：根据玩家选择的先手/后手
        Long firstPlayerId;
        RoomPlayer rp1 = players.get(0);
        RoomPlayer rp2 = players.get(1);
        
        // 获取两个玩家的选择
        Integer p1TurnOrder = rp1.getTurnOrder();
        Integer p2TurnOrder = rp2.getTurnOrder();
        
        if (p1TurnOrder != null && p2TurnOrder != null) {
            // 两个玩家都选择了
            if (p1TurnOrder == 1) {
                firstPlayerId = rp1.getUserId();
            } else if (p2TurnOrder == 1) {
                firstPlayerId = rp2.getUserId();
            } else {
                // 都没有选择先手（不应该发生），按ID顺序
                System.out.println("⚠️ 警告：两个玩家都没有选择先手，按ID顺序选择");
                firstPlayerId = Math.min(rp1.getUserId(), rp2.getUserId());
            }
            System.out.println("✅ 根据先手/后手选择确定先手玩家: " + firstPlayerId + 
                " (玩家1 turnOrder=" + p1TurnOrder + ", 玩家2 turnOrder=" + p2TurnOrder + ")");
        } else if (p1TurnOrder != null) {
            // 只有玩家1选择了
            firstPlayerId = p1TurnOrder == 1 ? rp1.getUserId() : rp2.getUserId();
            System.out.println("✅ 只有玩家1选择了先手/后手: " + firstPlayerId + " (玩家1 turnOrder=" + p1TurnOrder + ")");
        } else if (p2TurnOrder != null) {
            // 只有玩家2选择了
            firstPlayerId = p2TurnOrder == 1 ? rp2.getUserId() : rp1.getUserId();
            System.out.println("✅ 只有玩家2选择了先手/后手: " + firstPlayerId + " (玩家2 turnOrder=" + p2TurnOrder + ")");
        } else {
            // 都没选择，按ID顺序
            firstPlayerId = Math.min(rp1.getUserId(), rp2.getUserId());
            System.out.println("✅ 玩家未选择先手/后手，按玩家ID顺序选择先手: " + firstPlayerId + 
                " (玩家1: " + rp1.getUserId() + ", 玩家2: " + rp2.getUserId() + ")");
        }
        
        // 创建新的游戏状态
        String gameKey = "game:" + gameRecord.getId();
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("gameId", gameRecord.getId());
        gameState.put("gameType", "online");
        gameState.put("status", "playing");
        gameState.put("currentPlayer", firstPlayerId);
        gameState.put("firstPlayer", firstPlayerId); // 记录先手玩家，用于前端颜色区分
        gameState.put("countdown", 180); // 3分钟 = 180秒
        gameState.put("players", playerList);
        gameState.put("history", new ArrayList<>());
        gameState.put("playerSecrets", playerSecrets);
        // 添加当前用户ID（用于前端识别）
        gameState.put("currentUserId", userId);
        // 添加当前用户的数字（用于前端显示，增加紧张感）
        String mySecretNumber = playerSecrets.get(String.valueOf(userId));
        if (mySecretNumber != null) {
            gameState.put("mySecretNumber", mySecretNumber);
        }
        // 添加最后活跃时间
        gameState.put("lastActiveTime", System.currentTimeMillis());
        // 初始化玩家离线状态Map
        Map<String, Boolean> playerOffline = new HashMap<>();
        playerOffline.put(String.valueOf(players.get(0).getUserId()), false);
        playerOffline.put(String.valueOf(players.get(1).getUserId()), false);
        gameState.put("playerOffline", playerOffline);
        // 初始化玩家放弃状态Map
        Map<String, Boolean> playerGiveUp = new HashMap<>();
        playerGiveUp.put(String.valueOf(players.get(0).getUserId()), false);
        playerGiveUp.put(String.valueOf(players.get(1).getUserId()), false);
        gameState.put("playerGiveUp", playerGiveUp);
        
        // 保存到Redis，延长到24小时
        redisTemplate.opsForValue().set(gameKey, gameState, 24, TimeUnit.HOURS);
        System.out.println("✅ 创建新的游戏状态: gameId=" + gameRecord.getId());
        
        System.out.println("✅ 游戏开始成功: gameId=" + gameRecord.getId() + ", roomId=" + roomId + ", currentUserId=" + userId);
        System.out.println("✅ 游戏状态包含的字段: " + gameState.keySet());
        System.out.println("✅ currentUserId 值: " + gameState.get("currentUserId"));
        
        // 通过WebSocket通知游戏开始
        webSocketService.notifyGameStarted(roomId, gameRecord.getId());
        
        // 通知回合切换（先手玩家）
        Object currentPlayerObj = gameState.get("currentPlayer");
        Long currentPlayerId = null;
        if (currentPlayerObj instanceof Long) {
            currentPlayerId = (Long) currentPlayerObj;
        } else if (currentPlayerObj instanceof Integer) {
            currentPlayerId = ((Integer) currentPlayerObj).longValue();
        } else if (currentPlayerObj instanceof Number) {
            currentPlayerId = ((Number) currentPlayerObj).longValue();
        }
        if (currentPlayerId != null) {
            // 找到对手ID
            Long opponentId = null;
            for (Map<String, Object> player : playerList) {
                Long playerId = ((Number) player.get("id")).longValue();
                if (!playerId.equals(currentPlayerId)) {
                    opponentId = playerId;
                    break;
                }
            }
            
            Integer countdown = (Integer) gameState.get("countdown");
            if (countdown == null) {
                countdown = 180;
            }
            // 传入对手ID，确保两个玩家都能收到
            webSocketService.notifyTurnChanged(gameRecord.getId(), currentPlayerId, opponentId, countdown);
        }
        
        return gameState;
    }
    
    /**
     * 计算AB结果（猜数字模式：4位数字，允许重复）
     * @param secret 正确答案（4位数字，允许重复）
     * @param guess 玩家猜测（4位数字，允许重复）
     * @return { a: number, b: number } A=数字和位置都对，B=数字对但位置错
     */
    private Map<String, Integer> calculateAB(String secret, String guess) {
        int a = 0; // 数字和位置都对
        int b = 0; // 数字对但位置错
        
        char[] secretChars = secret.toCharArray();
        char[] guessChars = guess.toCharArray();
        
        // 先计算A（位置和数字都对）
        boolean[] matchedPositions = new boolean[4];
        boolean[] matchedInSecret = new boolean[4];
        
        for (int i = 0; i < 4; i++) {
            if (secretChars[i] == guessChars[i]) {
                a++;
                matchedPositions[i] = true;
                matchedInSecret[i] = true;
            }
        }
        
        // 计算B（数字对但位置错）
        // 对于每个未匹配位置的猜测数字，检查是否在秘密数字的未匹配位置中存在
        for (int i = 0; i < 4; i++) {
            if (!matchedPositions[i]) {
                char guessChar = guessChars[i];
                // 在秘密数字的未匹配位置中查找
                for (int j = 0; j < 4; j++) {
                    if (!matchedInSecret[j] && secretChars[j] == guessChar) {
                        b++;
                        matchedInSecret[j] = true;
                        break; // 每个猜测数字只能匹配一次
                    }
                }
            }
        }
        
        Map<String, Integer> result = new HashMap<>();
        result.put("a", a);
        result.put("b", b);
        return result;
    }
    
    @Transactional
    public Map<String, Object> makeGuess(Long gameId, Long userId, String guess) {
        System.out.println("🎯 [makeGuess] 开始处理猜测: gameId=" + gameId + ", userId=" + userId + ", guess=" + guess);
        
        // 验证猜测格式（4位数字，允许重复，允许0开头）
        if (guess == null || guess.length() != 4 || !guess.matches("^\\d{4}$")) {
            System.out.println("❌ [makeGuess] 猜测格式错误: guess=" + guess);
            throw new RuntimeException("请输入4位数字");
        }
        
        // 从Redis获取游戏状态
        String gameKey = "game:" + gameId;
        Map<String, Object> gameState = (Map<String, Object>) redisTemplate.opsForValue().get(gameKey);
        
        if (gameState == null) {
            System.out.println("❌ [makeGuess] 游戏不存在或已结束: gameId=" + gameId);
            throw new RuntimeException("游戏不存在或已结束");
        }
        
        String status = (String) gameState.get("status");
        if (!"playing".equals(status)) {
            System.out.println("❌ [makeGuess] 游戏已结束: gameId=" + gameId + ", status=" + status);
            throw new RuntimeException("游戏已结束");
        }
        
        // 安全地获取 currentPlayer（可能是 Long 或 Integer，Redis序列化可能导致类型变化）
        Object currentPlayerObj = gameState.get("currentPlayer");
        if (currentPlayerObj == null) {
            System.out.println("❌ [makeGuess] 游戏状态异常：currentPlayer 为空");
            throw new RuntimeException("游戏状态异常：currentPlayer 为空");
        }
        
        Long currentPlayer = null;
        if (currentPlayerObj instanceof Long) {
            currentPlayer = (Long) currentPlayerObj;
        } else if (currentPlayerObj instanceof Integer) {
            currentPlayer = ((Integer) currentPlayerObj).longValue();
        } else if (currentPlayerObj instanceof Number) {
            currentPlayer = ((Number) currentPlayerObj).longValue();
        } else {
            System.out.println("❌ [makeGuess] currentPlayer 类型错误: " + currentPlayerObj.getClass().getName() + ", 值: " + currentPlayerObj);
            throw new RuntimeException("游戏状态异常：currentPlayer 类型错误: " + currentPlayerObj.getClass().getName());
        }
        
        // 确保 userId 也是 Long 类型进行比较
        Long userIdLong = userId.longValue();
        
        // 使用 longValue() 进行比较，避免类型问题
        if (!currentPlayer.equals(userIdLong)) {
            System.out.println("❌ [makeGuess] 回合检查失败:");
            System.out.println("   currentPlayer=" + currentPlayer + " (类型: " + currentPlayer.getClass().getName() + ")");
            System.out.println("   userId=" + userId + " (类型: " + userId.getClass().getName() + ")");
            System.out.println("   userIdLong=" + userIdLong + " (类型: " + userIdLong.getClass().getName() + ")");
            System.out.println("   比较结果: currentPlayer.equals(userId)=" + currentPlayer.equals(userId));
            System.out.println("   比较结果: currentPlayer.equals(userIdLong)=" + currentPlayer.equals(userIdLong));
            System.out.println("   游戏状态: " + gameState);
            throw new RuntimeException("不是您的回合，当前回合玩家ID: " + currentPlayer + ", 您的ID: " + userId);
        }
        
        System.out.println("✅ [makeGuess] 回合检查通过: currentPlayer=" + currentPlayer + ", userId=" + userId);
        
        // 获取对手的数字（要猜的数字）
        Map<String, String> playerSecrets = (Map<String, String>) gameState.get("playerSecrets");
        if (playerSecrets == null) {
            System.out.println("❌ [makeGuess] 游戏配置错误: playerSecrets 为空");
            throw new RuntimeException("游戏配置错误");
        }
        
        // 找到对手的ID
        List<Map<String, Object>> players = (List<Map<String, Object>>) gameState.get("players");
        Long opponentId = null;
        Long currentUserIdLong = userId.longValue(); // 确保类型一致
        for (Map<String, Object> player : players) {
            Object playerIdObj = player.get("id");
            Long playerId = null;
            if (playerIdObj instanceof Long) {
                playerId = (Long) playerIdObj;
            } else if (playerIdObj instanceof Integer) {
                playerId = ((Integer) playerIdObj).longValue();
            } else if (playerIdObj instanceof Number) {
                playerId = ((Number) playerIdObj).longValue();
            }
            
            if (playerId != null && !playerId.equals(currentUserIdLong)) {
                opponentId = playerId;
                break;
            }
        }
        
        if (opponentId == null) {
            System.out.println("❌ [makeGuess] 找不到对手: userId=" + userId + ", players=" + players);
            throw new RuntimeException("找不到对手");
        }
        
        System.out.println("✅ [makeGuess] 找到对手: opponentId=" + opponentId + ", userId=" + userId);
        
        // 获取对手的数字
        String secretNumber = playerSecrets.get(String.valueOf(opponentId));
        if (secretNumber == null || secretNumber.isEmpty()) {
            System.out.println("❌ [makeGuess] 对手数字不存在: opponentId=" + opponentId);
            throw new RuntimeException("对手数字不存在");
        }
        
        // 计算AB结果
        Map<String, Integer> abResult = calculateAB(secretNumber, guess);
        int a = abResult.get("a");
        int b = abResult.get("b");
        
        // 判断是否获胜（4A0B）
        boolean isWin = (a == 4 && b == 0);
        boolean isGameOver = isWin;
        
        System.out.println("🎯 [makeGuess] 计算结果: guess=" + guess + ", secret=" + secretNumber + ", result=" + a + "A" + b + "B, isWin=" + isWin);
        
        // 获取玩家昵称
        String playerName = null;
        for (Map<String, Object> player : players) {
            Object playerIdObj = player.get("id");
            Long playerId = null;
            if (playerIdObj instanceof Long) {
                playerId = (Long) playerIdObj;
            } else if (playerIdObj instanceof Integer) {
                playerId = ((Integer) playerIdObj).longValue();
            } else if (playerIdObj instanceof Number) {
                playerId = ((Number) playerIdObj).longValue();
            }
            
            if (playerId != null && playerId.equals(currentUserIdLong)) {
                playerName = (String) player.get("nickname");
                break;
            }
        }
        
        if (playerName == null) {
            playerName = "玩家" + userId; // 如果找不到昵称，使用默认值
        }
        
        // 记录操作到数据库（猜数字模式）
        int roundNumber = getRoundNumber(gameId) + 1;
        GameAction gameAction = new GameAction();
        gameAction.setGameId(gameId);
        gameAction.setRoundNumber(roundNumber);
        gameAction.setPlayerId(userId);
        gameAction.setGuess(guess);
        gameAction.setResultA(a);
        gameAction.setResultB(b);
        gameAction.setIsWin(isWin);
        gameActionMapper.insert(gameAction);
        System.out.println("✅ [makeGuess] 游戏动作已保存到数据库: round=" + roundNumber);
        
        // 同时保存到Redis的history中（用于实时查询）
        List<Map<String, Object>> history = (List<Map<String, Object>>) gameState.get("history");
        if (history == null) {
            history = new ArrayList<>();
            gameState.put("history", history);
        }
        
        Map<String, Object> action = new HashMap<>();
        action.put("round", roundNumber);
        action.put("playerId", userId);
        action.put("playerName", playerName);
        action.put("guess", guess);
        Map<String, Integer> resultMap = new HashMap<>();
        resultMap.put("a", a);
        resultMap.put("b", b);
        action.put("result", resultMap);
        action.put("isWin", isWin);
        action.put("timestamp", System.currentTimeMillis());
        history.add(0, action); // 添加到开头
        
        if (isGameOver) {
            gameState.put("status", "ended");
            gameState.put("winnerId", userId);
            gameState.put("loserId", opponentId);
            
            System.out.println("🏆 [makeGuess] 游戏结束，获胜者: userId=" + userId);
            
            // 更新游戏记录
            GameRecord gameRecord = gameRecordMapper.selectById(gameId);
            if (gameRecord != null) {
                gameRecord.setWinnerId(userId);
                gameRecord.setLoserId(opponentId);
                gameRecord.setEndedAt(LocalDateTime.now());
                gameRecordMapper.updateById(gameRecord);
                
                // 更新房间状态
                if (gameRecord.getRoomId() != null) {
                    Room room = roomMapper.selectById(gameRecord.getRoomId());
                    if (room != null) {
                        room.setStatus(2); // 已结束
                        room.setWinnerId(userId);
                        roomMapper.updateById(room);
                    }
                }
                
                // 更新用户统计
                updateUserStats(userId, true);
                updateUserStats(opponentId, false);
            }
        } else {
            // 切换玩家
            gameState.put("currentPlayer", opponentId);
            gameState.put("countdown", 180); // 重置倒计时为3分钟
            System.out.println("🔄 [makeGuess] 切换回合: 下一个玩家=" + opponentId);
        }
        
        // 更新最后活跃时间
        gameState.put("lastActiveTime", System.currentTimeMillis());
        
        // 更新Redis，延长到24小时
        redisTemplate.opsForValue().set(gameKey, gameState, 24, TimeUnit.HOURS);
        System.out.println("✅ [makeGuess] 游戏状态已更新到Redis");

        // 获取下一个玩家ID和倒计时（用于消息通知）
        Long nextPlayerId = null;
        Integer countdown = 180;
        if (!isGameOver) {
            Object nextPlayerObj = gameState.get("currentPlayer");
            if (nextPlayerObj instanceof Long) {
                nextPlayerId = (Long) nextPlayerObj;
            } else if (nextPlayerObj instanceof Integer) {
                nextPlayerId = ((Integer) nextPlayerObj).longValue();
            } else if (nextPlayerObj instanceof Number) {
                nextPlayerId = ((Number) nextPlayerObj).longValue();
            }
            countdown = (Integer) gameState.get("countdown");
            if (countdown == null) {
                countdown = 180;
            }
        }

        // 发送猜测结果（包含nextPlayer，让前端可以立即更新回合状态）
        System.out.println("📤 [makeGuess] 发送猜测结果通知: gameId=" + gameId + ", playerId=" + userId + ", opponentId=" + opponentId + ", nextPlayerId=" + nextPlayerId);
        webSocketService.notifyGuessResult(gameId, userId, opponentId, guess, a, b, isGameOver, playerName, roundNumber, isWin, nextPlayerId, countdown);
        
        if (isGameOver) {
            // 游戏结束，通知所有玩家（传入loserId）
            System.out.println("📤 [makeGuess] 发送游戏结束通知: winnerId=" + userId + ", loserId=" + opponentId);
            Map<String, Object> gameResult = new HashMap<>();
            gameResult.put("winnerId", userId);
            gameResult.put("loserId", opponentId);
            webSocketService.notifyGameEnded(gameId, userId, opponentId, gameResult);
        } else if (nextPlayerId != null) {
            // 游戏未结束，发送回合切换消息（确保两个玩家都能收到）
            // 注意：notifyGuessResult已经包含了nextPlayer信息，这里再次发送是为了确保回合切换消息被正确接收
            System.out.println("📤 [makeGuess] 发送回合切换通知: nextPlayerId=" + nextPlayerId + ", previousPlayerId=" + userId);
            webSocketService.notifyTurnChanged(gameId, nextPlayerId, userId, countdown);
        }
        
        // 构建响应
        Map<String, Object> response = new HashMap<>();
        Map<String, Integer> result = new HashMap<>();
        result.put("a", a);
        result.put("b", b);
        response.put("result", result);
        response.put("nextPlayer", gameState.get("currentPlayer"));
        response.put("isGameOver", isGameOver);
        
        System.out.println("✅ [makeGuess] 猜测处理完成: userId=" + userId + ", guess=" + guess + ", result=" + a + "A" + b + "B, isWin=" + isWin);
        
        return response;
    }
    
    /**
     * 游戏重连
     * @param gameId 游戏ID
     * @param userId 用户ID
     * @return 完整游戏状态
     */
    @Transactional
    public Map<String, Object> reconnectGame(Long gameId, Long userId) {
        System.out.println("🔄 [reconnectGame] 游戏重连: gameId=" + gameId + ", userId=" + userId);
        
        // 验证游戏是否存在
        GameRecord gameRecord = gameRecordMapper.selectById(gameId);
        if (gameRecord == null) {
            throw new RuntimeException("游戏不存在");
        }
        
        // 验证用户是否在游戏房间中
        if (!userId.equals(gameRecord.getPlayer1Id()) && !userId.equals(gameRecord.getPlayer2Id())) {
            throw new RuntimeException("您不在该游戏中");
        }
        
        // 检查游戏是否已结束
        if (gameRecord.getEndedAt() != null) {
            throw new RuntimeException("游戏已结束");
        }
        
        // 获取游戏状态
        String gameKey = "game:" + gameId;
        Map<String, Object> gameState = (Map<String, Object>) redisTemplate.opsForValue().get(gameKey);
        
        if (gameState == null) {
            // 如果Redis中没有，从数据库恢复
            gameState = restoreGameStateFromDb(gameId, userId);
        }
        
        // 更新最后活跃时间
        gameState.put("lastActiveTime", System.currentTimeMillis());
        
        // 重置玩家离线状态
        Map<String, Boolean> playerOffline = (Map<String, Boolean>) gameState.get("playerOffline");
        if (playerOffline == null) {
            playerOffline = new HashMap<>();
        }
        playerOffline.put(String.valueOf(userId), false);
        gameState.put("playerOffline", playerOffline);
        
        // 保存回Redis
        redisTemplate.opsForValue().set(gameKey, gameState, 24, TimeUnit.HOURS);
        
        // 更新当前用户ID
        gameState.put("currentUserId", userId);
        
        // 添加当前用户的数字
        Map<String, String> playerSecrets = (Map<String, String>) gameState.get("playerSecrets");
        if (playerSecrets != null) {
            String mySecretNumber = playerSecrets.get(String.valueOf(userId));
            if (mySecretNumber != null) {
                gameState.put("mySecretNumber", mySecretNumber);
            }
        }
        
        // 获取对手ID
        Long opponentId = userId.equals(gameRecord.getPlayer1Id()) 
            ? gameRecord.getPlayer2Id() 
            : gameRecord.getPlayer1Id();
        
        // 通过WebSocket通知其他玩家该玩家已重连
        webSocketService.notifyPlayerReconnected(gameId, userId, opponentId);
        
        System.out.println("✅ [reconnectGame] 游戏重连成功: gameId=" + gameId + ", userId=" + userId);
        
        return gameState;
    }
    
    /**
     * 从数据库恢复游戏状态
     */
    private Map<String, Object> restoreGameStateFromDb(Long gameId, Long userId) {
        System.out.println("🔄 [restoreGameStateFromDb] 从数据库恢复游戏状态: gameId=" + gameId);
        
        GameRecord gameRecord = gameRecordMapper.selectById(gameId);
        if (gameRecord == null) {
            throw new RuntimeException("游戏不存在");
        }
        
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("gameId", gameId);
        gameState.put("gameType", "online");
        gameState.put("status", gameRecord.getEndedAt() != null ? "ended" : "playing");
        gameState.put("winnerId", gameRecord.getWinnerId());
        gameState.put("loserId", gameRecord.getLoserId());
        gameState.put("lastActiveTime", System.currentTimeMillis());
        
        // 初始化离线状态Map
        Map<String, Boolean> playerOffline = new HashMap<>();
        playerOffline.put(String.valueOf(gameRecord.getPlayer1Id()), false);
        playerOffline.put(String.valueOf(gameRecord.getPlayer2Id()), false);
        gameState.put("playerOffline", playerOffline);
        
        // 初始化放弃状态Map
        Map<String, Boolean> playerGiveUp = new HashMap<>();
        playerGiveUp.put(String.valueOf(gameRecord.getPlayer1Id()), false);
        playerGiveUp.put(String.valueOf(gameRecord.getPlayer2Id()), false);
        gameState.put("playerGiveUp", playerGiveUp);
        
        // 获取玩家信息
        List<Map<String, Object>> players = new ArrayList<>();
        User player1 = userMapper.selectById(gameRecord.getPlayer1Id());
        User player2 = userMapper.selectById(gameRecord.getPlayer2Id());
        
        if (player1 != null) {
            Map<String, Object> p1 = new HashMap<>();
            p1.put("id", player1.getId());
            p1.put("nickname", player1.getNickname());
            p1.put("avatarUrl", player1.getAvatarUrl() != null ? player1.getAvatarUrl() : "");
            players.add(p1);
        }
        
        if (player2 != null) {
            Map<String, Object> p2 = new HashMap<>();
            p2.put("id", player2.getId());
            p2.put("nickname", player2.getNickname());
            p2.put("avatarUrl", player2.getAvatarUrl() != null ? player2.getAvatarUrl() : "");
            players.add(p2);
        }
        
        gameState.put("players", players);
        
        // 玩家数字
        Map<String, String> playerSecrets = new HashMap<>();
        if (gameRecord.getPlayer1Secret() != null) {
            playerSecrets.put(String.valueOf(gameRecord.getPlayer1Id()), gameRecord.getPlayer1Secret());
        }
        if (gameRecord.getPlayer2Secret() != null) {
            playerSecrets.put(String.valueOf(gameRecord.getPlayer2Id()), gameRecord.getPlayer2Secret());
        }
        gameState.put("playerSecrets", playerSecrets);
        
        // 确定当前玩家
        if (gameRecord.getEndedAt() == null) {
            LambdaQueryWrapper<GameAction> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(GameAction::getGameId, gameId)
                   .orderByDesc(GameAction::getRoundNumber)
                   .last("LIMIT 1");
            GameAction lastAction = gameActionMapper.selectOne(wrapper);
            
            if (lastAction != null) {
                // 当前玩家应该是另一个玩家（因为上一个玩家已经操作过了）
                Long lastPlayerId = lastAction.getPlayerId();
                Long currentPlayerId = lastPlayerId.equals(gameRecord.getPlayer1Id()) 
                    ? gameRecord.getPlayer2Id() 
                    : gameRecord.getPlayer1Id();
                gameState.put("currentPlayer", currentPlayerId);
                System.out.println("✅ [restoreGameStateFromDb] 根据最后操作确定当前玩家: lastPlayerId=" + lastPlayerId + ", currentPlayerId=" + currentPlayerId);
            } else {
                // 如果没有操作记录，需要从房间玩家信息中确定先手
                // 优先使用turnOrder来确定先手
                LambdaQueryWrapper<RoomPlayer> playerWrapper = new LambdaQueryWrapper<>();
                playerWrapper.eq(RoomPlayer::getRoomId, gameRecord.getRoomId())
                            .orderByAsc(RoomPlayer::getId);
                List<RoomPlayer> roomPlayers = roomPlayerMapper.selectList(playerWrapper);
                
                Long firstPlayerId = null;
                if (roomPlayers.size() >= 2) {
                    Integer player1TurnOrder = roomPlayers.get(0).getTurnOrder();
                    Integer player2TurnOrder = roomPlayers.get(1).getTurnOrder();
                    
                    if (player1TurnOrder != null && player1TurnOrder == 1) {
                        firstPlayerId = roomPlayers.get(0).getUserId();
                    } else if (player2TurnOrder != null && player2TurnOrder == 1) {
                        firstPlayerId = roomPlayers.get(1).getUserId();
                    } else {
                        // 如果都没有选择先手，按玩家ID顺序（较小的先手）
                        firstPlayerId = roomPlayers.get(0).getUserId() < roomPlayers.get(1).getUserId() 
                            ? roomPlayers.get(0).getUserId() 
                            : roomPlayers.get(1).getUserId();
                    }
                } else {
                    // 如果只有1个玩家，默认玩家1先手
                    firstPlayerId = gameRecord.getPlayer1Id();
                }
                
                gameState.put("currentPlayer", firstPlayerId);
                System.out.println("✅ [restoreGameStateFromDb] 无操作记录，确定先手玩家: " + firstPlayerId);
            }
            gameState.put("countdown", 180); // 3分钟倒计时
        }
        
        // 获取历史记录
        LambdaQueryWrapper<GameAction> actionWrapper = new LambdaQueryWrapper<>();
        actionWrapper.eq(GameAction::getGameId, gameId)
                .orderByAsc(GameAction::getRoundNumber);
        List<GameAction> actions = gameActionMapper.selectList(actionWrapper);
        
        Map<Long, String> playerNameMap = new HashMap<>();
        for (Map<String, Object> player : players) {
            Long playerId = ((Number) player.get("id")).longValue();
            playerNameMap.put(playerId, (String) player.get("nickname"));
        }
        
        List<Map<String, Object>> history = actions.stream().map(action -> {
            Map<String, Object> item = new HashMap<>();
            item.put("round", action.getRoundNumber());
            item.put("playerId", action.getPlayerId());
            item.put("playerName", playerNameMap.get(action.getPlayerId()));
            item.put("guess", action.getGuess() != null ? action.getGuess() : "");
            Map<String, Integer> result = new HashMap<>();
            result.put("a", action.getResultA() != null ? action.getResultA() : 0);
            result.put("b", action.getResultB() != null ? action.getResultB() : 0);
            item.put("result", result);
            item.put("isWin", action.getIsWin() != null ? action.getIsWin() : false);
            item.put("timestamp", action.getCreatedAt() != null ? 
                action.getCreatedAt().toEpochSecond(java.time.ZoneOffset.of("+8")) * 1000 : System.currentTimeMillis());
            return item;
        }).collect(java.util.stream.Collectors.toList());
        
        gameState.put("history", history);
        
        // 保存到Redis
        redisTemplate.opsForValue().set("game:" + gameId, gameState, 24, TimeUnit.HOURS);
        
        System.out.println("✅ [restoreGameStateFromDb] 游戏状态恢复完成: gameId=" + gameId);
        
        return gameState;
    }

    public Map<String, Object> getGameStatus(Long gameId, Long userId) {
        String gameKey = "game:" + gameId;
        Map<String, Object> gameState = (Map<String, Object>) redisTemplate.opsForValue().get(gameKey);
        
        // 如果Redis中没有，尝试从数据库恢复
        if (gameState == null) {
            GameRecord gameRecord = gameRecordMapper.selectById(gameId);
            if (gameRecord == null) {
                throw new RuntimeException("游戏不存在");
            }
            
            // 从数据库恢复游戏状态
            gameState = new HashMap<>();
            gameState.put("gameId", gameId);
            gameState.put("status", gameRecord.getEndedAt() != null ? "ended" : "playing");
            gameState.put("winnerId", gameRecord.getWinnerId());
            gameState.put("loserId", gameRecord.getLoserId());
            
            // 获取玩家信息
            List<Map<String, Object>> players = new ArrayList<>();
            User player1 = userMapper.selectById(gameRecord.getPlayer1Id());
            User player2 = userMapper.selectById(gameRecord.getPlayer2Id());
            
            Map<String, Object> p1 = new HashMap<>();
            p1.put("id", player1.getId());
            p1.put("nickname", player1.getNickname());
            p1.put("avatarUrl", player1.getAvatarUrl() != null ? player1.getAvatarUrl() : "");
            players.add(p1);
            
            Map<String, Object> p2 = new HashMap<>();
            p2.put("id", player2.getId());
            p2.put("nickname", player2.getNickname());
            p2.put("avatarUrl", player2.getAvatarUrl() != null ? player2.getAvatarUrl() : "");
            players.add(p2);
            
            gameState.put("players", players);
            gameState.put("currentUserId", userId);
            
            // 从数据库获取历史记录
            LambdaQueryWrapper<GameAction> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(GameAction::getGameId, gameId)
                    .orderByAsc(GameAction::getRoundNumber);
            List<GameAction> actions = gameActionMapper.selectList(wrapper);
            
            Map<Long, String> playerNameMap = new HashMap<>();
            for (Map<String, Object> player : players) {
                Long playerId = ((Number) player.get("id")).longValue();
                playerNameMap.put(playerId, (String) player.get("nickname"));
            }
            
            List<Map<String, Object>> history = actions.stream().map(action -> {
                Map<String, Object> item = new HashMap<>();
                item.put("round", action.getRoundNumber());
                item.put("playerId", action.getPlayerId());
                item.put("playerName", playerNameMap.get(action.getPlayerId()));
                item.put("guess", action.getGuess() != null ? action.getGuess() : "");
                Map<String, Integer> result = new HashMap<>();
                result.put("a", action.getResultA() != null ? action.getResultA() : 0);
                result.put("b", action.getResultB() != null ? action.getResultB() : 0);
                item.put("result", result);
                item.put("isWin", action.getIsWin() != null ? action.getIsWin() : false);
                item.put("timestamp", action.getCreatedAt() != null ? 
                    action.getCreatedAt().toEpochSecond(java.time.ZoneOffset.of("+8")) * 1000 : System.currentTimeMillis());
                return item;
            }).collect(java.util.stream.Collectors.toList());
            
            gameState.put("history", history);
            
            // 恢复玩家数字（如果游戏已结束，可以显示）
            if (gameRecord.getEndedAt() != null && gameRecord.getPlayer1Secret() != null && gameRecord.getPlayer2Secret() != null) {
                Map<String, String> playerSecrets = new HashMap<>();
                playerSecrets.put(String.valueOf(gameRecord.getPlayer1Id()), gameRecord.getPlayer1Secret());
                playerSecrets.put(String.valueOf(gameRecord.getPlayer2Id()), gameRecord.getPlayer2Secret());
                gameState.put("playerSecrets", playerSecrets);
                
                String mySecretNumber = playerSecrets.get(String.valueOf(userId));
                if (mySecretNumber != null) {
                    gameState.put("mySecretNumber", mySecretNumber);
                }
            }
            
            // 确定当前玩家（如果游戏未结束）
            if (gameRecord.getEndedAt() == null) {
                // 从最后一次操作判断当前玩家
                if (!actions.isEmpty()) {
                    GameAction lastAction = actions.get(actions.size() - 1);
                    Long lastPlayerId = lastAction.getPlayerId();
                    // 当前玩家应该是另一个玩家（因为上一个玩家已经操作过了）
                    Long currentPlayerId = lastPlayerId.equals(gameRecord.getPlayer1Id()) 
                        ? gameRecord.getPlayer2Id() 
                        : gameRecord.getPlayer1Id();
                    gameState.put("currentPlayer", currentPlayerId);
                    System.out.println("✅ [getGameStatus] 根据最后操作确定当前玩家: lastPlayerId=" + lastPlayerId + ", currentPlayerId=" + currentPlayerId);
                } else {
                    // 如果没有操作记录，需要从房间玩家信息中确定先手
                    // 优先使用turnOrder来确定先手
                    if (gameRecord.getRoomId() != null) {
                        LambdaQueryWrapper<RoomPlayer> playerWrapper = new LambdaQueryWrapper<>();
                        playerWrapper.eq(RoomPlayer::getRoomId, gameRecord.getRoomId())
                                    .orderByAsc(RoomPlayer::getId);
                        List<RoomPlayer> roomPlayers = roomPlayerMapper.selectList(playerWrapper);
                        
                        Long firstPlayerId = null;
                        if (roomPlayers.size() >= 2) {
                            Integer player1TurnOrder = roomPlayers.get(0).getTurnOrder();
                            Integer player2TurnOrder = roomPlayers.get(1).getTurnOrder();
                            
                            if (player1TurnOrder != null && player1TurnOrder == 1) {
                                firstPlayerId = roomPlayers.get(0).getUserId();
                            } else if (player2TurnOrder != null && player2TurnOrder == 1) {
                                firstPlayerId = roomPlayers.get(1).getUserId();
                            } else {
                                // 如果都没有选择先手，按玩家ID顺序（较小的先手）
                                firstPlayerId = roomPlayers.get(0).getUserId() < roomPlayers.get(1).getUserId() 
                                    ? roomPlayers.get(0).getUserId() 
                                    : roomPlayers.get(1).getUserId();
                            }
                        } else {
                            // 如果只有1个玩家，默认玩家1先手
                            firstPlayerId = gameRecord.getPlayer1Id();
                        }
                        
                        gameState.put("currentPlayer", firstPlayerId);
                        System.out.println("✅ [getGameStatus] 无操作记录，确定先手玩家: " + firstPlayerId);
                    } else {
                        // 如果没有房间ID，默认玩家1先手
                        gameState.put("currentPlayer", gameRecord.getPlayer1Id());
                    }
                }
            }
            
            // 添加最后活跃时间
            gameState.put("lastActiveTime", System.currentTimeMillis());
            // 初始化离线状态Map
            Map<String, Boolean> playerOffline = (Map<String, Boolean>) gameState.get("playerOffline");
            if (playerOffline == null) {
                playerOffline = new HashMap<>();
                playerOffline.put(String.valueOf(gameRecord.getPlayer1Id()), false);
                playerOffline.put(String.valueOf(gameRecord.getPlayer2Id()), false);
                gameState.put("playerOffline", playerOffline);
            }
            // 初始化放弃状态Map
            Map<String, Boolean> playerGiveUp = (Map<String, Boolean>) gameState.get("playerGiveUp");
            if (playerGiveUp == null) {
                playerGiveUp = new HashMap<>();
                playerGiveUp.put(String.valueOf(gameRecord.getPlayer1Id()), false);
                playerGiveUp.put(String.valueOf(gameRecord.getPlayer2Id()), false);
                gameState.put("playerGiveUp", playerGiveUp);
            }
            // 将恢复的状态保存回Redis（避免下次再查数据库），延长到24小时
            redisTemplate.opsForValue().set(gameKey, gameState, 24, TimeUnit.HOURS);
        }
        
        // 获取历史记录（猜数字模式）
        LambdaQueryWrapper<GameAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GameAction::getGameId, gameId)
                .orderByAsc(GameAction::getRoundNumber);
        List<GameAction> actions = gameActionMapper.selectList(wrapper);
        
        // 获取玩家信息映射（用于填充playerName）
        // 从数据库获取最新的用户信息，确保昵称是最新的
        List<Map<String, Object>> players = (List<Map<String, Object>>) gameState.get("players");
        Map<Long, String> playerNameMap = new HashMap<>();
        if (players != null) {
            for (Map<String, Object> player : players) {
                Long playerId = ((Number) player.get("id")).longValue();
                // 从数据库获取最新的用户信息（确保昵称是最新的）
                User user = userMapper.selectById(playerId);
                if (user != null && user.getNickname() != null) {
                    String latestNickname = user.getNickname();
                    playerNameMap.put(playerId, latestNickname);
                    // 同时更新游戏状态中的玩家昵称（确保一致性）
                    player.put("nickname", latestNickname);
                } else {
                    // 如果数据库中没有，使用游戏状态中的昵称
                    String nickname = (String) player.get("nickname");
                    if (nickname != null) {
                        playerNameMap.put(playerId, nickname);
                    }
                }
            }
            // 更新游戏状态中的玩家列表（包含最新昵称）
            gameState.put("players", players);
        }
        
        List<Map<String, Object>> history = actions.stream().map(action -> {
            Map<String, Object> item = new HashMap<>();
            item.put("round", action.getRoundNumber());
            item.put("playerId", action.getPlayerId());
            item.put("playerName", playerNameMap.get(action.getPlayerId()));
            item.put("guess", action.getGuess() != null ? action.getGuess() : "");
            Map<String, Integer> result = new HashMap<>();
            result.put("a", action.getResultA() != null ? action.getResultA() : 0);
            result.put("b", action.getResultB() != null ? action.getResultB() : 0);
            item.put("result", result);
            item.put("isWin", action.getIsWin() != null ? action.getIsWin() : false);
            item.put("timestamp", action.getCreatedAt() != null ? 
                action.getCreatedAt().toEpochSecond(java.time.ZoneOffset.of("+8")) * 1000 : System.currentTimeMillis());
            return item;
        }).collect(java.util.stream.Collectors.toList());
        
        gameState.put("history", history);
        
        // 添加当前用户ID（用于前端识别）
        gameState.put("currentUserId", userId);
        
        // 添加当前用户的数字（用于前端显示，增加紧张感）
        Map<String, String> playerSecrets = (Map<String, String>) gameState.get("playerSecrets");
        if (playerSecrets != null) {
            String mySecretNumber = playerSecrets.get(String.valueOf(userId));
            if (mySecretNumber != null) {
                gameState.put("mySecretNumber", mySecretNumber);
            }
        }
        
        return gameState;
    }
    
    @Transactional
    public Map<String, Object> endGame(Long gameId, Long loserId, Long userId) {
        GameRecord gameRecord = gameRecordMapper.selectById(gameId);
        if (gameRecord == null) {
            throw new RuntimeException("游戏记录不存在");
        }
        
        Long winnerId = gameRecord.getPlayer1Id().equals(loserId) 
            ? gameRecord.getPlayer2Id() 
            : gameRecord.getPlayer1Id();
        
        gameRecord.setWinnerId(winnerId);
        gameRecord.setLoserId(loserId);
        gameRecord.setEndedAt(LocalDateTime.now());
        gameRecordMapper.updateById(gameRecord);
        
        // 更新用户统计
        updateUserStats(winnerId, true);
        updateUserStats(loserId, false);
        
        // 生成惩罚
        String punishment = generatePunishment();
        gameRecord.setPunishmentContent(punishment);
        gameRecordMapper.updateById(gameRecord);
        
        // 更新房间状态
        if (gameRecord.getRoomId() != null) {
            Room room = roomMapper.selectById(gameRecord.getRoomId());
            room.setStatus(2); // 已结束
            room.setWinnerId(winnerId);
            roomMapper.updateById(room);
        }
        
        // 获取游戏状态以获取玩家数字
        String gameKey = "game:" + gameId;
        Map<String, Object> gameState = (Map<String, Object>) redisTemplate.opsForValue().get(gameKey);
        
        Map<String, Object> result = new HashMap<>();
        result.put("punishment", punishment);
        result.put("winner", getUserInfo(winnerId));
        result.put("loser", getUserInfo(loserId));
        
        // 猜数字模式：返回获胜者和失败者的数字
        if (gameState != null) {
            Map<String, String> playerSecrets = (Map<String, String>) gameState.get("playerSecrets");
            if (playerSecrets != null) {
                String winnerSecret = playerSecrets.get(String.valueOf(winnerId));
                String loserSecret = playerSecrets.get(String.valueOf(loserId));
                result.put("winnerSecret", winnerSecret);
                result.put("loserSecret", loserSecret);
            }
        } else {
            // 如果Redis已清除，从数据库获取
            if (gameRecord.getPlayer1Secret() != null && gameRecord.getPlayer2Secret() != null) {
                String winnerSecret = winnerId.equals(gameRecord.getPlayer1Id()) 
                    ? gameRecord.getPlayer1Secret() 
                    : gameRecord.getPlayer2Secret();
                String loserSecret = loserId.equals(gameRecord.getPlayer1Id()) 
                    ? gameRecord.getPlayer1Secret() 
                    : gameRecord.getPlayer2Secret();
                result.put("winnerSecret", winnerSecret);
                result.put("loserSecret", loserSecret);
            }
        }
        
        // 通过WebSocket通知游戏结束（传入loserId，确保两个玩家都能收到）
        Map<String, Object> gameResult = new HashMap<>();
        gameResult.put("winnerId", winnerId);
        gameResult.put("loserId", loserId);
        gameResult.put("punishment", punishment);
        if (result.containsKey("winnerSecret")) {
            gameResult.put("winnerSecret", result.get("winnerSecret"));
        }
        if (result.containsKey("loserSecret")) {
            gameResult.put("loserSecret", result.get("loserSecret"));
        }
        webSocketService.notifyGameEnded(gameId, winnerId, loserId, gameResult);
        
        // 不立即删除Redis，而是更新状态为"ended"并保留24小时，避免查询失败
        if (gameState != null) {
            gameState.put("status", "ended");
            gameState.put("winnerId", winnerId);
            gameState.put("loserId", loserId);
            gameState.put("lastActiveTime", System.currentTimeMillis());
            // 更新Redis，保留24小时
            redisTemplate.opsForValue().set(gameKey, gameState, 24, TimeUnit.HOURS);
        } else {
            // 如果Redis中没有，创建新的状态
            gameState = new HashMap<>();
            gameState.put("gameId", gameId);
            gameState.put("status", "ended");
            gameState.put("winnerId", winnerId);
            gameState.put("loserId", loserId);
            gameState.put("lastActiveTime", System.currentTimeMillis());
            redisTemplate.opsForValue().set(gameKey, gameState, 24, TimeUnit.HOURS);
        }
        
        return result;
    }
    
    private int getRoundNumber(Long gameId) {
        LambdaQueryWrapper<GameAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GameAction::getGameId, gameId);
        return gameActionMapper.selectCount(wrapper).intValue();
    }
    
    private void updateUserStats(Long userId, boolean isWin) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setTotalGames(user.getTotalGames() + 1);
            if (isWin) {
                user.setWinGames(user.getWinGames() + 1);
                user.setCurrentStreak(user.getCurrentStreak() + 1);
                if (user.getCurrentStreak() > user.getMaxStreak()) {
                    user.setMaxStreak(user.getCurrentStreak());
                }
                // 增加积分
                user.setRankScore(user.getRankScore() + 10);
            } else {
                user.setCurrentStreak(0);
                // 减少积分
                user.setRankScore(Math.max(0, user.getRankScore() - 5));
            }
            
            // 更新段位
            if (user.getRankScore() < 1000) {
                user.setRankLevel(1); // 青铜
            } else if (user.getRankScore() < 2000) {
                user.setRankLevel(2); // 白银
            } else {
                user.setRankLevel(3); // 黄金
            }
            
            userMapper.updateById(user);
        }
    }
    
    private String generatePunishment() {
        List<String> punishments = Arrays.asList(
            "学三声狗叫",
            "做10个俯卧撑",
            "唱一首儿歌",
            "讲一个冷笑话",
            "模仿一种动物",
            "做10个深蹲",
            "说一个自己的糗事",
            "表演一个才艺"
        );
        return punishments.get(new Random().nextInt(punishments.size()));
    }
    
    private Map<String, Object> getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("nickname", user.getNickname());
        info.put("avatarUrl", user.getAvatarUrl());
        return info;
    }

    /**
     * 处理玩家回合超时
     * 当玩家在规定时间内没有提交猜测时调用
     */
    @Transactional
    public void handleTimeout(Long gameId, Long userId) {
        System.out.println("⏱️ [handleTimeout] 处理回合超时: gameId=" + gameId + ", userId=" + userId);

        String gameKey = "game:" + gameId;
        Map<String, Object> gameState = (Map<String, Object>) redisTemplate.opsForValue().get(gameKey);

        if (gameState == null) {
            System.out.println("⚠️ [handleTimeout] 游戏状态不存在: gameId=" + gameId);
            return;
        }

        // 检查游戏是否还在进行中
        String status = (String) gameState.get("status");
        if (!"playing".equals(status)) {
            System.out.println("⚠️ [handleTimeout] 游戏不在进行中，跳过超时处理: gameId=" + gameId + ", status=" + status);
            return;
        }

        // 安全地获取 currentPlayer（可能是 Long 或 Integer，Redis序列化可能导致类型变化）
        Object currentPlayerObj = gameState.get("currentPlayer");
        if (currentPlayerObj == null) {
            System.out.println("⚠️ [handleTimeout] 游戏状态异常：currentPlayer 为空");
            return;
        }
        
        Long currentPlayer = null;
        if (currentPlayerObj instanceof Long) {
            currentPlayer = (Long) currentPlayerObj;
        } else if (currentPlayerObj instanceof Integer) {
            currentPlayer = ((Integer) currentPlayerObj).longValue();
        } else if (currentPlayerObj instanceof Number) {
            currentPlayer = ((Number) currentPlayerObj).longValue();
        } else {
            System.out.println("⚠️ [handleTimeout] currentPlayer 类型错误: " + currentPlayerObj.getClass().getName());
            return;
        }

        Long userIdLong = userId.longValue();
        if (!currentPlayer.equals(userIdLong)) {
            System.out.println("⚠️ [handleTimeout] 不是当前玩家的回合，跳过超时处理: gameId=" + gameId + ", currentPlayer=" + currentPlayer + ", userId=" + userId);
            return;
        }

        // 获取游戏记录
        GameRecord gameRecord = gameRecordMapper.selectById(gameId);
        if (gameRecord == null) {
            System.out.println("❌ [handleTimeout] 游戏记录不存在: gameId=" + gameId);
            return;
        }

        // 获取对手ID
        Long opponentId = gameRecord.getPlayer1Id().equals(userId)
            ? gameRecord.getPlayer2Id()
            : gameRecord.getPlayer1Id();

        // 记录超时操作（作为一次空猜测）
        int roundNumber = getRoundNumber(gameId) + 1;
        GameAction timeoutAction = new GameAction();
        timeoutAction.setGameId(gameId);
        timeoutAction.setPlayerId(userId);
        timeoutAction.setRoundNumber(roundNumber);
        timeoutAction.setGuess("TIMEOUT"); // 标记为超时
        timeoutAction.setResultA(0);
        timeoutAction.setResultB(0);
        timeoutAction.setIsWin(false);
        timeoutAction.setCreatedAt(LocalDateTime.now());
        gameActionMapper.insert(timeoutAction);

        // 切换回合到对手
        gameState.put("currentPlayer", opponentId);
        gameState.put("countdown", 180); // 重置倒计时
        gameState.put("lastActiveTime", System.currentTimeMillis()); // 更新最后活跃时间

        // 更新Redis，延长到24小时
        redisTemplate.opsForValue().set(gameKey, gameState, 24, TimeUnit.HOURS);

        // 获取玩家名称
        String playerName = getUserInfo(userId).get("nickname").toString();

        // 广播回合切换消息
        webSocketService.notifyTurnChanged(gameId, opponentId, userId, 180);

        // 广播超时通知
        Map<String, Object> timeoutData = new HashMap<>();
        timeoutData.put("gameId", gameId);
        timeoutData.put("playerId", userId);
        timeoutData.put("playerName", playerName);
        timeoutData.put("message", playerName + " 回合超时，自动切换到对方回合");
        webSocketService.notifyTimeout(gameId, userId, timeoutData);

        System.out.println("✅ [handleTimeout] 回合超时处理完成: gameId=" + gameId + ", 切换到对手: " + opponentId);
    }

    /**
     * 获取游戏状态（用于重连后同步）
     */
    public Map<String, Object> getGameState(Long gameId, Long userId, Integer lastHistoryIndex) {
        // 查询游戏记录
        GameRecord gameRecord = gameRecordMapper.selectById(gameId);
        if (gameRecord == null) {
            return null;
        }

        Map<String, Object> state = new HashMap<>();
        state.put("gameId", gameId);
        boolean isEnded = gameRecord.getEndedAt() != null;
        state.put("status", isEnded ? "ended" : "playing");
        state.put("currentPlayer", isEnded ? null : getCurrentPlayer(gameId, gameRecord));
        state.put("countdown", 180);

        // 查询历史记录
        LambdaQueryWrapper<GameAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GameAction::getGameId, gameId)
                .orderByAsc(GameAction::getRoundNumber);
        List<GameAction> actions = gameActionMapper.selectList(wrapper);

        List<Map<String, Object>> history = actions.stream()
                .skip(lastHistoryIndex != null ? lastHistoryIndex : 0)
                .map(this::convertToHistoryItem)
                .collect(java.util.stream.Collectors.toList());
        state.put("history", history);

        // 游戏结束时，返回双方的数字
        if (isEnded) {
            Map<String, String> playerSecrets = new HashMap<>();
            playerSecrets.put(String.valueOf(gameRecord.getPlayer1Id()), gameRecord.getPlayer1Secret());
            playerSecrets.put(String.valueOf(gameRecord.getPlayer2Id()), gameRecord.getPlayer2Secret());
            state.put("playerSecrets", playerSecrets);
            state.put("winnerId", gameRecord.getWinnerId());
        }

        return state;
    }

    /**
     * 获取当前玩家
     */
    private Long getCurrentPlayer(Long gameId, GameRecord gameRecord) {
        LambdaQueryWrapper<GameAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GameAction::getGameId, gameId)
                .orderByDesc(GameAction::getRoundNumber)
                .last("LIMIT 1");
        GameAction lastAction = gameActionMapper.selectOne(wrapper);

        if (lastAction != null) {
            // 当前玩家应该是另一个玩家（因为上一个玩家已经操作过了）
            Long lastPlayerId = lastAction.getPlayerId();
            Long currentPlayerId = lastPlayerId.equals(gameRecord.getPlayer1Id())
                    ? gameRecord.getPlayer2Id()
                    : gameRecord.getPlayer1Id();
            return currentPlayerId;
        }
        
        // 如果没有操作记录，需要从房间玩家信息中确定先手
        if (gameRecord.getRoomId() != null) {
            LambdaQueryWrapper<RoomPlayer> playerWrapper = new LambdaQueryWrapper<>();
            playerWrapper.eq(RoomPlayer::getRoomId, gameRecord.getRoomId())
                        .orderByAsc(RoomPlayer::getId);
            List<RoomPlayer> roomPlayers = roomPlayerMapper.selectList(playerWrapper);
            
            if (roomPlayers.size() >= 2) {
                Integer player1TurnOrder = roomPlayers.get(0).getTurnOrder();
                Integer player2TurnOrder = roomPlayers.get(1).getTurnOrder();
                
                if (player1TurnOrder != null && player1TurnOrder == 1) {
                    return roomPlayers.get(0).getUserId();
                } else if (player2TurnOrder != null && player2TurnOrder == 1) {
                    return roomPlayers.get(1).getUserId();
                } else {
                    // 如果都没有选择先手，按玩家ID顺序（较小的先手）
                    return roomPlayers.get(0).getUserId() < roomPlayers.get(1).getUserId() 
                        ? roomPlayers.get(0).getUserId() 
                        : roomPlayers.get(1).getUserId();
                }
            }
        }
        
        // 默认返回玩家1
        return gameRecord.getPlayer1Id();
    }

    /**
     * 转换游戏动作为历史记录项
     */
    private Map<String, Object> convertToHistoryItem(GameAction action) {
        Map<String, Object> item = new HashMap<>();
        item.put("round", action.getRoundNumber());
        item.put("playerId", action.getPlayerId());
        item.put("guess", action.getGuess());
        Map<String, Integer> result = new HashMap<>();
        result.put("a", action.getResultA() != null ? action.getResultA() : 0);
        result.put("b", action.getResultB() != null ? action.getResultB() : 0);
        item.put("result", result);
        item.put("isWin", action.getIsWin() != null ? action.getIsWin() : false);
        item.put("timestamp", action.getCreatedAt() != null ?
                action.getCreatedAt().toEpochSecond(java.time.ZoneOffset.of("+8")) * 1000 : System.currentTimeMillis());
        return item;
    }

    /**
     * 玩家放弃游戏
     * @param gameId 游戏ID
     * @param userId 用户ID
     * @return 游戏结果
     */
    @Transactional
    public Map<String, Object> giveUpGame(Long gameId, Long userId) {
        System.out.println("🏳️ [giveUpGame] 玩家放弃游戏: gameId=" + gameId + ", userId=" + userId);

        // 验证游戏是否存在
        GameRecord gameRecord = gameRecordMapper.selectById(gameId);
        if (gameRecord == null) {
            System.out.println("❌ [giveUpGame] 游戏不存在: gameId=" + gameId);
            throw new RuntimeException("游戏不存在");
        }

        // 验证用户是否在游戏房间中
        if (!userId.equals(gameRecord.getPlayer1Id()) && !userId.equals(gameRecord.getPlayer2Id())) {
            System.out.println("❌ [giveUpGame] 用户不在该游戏中: userId=" + userId + ", player1=" + gameRecord.getPlayer1Id() + ", player2=" + gameRecord.getPlayer2Id());
            throw new RuntimeException("您不在该游戏中");
        }

        // 检查游戏是否已结束
        if (gameRecord.getEndedAt() != null) {
            System.out.println("❌ [giveUpGame] 游戏已结束: gameId=" + gameId);
            throw new RuntimeException("游戏已结束");
        }

        // 获取游戏状态
        String gameKey = "game:" + gameId;
        Map<String, Object> gameState = (Map<String, Object>) redisTemplate.opsForValue().get(gameKey);

        if (gameState == null) {
            System.out.println("🔄 [giveUpGame] Redis中无游戏状态，从数据库恢复: gameId=" + gameId);
            // 如果Redis中没有，从数据库恢复
            gameState = restoreGameStateFromDb(gameId, userId);
        }

        // 检查游戏状态
        String status = (String) gameState.get("status");
        if (!"playing".equals(status)) {
            System.out.println("❌ [giveUpGame] 游戏不在进行中: gameId=" + gameId + ", status=" + status);
            throw new RuntimeException("游戏不在进行中");
        }

        // 标记玩家为已放弃
        Map<String, Boolean> playerGiveUp = (Map<String, Boolean>) gameState.get("playerGiveUp");
        if (playerGiveUp == null) {
            playerGiveUp = new HashMap<>();
        }
        playerGiveUp.put(String.valueOf(userId), true);
        gameState.put("playerGiveUp", playerGiveUp);
        gameState.put("lastActiveTime", System.currentTimeMillis());
        System.out.println("✅ [giveUpGame] 玩家标记为已放弃: userId=" + userId);

        // 获取对手ID
        Long opponentId = userId.equals(gameRecord.getPlayer1Id())
                ? gameRecord.getPlayer2Id()
                : gameRecord.getPlayer1Id();
        System.out.println("✅ [giveUpGame] 对手ID: opponentId=" + opponentId);

        // 检查对手是否已放弃或离线超2分钟
        boolean shouldEndGame = false;
        String endReason = null;

        // 检查对手是否已放弃
        Boolean opponentGiveUp = playerGiveUp.get(String.valueOf(opponentId));
        if (opponentGiveUp != null && opponentGiveUp) {
            shouldEndGame = true;
            endReason = "双方都已放弃游戏";
            System.out.println("🏳️ [giveUpGame] 对手也已放弃，准备结束游戏");
        }

        // 检查对手是否离线（如果对手已离线，直接结束游戏）
        if (!shouldEndGame) {
            Map<String, Boolean> playerOffline = (Map<String, Boolean>) gameState.get("playerOffline");
            if (playerOffline != null) {
                Boolean opponentOffline = playerOffline.get(String.valueOf(opponentId));
                if (opponentOffline != null && opponentOffline) {
                    // 如果对手已标记为离线，且当前玩家放弃，则直接结束游戏
                    // 注意：这里不再检查2分钟，因为如果对手已离线，就应该直接结束游戏
                    shouldEndGame = true;
                    endReason = "对方已离线";
                    System.out.println("🏳️ [giveUpGame] 对手已离线，准备结束游戏");
                }
            }
        }

        // 保存游戏状态
        redisTemplate.opsForValue().set(gameKey, gameState, 24, TimeUnit.HOURS);
        System.out.println("✅ [giveUpGame] 游戏状态已保存到Redis");

        // 广播放弃消息给房间内所有玩家
        System.out.println("📤 [giveUpGame] 发送放弃游戏通知: gameId=" + gameId + ", playerId=" + userId + ", opponentId=" + opponentId);
        webSocketService.notifyPlayerGiveUp(gameId, userId, opponentId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已放弃游戏");
        result.put("gameId", gameId);
        result.put("playerId", userId);
        result.put("opponentId", opponentId);

        // 如果需要结束游戏
        if (shouldEndGame) {
            System.out.println("🏳️ [giveUpGame] 结束游戏: " + endReason);

            // 确定获胜者（未放弃的一方获胜，如果双方都放弃则对方获胜）
            Long winnerId = opponentId;
            Long loserId = userId;

            // 更新游戏记录
            gameRecord.setWinnerId(winnerId);
            gameRecord.setLoserId(loserId);
            gameRecord.setEndedAt(LocalDateTime.now());
            gameRecordMapper.updateById(gameRecord);
            System.out.println("✅ [giveUpGame] 游戏记录已更新: winnerId=" + winnerId + ", loserId=" + loserId);

            // 更新房间状态
            if (gameRecord.getRoomId() != null) {
                Room room = roomMapper.selectById(gameRecord.getRoomId());
                if (room != null) {
                    room.setStatus(2); // 已结束
                    room.setWinnerId(winnerId);
                    roomMapper.updateById(room);
                    System.out.println("✅ [giveUpGame] 房间状态已更新: roomId=" + gameRecord.getRoomId());
                }
            }

            // 更新用户统计
            updateUserStats(winnerId, true);
            updateUserStats(loserId, false);

            // 更新游戏状态
            gameState.put("status", "ended");
            gameState.put("winnerId", winnerId);
            gameState.put("loserId", loserId);
            gameState.put("endReason", endReason);
            redisTemplate.opsForValue().set(gameKey, gameState, 24, TimeUnit.HOURS);

            // 广播游戏结束给房间内所有玩家
            System.out.println("📤 [giveUpGame] 发送游戏结束通知: gameId=" + gameId + ", winnerId=" + winnerId + ", loserId=" + loserId);
            Map<String, Object> gameResult = new HashMap<>();
            gameResult.put("winnerId", winnerId);
            gameResult.put("loserId", loserId);
            gameResult.put("endReason", endReason);
            gameResult.put("giveUpPlayerId", userId);
            webSocketService.notifyGameEnded(gameId, winnerId, loserId, gameResult);

            result.put("gameEnded", true);
            result.put("winnerId", winnerId);
            result.put("loserId", loserId);
            result.put("endReason", endReason);

            System.out.println("✅ [giveUpGame] 游戏已结束: gameId=" + gameId + ", winnerId=" + winnerId + ", reason=" + endReason);
        } else {
            result.put("gameEnded", false);
            result.put("message", "已放弃游戏，等待对方响应");
            System.out.println("✅ [giveUpGame] 玩家已放弃，等待对方响应: gameId=" + gameId + ", userId=" + userId);
        }

        return result;
    }
}
