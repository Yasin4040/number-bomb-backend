# 数据库迁移说明

## 用户表添加账号密码登录支持

### users 表变更

```sql
-- 添加 username 字段
ALTER TABLE users 
ADD COLUMN username VARCHAR(20) UNIQUE COMMENT '登录用户名';

-- 添加 password 字段
ALTER TABLE users 
ADD COLUMN password VARCHAR(64) COMMENT '密码（MD5加密）';

-- 修改 openid 字段允许为空（账号密码登录不需要 openid）
ALTER TABLE users 
MODIFY COLUMN openid VARCHAR(64) NULL COMMENT '微信openid';

-- 为 username 添加索引
CREATE INDEX idx_users_username ON users(username);
```

## 从数字炸弹模式迁移到猜数字模式

### 1. GameRecord 表 (game_records)

#### 新增字段
```sql
ALTER TABLE game_records 
ADD COLUMN player1_secret VARCHAR(4) COMMENT '玩家1的4位数字',
ADD COLUMN player2_secret VARCHAR(4) COMMENT '玩家2的4位数字';
```

#### 废弃字段（可选删除）
```sql
-- 以下字段已不再使用，可以保留或删除
-- ALTER TABLE game_records DROP COLUMN bomb_number;
-- ALTER TABLE game_records DROP COLUMN min_range;
-- ALTER TABLE game_records DROP COLUMN max_range;
```

### 2. GameAction 表 (game_actions)

#### 新增字段
```sql
ALTER TABLE game_actions 
ADD COLUMN guess VARCHAR(4) COMMENT '猜测的4位数字',
ADD COLUMN result_a INT COMMENT 'A值（数字和位置都对的数量）',
ADD COLUMN result_b INT COMMENT 'B值（数字对但位置错的数量）',
ADD COLUMN is_win TINYINT(1) COMMENT '是否获胜（4A0B）';
```

#### 废弃字段（可选删除）
```sql
-- 以下字段已不再使用，可以保留或删除
-- ALTER TABLE game_actions DROP COLUMN guess_number;
-- ALTER TABLE game_actions DROP COLUMN result;
-- ALTER TABLE game_actions DROP COLUMN current_min;
-- ALTER TABLE game_actions DROP COLUMN current_max;
```

### 3. 数据迁移（如果需要保留历史数据）

如果数据库中有旧的游戏记录，需要决定：
1. **保留旧数据**：不删除废弃字段，新游戏使用新字段
2. **清空旧数据**：删除所有旧记录，只保留新格式的数据

### 4. 索引建议

```sql
-- 为常用查询字段添加索引
CREATE INDEX idx_game_records_room_id ON game_records(room_id);
CREATE INDEX idx_game_records_player1_id ON game_records(player1_id);
CREATE INDEX idx_game_records_player2_id ON game_records(player2_id);
CREATE INDEX idx_game_actions_game_id ON game_actions(game_id);
CREATE INDEX idx_game_actions_player_id ON game_actions(player_id);
```

### 5. 验证

迁移后，请验证：
1. 新游戏可以正常创建和保存
2. 猜测记录可以正常保存
3. 游戏历史可以正常查询
4. 游戏结束可以正常保存结果
