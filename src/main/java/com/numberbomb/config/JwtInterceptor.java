package com.numberbomb.config;

import com.numberbomb.utils.JwtUtil;
import com.numberbomb.utils.TempUserUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {
    
    private final JwtUtil jwtUtil;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 预检请求直接放行
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        
        // 公开接口放行
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/") || path.startsWith("/ws/")) {
            return true;
        }
        
        // 房间和游戏相关接口允许未登录访问（使用临时用户）
        // 使用startsWith匹配，更灵活
        boolean isRoomApi = path.startsWith("/api/room/");
        boolean isGameApi = path.startsWith("/api/game/");
        
        if (isRoomApi || isGameApi) {
            // 尝试获取token
            String authHeader = request.getHeader("Authorization");
            Long userId = null;
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    if (jwtUtil.validateToken(token)) {
                        userId = jwtUtil.getUserIdFromToken(token);
                    }
                } catch (Exception e) {
                    // Token无效，忽略，继续使用临时用户逻辑
                    userId = null;
                }
            }
            
            // 如果没有有效的userId，使用临时用户
            if (userId == null) {
                // 优先检查 X-User-Id（前端发送的header名称）
                String tempUserId = request.getHeader("X-User-Id");
                // 兼容旧的 header 名称
                if (tempUserId == null || tempUserId.isEmpty()) {
                    tempUserId = request.getHeader("X-Temp-User-Id");
                }
                
                if (tempUserId != null && !tempUserId.isEmpty()) {
                    // 支持 guest_ 和 temp_ 两种格式
                    if (tempUserId.startsWith("guest_") || tempUserId.startsWith("temp_")) {
                        // 使用前端传来的临时用户ID
                        request.setAttribute("tempUserId", tempUserId);
                        request.setAttribute("userId", null); // 标记为临时用户
                        System.out.println("✅ [JwtInterceptor] 使用前端传来的临时用户ID: " + tempUserId + ", URL: " + path);
                    } else {
                        // 如果不是标准格式，仍然使用，但记录警告
                        request.setAttribute("tempUserId", tempUserId);
                        request.setAttribute("userId", null);
                        System.out.println("⚠️ [JwtInterceptor] 收到非标准格式的临时用户ID: " + tempUserId + ", URL: " + path);
                    }
                } else {
                    // 如果没有临时用户ID，返回错误（不再自动生成，强制前端提供）
                    System.out.println("❌ [JwtInterceptor] 未提供临时用户ID，请求将被拒绝, URL: " + path);
                    // 注意：这里不返回false，让Controller处理错误
                }
                
                // 处理临时昵称（检查 X-Guest-Nickname 和 X-Temp-Nickname）
                String tempNickname = request.getHeader("X-Guest-Nickname");
                if (tempNickname == null || tempNickname.isEmpty()) {
                    tempNickname = request.getHeader("X-Temp-Nickname");
                }
                if (tempNickname != null && !tempNickname.isEmpty()) {
                    // 前端使用 encodeURIComponent 编码，这里需要解码
                    try {
                        tempNickname = URLDecoder.decode(tempNickname, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        // 如果解码失败，使用原始值（可能是未编码的）
                        System.out.println("⚠️ [JwtInterceptor] 昵称解码失败，使用原始值: " + e.getMessage());
                    }
                    request.setAttribute("tempNickname", tempNickname);
                    System.out.println("✅ [房间/游戏接口] 收到临时昵称: " + tempNickname);
                }
            } else {
                // 有有效的userId，使用正常用户
                request.setAttribute("userId", userId);
                request.setAttribute("tempUserId", null);
                System.out.println("✅ [房间/游戏接口] 使用登录用户ID: " + userId);
            }
            
            // 允许通过，无论是否有userId（临时用户会在Controller中处理）
            return true;
        }
        
        // 其他接口需要登录
        // 获取token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        
        // 验证token
        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        
        // 将userId存入request
        Long userId = jwtUtil.getUserIdFromToken(token);
        request.setAttribute("userId", userId);
        
        return true;
    }
    
    /**
     * 获取客户端IP地址
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
