package com.numberbomb.utils;

import java.security.MessageDigest;
import java.util.Random;

/**
 * 临时用户工具类
 * 用于生成和管理未登录用户的临时标识
 */
public class TempUserUtil {
    
    /**
     * 生成临时用户ID
     * 格式：temp_{hash}_{timestamp}_{random}
     */
    public static String generateTempUserId(String clientIp) {
        long timestamp = System.currentTimeMillis();
        int random = new Random().nextInt(10000);
        
        // 使用IP+时间戳+随机数生成唯一标识
        String input = clientIp + "_" + timestamp + "_" + random;
        String hash = md5(input).substring(0, 8);
        
        return "temp_" + hash + "_" + timestamp + "_" + random;
    }
    
    /**
     * 从临时用户ID生成昵称
     * 确保不同会话生成不同的昵称
     */
    public static String generateTempNickname(String tempUserId) {
        // 提取hash部分和时间戳，生成友好的昵称
        if (tempUserId.startsWith("temp_")) {
            String[] parts = tempUserId.split("_");
            if (parts.length >= 3) {
                String hash = parts[1];
                String timestamp = parts[2];
                String random = parts.length > 3 ? parts[3] : "0";
                
                // 使用hash + timestamp + random的组合，确保唯一性
                // 将hash转换为数字
                long hashNum = Long.parseLong(hash.substring(0, Math.min(8, hash.length())), 16);
                
                // 使用时间戳的后4位
                long timestampNum = 0;
                try {
                    if (timestamp.length() >= 4) {
                        timestampNum = Long.parseLong(timestamp.substring(timestamp.length() - 4));
                    } else {
                        timestampNum = Long.parseLong(timestamp);
                    }
                } catch (NumberFormatException e) {
                    timestampNum = timestamp.hashCode() % 10000;
                }
                
                // 使用随机数部分
                int randomNum = 0;
                try {
                    randomNum = Integer.parseInt(random) % 1000;
                } catch (NumberFormatException e) {
                    randomNum = random.hashCode() % 1000;
                }
                
                // 组合生成唯一数字（0-99999）
                int num = (int) ((hashNum + timestampNum + randomNum) % 99999);
                // 确保至少是4位数，更友好
                if (num < 1000) {
                    num = num + 1000;
                }
                
                return "游客" + num;
            }
        }
        // 如果格式不对，使用随机数
        return "游客" + (new Random().nextInt(90000) + 1000);
    }
    
    /**
     * 检查是否为临时用户ID
     */
    public static boolean isTempUserId(String userId) {
        return userId != null && userId.startsWith("temp_");
    }
    
    /**
     * MD5加密
     */
    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
