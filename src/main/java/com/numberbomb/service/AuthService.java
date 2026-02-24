package com.numberbomb.service;

import com.numberbomb.dto.LoginRequest;
import com.numberbomb.dto.LoginResponse;
import com.numberbomb.dto.RegisterRequest;
import com.numberbomb.dto.UsernameLoginRequest;
import com.numberbomb.entity.User;
import com.numberbomb.mapper.UserMapper;
import com.numberbomb.utils.JwtUtil;
import com.numberbomb.utils.WxUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final WxUtil wxUtil;
    
    /**
     * 用户名密码登录
     */
    public LoginResponse login(UsernameLoginRequest request) {
        // 查询用户（只查有 username 的用户）
        User user = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .isNotNull(User::getUsername)
        );
        
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 验证密码
        String encryptedPassword = encryptPassword(request.getPassword());
        if (!encryptedPassword.equals(user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        
        // 更新登录时间
        user.setLastLogin(LocalDateTime.now());
        userMapper.updateById(user);
        
        // 生成token
        String token = jwtUtil.generateToken(user.getId());
        
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(convertToUserVO(user));
        
        return response;
    }
    
    /**
     * 用户注册
     */
    public LoginResponse register(RegisterRequest request) {
        // 检查用户名是否已存在（只查有 username 的用户）
        User existUser = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .isNotNull(User::getUsername)
        );
        
        if (existUser != null) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 创建新用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(encryptPassword(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setAvatarUrl("");
        user.setOpenid(null);  // 账号密码登录没有 openid
        user.setTotalGames(0);
        user.setWinGames(0);
        user.setRankScore(0);
        user.setRankLevel(1);
        user.setMaxStreak(0);
        user.setCurrentStreak(0);
        user.setLastLogin(LocalDateTime.now());
        userMapper.insert(user);
        
        // 生成token
        String token = jwtUtil.generateToken(user.getId());
        
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(convertToUserVO(user));
        
        return response;
    }
    
    /**
     * 微信登录
     */
    public LoginResponse wxLogin(LoginRequest request) {
        // 获取微信openid
        String openid = wxUtil.getOpenid(request.getCode());
        
        // 查询用户
        User user = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getOpenid, openid)
        );

        // 新用户注册
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname(request.getUserInfo() != null ? request.getUserInfo().getNickName() : "玩家" + System.currentTimeMillis() % 10000);
            user.setAvatarUrl(request.getUserInfo() != null ? request.getUserInfo().getAvatarUrl() : "");
            user.setTotalGames(0);
            user.setWinGames(0);
            user.setRankScore(0);
            user.setRankLevel(1);
            user.setMaxStreak(0);
            user.setCurrentStreak(0);
            user.setLastLogin(LocalDateTime.now());
            userMapper.insert(user);
        } else {
            // 更新登录时间
            user.setLastLogin(LocalDateTime.now());
            if (request.getUserInfo() != null) {
                user.setNickname(request.getUserInfo().getNickName());
                user.setAvatarUrl(request.getUserInfo().getAvatarUrl());
            }
            userMapper.updateById(user);
        }
        
        // 生成token
        String token = jwtUtil.generateToken(user.getId());
        
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(convertToUserVO(user));
        
        return response;
    }
    
    public String refreshToken(String refreshToken) {
        // 验证并刷新token
        return jwtUtil.refreshToken(refreshToken);
    }
    
    /**
     * 模拟登录（用于H5开发测试）
     */
    public LoginResponse mockLogin(String nickname, String avatarUrl) {
        // 模拟登录（用于H5开发）
        User user = new User();
        user.setNickname(nickname != null ? nickname : "玩家" + System.currentTimeMillis() % 10000);
        user.setAvatarUrl(avatarUrl != null ? avatarUrl : "");
        user.setTotalGames(0);
        user.setWinGames(0);
        user.setRankScore(0);
        user.setRankLevel(1);
        user.setMaxStreak(0);
        user.setCurrentStreak(0);
        user.setLastLogin(LocalDateTime.now());
        user.setOpenid("mock_" + System.currentTimeMillis());
        userMapper.insert(user);
        
        String token = jwtUtil.generateToken(user.getId());
        
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(convertToUserVO(user));
        
        return response;
    }
    
    /**
     * 密码加密
     */
    private String encryptPassword(String password) {
        return DigestUtils.md5DigestAsHex((password + "numberbomb_salt").getBytes(StandardCharsets.UTF_8));
    }
    
    private LoginResponse.UserVO convertToUserVO(User user) {
        LoginResponse.UserVO vo = new LoginResponse.UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRankLevel(user.getRankLevel());
        vo.setRankScore(user.getRankScore());
        vo.setTotalGames(user.getTotalGames());
        vo.setWinGames(user.getWinGames());
        return vo;
    }
}
