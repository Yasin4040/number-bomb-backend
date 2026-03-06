-- 为 rooms 表添加房间类型字段
ALTER TABLE `rooms` 
ADD COLUMN `room_type` INT NOT NULL DEFAULT 1 COMMENT '房间类型：1-普通对战，2-语音对战' AFTER `winner_id`,
ADD INDEX `idx_room_type` (`room_type`);

-- 更新现有数据，默认为普通对战
UPDATE `rooms` SET `room_type` = 1 WHERE `room_type` IS NULL;
