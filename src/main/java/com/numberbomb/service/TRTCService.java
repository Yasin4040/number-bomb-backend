package com.numberbomb.service;

import com.numberbomb.util.TLSSigAPIv2;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.trtc.v20190722.TrtcClient;
import com.tencentcloudapi.trtc.v20190722.models.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * TRTC 服务类 - 服务端生成 UserSig
 */
@Slf4j
@Service
public class TRTCService {

    @Value("${trtc.sdkAppId:0}")
    private long sdkAppId;

    @Value("${trtc.secretKey:}")
    private String secretKey;

    @Value("${trtc.expireTime:86400}")
    private int expireTime;

    private TrtcClient trtcClient;
    private TLSSigAPIv2 tlsSigAPI;

    @PostConstruct
    public void init() {
        System.out.println("[TRTCService] 初始化开始...");
        System.out.println("[TRTCService] sdkAppId: " + sdkAppId);
        System.out.println("[TRTCService] secretKey length: " + (secretKey != null ? secretKey.length() : 0));
        System.out.println("[TRTCService] secretKey: " + (secretKey != null && secretKey.length() > 50 ? secretKey.substring(0, 50) + "..." : secretKey));
        
        if (sdkAppId > 0 && secretKey != null && !secretKey.isEmpty()) {
            try {
                // 初始化 UserSig 生成工具
                tlsSigAPI = new TLSSigAPIv2(sdkAppId, secretKey);
                System.out.println("[TRTCService] TLSSigAPIv2 初始化成功");
                
                // 初始化 TRTC 客户端（用于管理房间）
                Credential cred = new Credential(String.valueOf(sdkAppId), secretKey);
                HttpProfile httpProfile = new HttpProfile();
                httpProfile.setEndpoint("trtc.tencentcloudapi.com");
                ClientProfile clientProfile = new ClientProfile();
                clientProfile.setHttpProfile(httpProfile);
                trtcClient = new TrtcClient(cred, "ap-guangzhou", clientProfile);
                
                log.info("TRTC 服务初始化成功，SDKAppId: {}", sdkAppId);
            } catch (Exception e) {
                log.error("TRTC 服务初始化失败", e);
            }
        } else {
            log.warn("TRTC 配置不完整，SDKAppId: {}, SecretKey: {}", 
                sdkAppId, secretKey != null ? "已配置" : "未配置");
        }
    }

    /**
     * 生成 TRTC UserSig（用户签名）
     * 使用腾讯云官方算法
     */
    public String generateUserSig(String userId) {
        if (sdkAppId == 0 || secretKey == null || secretKey.isEmpty()) {
            log.warn("TRTC 未配置，返回空签名");
            return "";
        }

        if (tlsSigAPI == null) {
            log.error("TLSSigAPI 未初始化");
            return "";
        }

        try {
            String userSig = tlsSigAPI.genUserSig(userId, expireTime);
            // 打印调试信息
            System.out.println("[TRTC] 生成 UserSig:");
            System.out.println("[TRTC]   SDKAppId: " + sdkAppId);
            System.out.println("[TRTC]   UserId: " + userId);
            System.out.println("[TRTC]   UserSig: " + userSig);
            System.out.println("[TRTC]   SecretKey length: " + (secretKey != null ? secretKey.length() : 0));
            log.debug("生成 UserSig 成功: userId={}, sigLength={}", userId, userSig.length());
            return userSig;
        } catch (Exception e) {
            log.error("生成 UserSig 失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 获取 SDKAppId
     */
    public long getSdkAppId() {
        return sdkAppId;
    }

    /**
     * 查询房间状态
     */
    public void queryRoomStatus(int roomId) {
        if (trtcClient == null) {
            log.warn("TRTC 客户端未初始化");
            return;
        }

        try {
            DescribeRoomInfoRequest req = new DescribeRoomInfoRequest();
            req.setSdkAppId((long) sdkAppId);
            req.setRoomId(String.valueOf(roomId));

            DescribeRoomInfoResponse resp = trtcClient.DescribeRoomInfo(req);
            log.info("房间状态查询成功: {}", DescribeRoomInfoResponse.toJsonString(resp));
        } catch (TencentCloudSDKException e) {
            log.error("查询房间状态失败", e);
        }
    }

    /**
     * 解散房间
     */
    public void dismissRoom(int roomId) {
        if (trtcClient == null) {
            log.warn("TRTC 客户端未初始化");
            return;
        }

        try {
            DismissRoomRequest req = new DismissRoomRequest();
            req.setSdkAppId((long) sdkAppId);
            req.setRoomId(Long.valueOf(String.valueOf(roomId)));

            DismissRoomResponse resp = trtcClient.DismissRoom(req);
            log.info("解散房间成功: {}", DismissRoomResponse.toJsonString(resp));
        } catch (TencentCloudSDKException e) {
            log.error("解散房间失败", e);
        }
    }
}
