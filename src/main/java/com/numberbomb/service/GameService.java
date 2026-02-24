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
    
    @Transactional
    public Map<String, Object> startGame(Long roomId, Long userId) {
        System.out.println("🎮 开始游戏: roomId=" + roomId + ", userId=" + userId);
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
        
        // 检查房间是否已经有游戏记录（且游戏未结束）
        LambdaQueryWrapper<GameRecord> gameWrapper = new LambdaQueryWrapper<>();
        gameWrapper.eq(GameRecord::getRoomId, roomId)
                   .eq(GameRecord::getGameType, 2) // 在线房间
                   .isNull(GameRecord::getEndedAt); // 游戏未结束
        GameRecord existingGame = gameRecordMapper.selectOne(gameWrapper);
        
        GameRecord gameRecord;
        if (existingGame != null) {
            // 如果已经有游戏记录，使用现有的
            gameRecord = existingGame;
            System.out.println("✅ 房间已有游戏记录，使用现有游戏: gameId=" + gameRecord.getId());
        } else {
            // 如果房间状态是0（等待中），更新为1（游戏中）
            if (room.getStatus() == 0) {
                room.setStatus(1);
                roomMapper.updateById(room);
                System.out.println("✅ 更新房间状态为游戏中: " + roomId);
            }
            
            // 创建新的游戏记录（猜数字模式）
            gameRecord = new GameRecord();
            gameRecord.setRoomId(roomId);
            gameRecord.setGameType(2); // 在线房间
            gameRecord.setStartedAt(LocalDateTime.now());
            gameRecord.setPlayer1Id(players.get(0).getUserId());
            gameRecord.setPlayer2Id(players.get(1).getUserId());
            gameRecord.setPlayer1Secret(players.get(0).getSecretNumber());
            gameRecord.setPlayer2Secret(players.get(1).getSecretNumber());
            gameRecordMapper.insert(gameRecord);
            System.out.println("✅ 创建新的游戏记录: gameId=" + gameRecord.getId());
        }
        
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
        
        // 检查Redis中是否已有游戏状态
        String gameKey = "game:" + gameRecord.getId();
        Map<String, Object> gameState = (Map<String, Object>) redisTemplate.opsForValue().get(gameKey);
        
        if (gameState != null && existingGame != null) {
            // 如果使用现有游戏，从Redis获取游戏状态，但更新 currentUserId
            System.out.println("✅ 从Redis获取现有游戏状态: gameId=" + gameRecord.getId());
            gameState.put("currentUserId", userId); // 更新当前用户ID
            // 确保玩家列表是最新的
            gameState.put("players", playerList);
            // 更新Redis
            redisTemplate.opsForValue().set(gameKey, gameState, 1, TimeUnit.HOURS);
        } else {
            // 确定先手玩家：根据玩家选择的先手/后手
            Long firstPlayerId;
            Integer player1TurnOrder = players.get(0).getTurnOrder();
            Integer player2TurnOrder = players.get(1).getTurnOrder();
            
            if (player1TurnOrder != null && player2TurnOrder != null) {
                // 如果两个玩家都选择了先手/后手，选择先手（turnOrder=1）的玩家
                if (player1TurnOrder == 1) {
                    firstPlayerId = players.get(0).getUserId();
                } else if (player2TurnOrder == 1) {
                    firstPlayerId = players.get(1).getUserId();
                } else {
                    // 如果都没有选择先手（不应该发生），按玩家ID顺序
                    System.out.println("⚠️ 警告：两个玩家都没有选择先手，按ID顺序选择");
                    Long player1Id = players.get(0).getUserId();
                    Long player2Id = players.get(1).getUserId();
                    firstPlayerId = player1Id < player2Id ? player1Id : player2Id;
                }
                System.out.println("✅ 根据先手/后手选择确定先手玩家: " + firstPlayerId + 
                    " (玩家1 turnOrder=" + player1TurnOrder + ", 玩家2 turnOrder=" + player2TurnOrder + ")");
            } else {
                // 如果玩家还没有选择先手/后手，按照玩家ID顺序（较小的先手），确保每次选择一致
                Long player1Id = players.get(0).getUserId();
                Long player2Id = players.get(1).getUserId();
                // 使用确定性的规则：玩家ID较小的先手
                firstPlayerId = player1Id < player2Id ? player1Id : player2Id;
                System.out.println("✅ 玩家未选择先手/后手，按玩家ID顺序选择先手: " + firstPlayerId + " (玩家1: " + player1Id + ", 玩家2: " + player2Id + ")");
            }
            
            // 创建新的游戏状态
            gameState = new HashMap<>();
            gameState.put("gameId", gameRecord.getId());
            gameState.put("gameType", "online");
            gameState.put("status", "playing");
            gameState.put("currentPlayer", firstPlayerId); // 根据准备顺序或随机选择先手
            gameState.put("countdown", 180); // 3分钟 = 180秒
            gameState.put("players", playerList);
            gameState.put("history", new ArrayList<>());
            gameState.put("playerSecrets", playerSecrets);
            // 添加当前用户ID（用于前端识别）
            gameState.put("currentUserId", userId);
            
            // 保存到Redis
            redisTemplate.opsForValue().set(gameKey, gameState, 1, TimeUnit.HOURS);
            System.out.println("✅ 创建新的游戏状态: gameId=" + gameRecord.getId());
        }
        
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
            Integer countdown = (Integer) gameState.get("countdown");
            if (countdown == null) {
                countdown = 180;
            }
            webSocketService.notifyTurnChanged(gameRecord.getId(), currentPlayerId, countdown);
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
        // 验证猜测格式（4位数字，允许重复，允许0开头）
        if (guess == null || guess.length() != 4 || !guess.matches("^\\d{4}$")) {
            throw new RuntimeException("请输入4位数字");
        }
        
        // 从Redis获取游戏状态
        String gameKey = "game:" + gameId;
        Map<String, Object> gameState = (Map<String, Object>) redisTemplate.opsForValue().get(gameKey);
        
        if (gameState == null) {
            throw new RuntimeException("游戏不存在或已结束");
        }
        
        String status = (String) gameState.get("status");
        if (!"playing".equals(status)) {
            throw new RuntimeException("游戏已结束");
        }
        
        // 安全地获取 currentPlayer（可能是 Long 或 Integer，Redis序列化可能导致类型变化）
        Object currentPlayerObj = gameState.get("currentPlayer");
        if (currentPlayerObj == null) {
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
            System.out.println("❌ currentPlayer 类型错误: " + currentPlayerObj.getClass().getName() + ", 值: " + currentPlayerObj);
            throw new RuntimeException("游戏状态异常：currentPlayer 类型错误: " + currentPlayerObj.getClass().getName());
        }
        
        // 确保 userId 也是 Long 类型进行比较
        Long userIdLong = userId.longValue();
        
        // 使用 longValue() 进行比较，避免类型问题
        if (!currentPlayer.equals(userIdLong)) {
            System.out.println("❌ 回合检查失败:");
            System.out.println("   currentPlayer=" + currentPlayer + " (类型: " + currentPlayer.getClass().getName() + ")");
            System.out.println("   userId=" + userId + " (类型: " + userId.getClass().getName() + ")");
            System.out.println("   userIdLong=" + userIdLong + " (类型: " + userIdLong.getClass().getName() + ")");
            System.out.println("   比较结果: currentPlayer.equals(userId)=" + currentPlayer.equals(userId));
            System.out.println("   比较结果: currentPlayer.equals(userIdLong)=" + currentPlayer.equals(userIdLong));
            System.out.println("   游戏状态: " + gameState);
            throw new RuntimeException("不是您的回合，当前回合玩家ID: " + currentPlayer + ", 您的ID: " + userId);
        }
        
        System.out.println("✅ 回合检查通过: currentPlayer=" + currentPlayer + ", userId=" + userId);
        
        // 获取对手的数字（要猜的数字）
        Map<String, String> playerSecrets = (Map<String, String>) gameState.get("playerSecrets");
        if (playerSecrets == null) {
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
            System.out.println("❌ 找不到对手: userId=" + userId + ", players=" + players);
            throw new RuntimeException("找不到对手");
        }
        
        System.out.println("✅ 找到对手: opponentId=" + opponentId + ", userId=" + userId);
        
        // 获取对手的数字
        String secretNumber = playerSecrets.get(String.valueOf(opponentId));
        if (secretNumber == null || secretNumber.isEmpty()) {
            throw new RuntimeException("对手数字不存在");
        }
        
        // 计算AB结果
        Map<String, Integer> abResult = calculateAB(secretNumber, guess);
        int a = abResult.get("a");
        int b = abResult.get("b");
        
        // 判断是否获胜（4A0B）
        boolean isWin = (a == 4 && b == 0);
        boolean isGameOver = isWin;
        
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
        }
        
        // 更新Redis
        redisTemplate.opsForValue().set(gameKey, gameState, 1, TimeUnit.HOURS);
        
        // 通过WebSocket通知猜测结果
        webSocketService.notifyGuessResult(gameId, userId, guess, a, b, isGameOver);
        
        if (isGameOver) {
            // 游戏结束，通知所有玩家
            Map<String, Object> gameResult = new HashMap<>();
            gameResult.put("winnerId", userId);
            gameResult.put("loserId", opponentId);
            webSocketService.notifyGameEnded(gameId, userId, gameResult);
        } else {
            // 回合切换，通知新回合
            Object nextPlayerObj = gameState.get("currentPlayer");
            Long nextPlayerId = null;
            if (nextPlayerObj instanceof Long) {
                nextPlayerId = (Long) nextPlayerObj;
            } else if (nextPlayerObj instanceof Integer) {
                nextPlayerId = ((Integer) nextPlayerObj).longValue();
            } else if (nextPlayerObj instanceof Number) {
                nextPlayerId = ((Number) nextPlayerObj).longValue();
            }
            if (nextPlayerId != null) {
                Integer countdown = (Integer) gameState.get("countdown");
                if (countdown == null) {
                    countdown = 180;
                }
                webSocketService.notifyTurnChanged(gameId, nextPlayerId, countdown);
            }
        }
        
        // 构建响应
        Map<String, Object> response = new HashMap<>();
        Map<String, Integer> result = new HashMap<>();
        result.put("a", a);
        result.put("b", b);
        response.put("result", result);
        response.put("nextPlayer", gameState.get("currentPlayer"));
        response.put("isGameOver", isGameOver);
        
        System.out.println("✅ 猜测结果: userId=" + userId + ", guess=" + guess + ", result=" + a + "A" + b + "B, isWin=" + isWin);
        
        return response;
    }
    
    public Map<String, Object> getGameStatus(Long gameId, Long userId) {
        String gameKey = "game:" + gameId;
        Map<String, Object> gameState = (Map<String, Object>) redisTemplate.opsForValue().get(gameKey);
        
        if (gameState == null) {
            throw new RuntimeException("游戏不存在");
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
        
        // 通过WebSocket通知游戏结束
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
        webSocketService.notifyGameEnded(gameId, winnerId, gameResult);
        
        // 清除Redis
        redisTemplate.delete(gameKey);
        
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
}
