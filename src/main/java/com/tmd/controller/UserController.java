package com.tmd.controller;

import com.tmd.entity.dto.Result;
import com.tmd.entity.dto.StickVO;
import com.tmd.entity.dto.UserProfile;
import com.tmd.entity.dto.UserVO;
import com.tmd.entity.po.UserData;
import com.tmd.service.UserService;
import com.tmd.tools.JwtUtil;
import com.tmd.tools.SimpleTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.tmd.constants.common.ERROR_CODE;

@Slf4j
@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result register(@RequestBody UserData userData) {
        log.info("用户正在注册:{}", userData.getUsername());
        if(userService.register(userData)) {
            userData.setToken(JwtUtil.makeToken(userData.getId()));
            return Result.success(new UserVO(userData));
        }
        return Result.error("注册失败,用户已注册");
    }

    @PostMapping("/login")
    public Result login(@RequestBody UserData userData) {
        log.info("用户正在登录:{}", userData.getUsername());
        userData = userService.login(userData);
        if(userData != null){

            log.info("用户登录成功:{}", userData.getUsername());
            return Result.success(new UserVO(userData));
        }
        log.info("用户登录失败:{},{}", userData.getUsername(),userData.getPassword());
        return Result.error("用户名或密码错误");
    }
    @GetMapping("/user/profile")
    public Result getUserProfile(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        var id = SimpleTools.checkToken(authorization);
        if (id != ERROR_CODE){
            UserProfile userProfile = userService.getProfile(id);
            return Result.success(userProfile);
        }
        return Result.error("验证失败,非法访问");
    }
    //忘记密码功能


    @PutMapping("/updatepw")
    public Result updatepassword(@RequestBody Map<String, String> requestBody, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization ){
        log.info("用户正在尝试修改密码");
        var uid = SimpleTools.checkToken(authorization);
        if (uid != ERROR_CODE){
            log.info("用户{}正在尝试修改密码", uid);
            String oldPassword = requestBody.get("oldPw");
            String newPassword = requestBody.get("newPw");
            if(userService.updatePassword(uid, oldPassword, newPassword)){
                log.info("密码修改成功");
                return Result.success("密码修改成功");
            }
            return Result.error("旧密码错误");
        }
        return Result.error("验证失败,非法访问");
    }
}
