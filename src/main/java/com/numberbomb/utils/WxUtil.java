package com.numberbomb.utils;

import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class WxUtil {
    
    @Value("${wx.miniapp.app-id}")
    private String appId;
    
    @Value("${wx.miniapp.app-secret}")
    private String appSecret;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    public String getOpenid(String code) {
        // 实际环境中调用微信接口
        // String url = String.format(
        //     "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
        //     appId, appSecret, code
        // );
        // String response = restTemplate.getForObject(url, String.class);
        // JSONObject json = JSONObject.parseObject(response);
        // return json.getString("openid");
        
        // 模拟返回
        return "mock_openid_" + code;
    }
}
