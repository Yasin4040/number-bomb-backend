-- 游戏房间表（支持普通对战和语音对战）
CREATE TABLE IF NOT EXISTS `voice_room` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `room_number` VARCHAR(6) NOT NULL COMMENT '房间号（6位数字）',
    `creator_id` VARCHAR(64) NOT NULL COMMENT '创建者ID',
    `creator_number` VARCHAR(4) DEFAULT NULL COMMENT '创建者数字',
    `opponent_id` VARCHAR(64) DEFAULT NULL COMMENT '对手ID',
    `opponent_number` VARCHAR(4) DEFAULT NULL COMMENT '对手数字',
    `status` VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT '房间状态：WAITING-等待中，PLAYING-游戏中，ENDED-已结束',
    `winner_id` VARCHAR(64) DEFAULT NULL COMMENT '获胜者ID',
    `room_type` INT NOT NULL DEFAULT 1 COMMENT '房间类型：1-普通对战，2-语音对战',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
    `ended_at` DATETIME DEFAULT NULL COMMENT '结束时间',
    `duration` INT DEFAULT NULL COMMENT '游戏时长（秒）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_number` (`room_number`),
    KEY `idx_creator_id` (`creator_id`),
    KEY `idx_opponent_id` (`opponent_id`),
    KEY `idx_status` (`status`),
    KEY `idx_room_type` (`room_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏房间表';

-- 语音对战猜测记录表
CREATE TABLE IF NOT EXISTS `voice_guess_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `room_id` BIGINT NOT NULL COMMENT '房间ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '猜测者ID',
    `round` INT NOT NULL COMMENT '轮次',
    `guess_number` VARCHAR(4) NOT NULL COMMENT '猜测的数字',
    `result_a` INT NOT NULL COMMENT '结果（几个A）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_room_id` (`room_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_room_user` (`room_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='语音对战猜测记录表';
