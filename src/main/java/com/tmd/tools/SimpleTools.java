package com.tmd.tools;

import com.auth0.jwt.interfaces.Claim;
import com.tmd.entity.dto.UserProfile;
import com.tmd.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static com.tmd.constants.common.ERROR_CODE;

/**
 * @Description 简单的工具类
 * @Author Bluegod
 * @Date 2025/9/12
 */
@Component
@Slf4j
public class SimpleTools {
    private static UserService userService;
    @Autowired
    public SimpleTools(UserService userService) {
        SimpleTools.userService = userService;
    }
    public static long checkToken(String authorization) {
        log.info("正在验证Authorization");
        // 1. 去掉前缀 “Bearer ”
        String token = authorization.replace("Bearer ", "");

        // 2. 解析 token 拿用户 ID
        Optional<Map<String, Claim>> claims = JwtUtil.getClaims(token);
        Long userId = claims.get().get("uid").asLong();


        // 3. 查库并返回
        UserProfile userProfile = userService.getProfile(userId);
        if (userProfile != null) {
            log.info("验证成功,用户ID:{}", userId);
            return userId;
        }
        log.info("验证失败,非法访问");
        return ERROR_CODE;
    }
}
