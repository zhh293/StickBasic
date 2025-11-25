package com.tmd.controller;

import cn.hutool.json.JSONUtil;
import com.tmd.entity.dto.Result;
import com.tmd.entity.dto.StickVO;
import com.tmd.entity.dto.UserProfile;
import com.tmd.entity.dto.UserUpdateDTO;
import com.tmd.entity.dto.UserVO;
import com.tmd.entity.po.LoginUser;
import com.tmd.entity.po.UserData;
import com.tmd.service.AttachmentService;
import com.tmd.service.FollowService;
import com.tmd.service.UserService;
import com.tmd.tools.BaseContext;
import com.tmd.tools.JwtUtil;
import com.tmd.tools.SimpleTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import com.tmd.entity.dto.FileUploadResponse;
import cn.hutool.core.util.StrUtil;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static com.tmd.constants.common.ERROR_CODE;

@Slf4j
@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private FollowService followService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/register")
    public Result register(@RequestBody UserData userData) {
        log.info("用户正在注册:{}", userData.getUsername());
        if(userService.register(userData)) {
            userData.setToken(JwtUtil.makeToken(userData.getId()));
            LoginUser loginUser = new LoginUser(userData);
            stringRedisTemplate.opsForValue().set("login:"+userData.getToken(), JSONUtil.toJsonStr(loginUser));
            log.info("用户{}注册成功,生成token:{},并且信息存入redis当中了", userData.getUsername(), userData.getToken());
            return Result.success(new UserVO(userData));
        }
        return Result.error("注册失败,用户已注册");
    }

    @PostMapping("/login")
    public Result login(@RequestBody UserData userData,@RequestParam String captchaCode,@RequestParam String captchaId) {
        log.info("用户正在登录:{}", userData.getUsername());
        userData = userService.login(userData);
        if(userData != null){
            //验证码登录
            String key="captcha:"+captchaId;
            String code = stringRedisTemplate.opsForValue().get(key);
            if(code==null||StrUtil.isBlank(code)){
                return Result.error("验证码已过期");
            }
            if(!code.equalsIgnoreCase(captchaCode)){
                return Result.error("验证码错误");
            }
            stringRedisTemplate.delete(key);
            log.info("用户登录成功:{}", userData.getUsername());
            return Result.success(userData.getToken());
        }
        log.info("用户登录失败:{},{}", userData.getUsername(),userData.getPassword());
        return Result.error("用户名或密码错误");
    }
    @GetMapping("/user/profile")
    public Result getUserProfile(
            @RequestHeader("authentication") String authorization
    ) {
        Long userId=BaseContext.get();
        log.info("用户正在获取用户信息:{}", BaseContext.get());
        var id =1;
        if (id != ERROR_CODE){
            String s = stringRedisTemplate.opsForValue().get("login:" + authorization);
            if(!StringUtils.hasText(s)){
                return Result.error("验证失败,非法访问");
            }
            LoginUser loginUser = JSONUtil.toBean(s, LoginUser.class);
            log.info("用户id为{}",userId);
            log.info("threadLocal所取得id为{}",BaseContext.get());
            UserProfile userProfile = userService.getProfile(userId);
            return Result.success(userProfile);
        }
        return Result.error("验证失败,非法访问");
    }
    //忘记密码功能

    @PostMapping("/{userId}/follow")
    public Result followUser(@PathVariable Long userId) {
        log.info("用户正在关注用户:{}", userId);
        Long id = BaseContext.get();
        if (id != ERROR_CODE) {
            boolean success = followService.followUser(id, userId);
            if (success) {
                return Result.success("关注成功");
            } else {
                return Result.error("关注失败");
            }
        }
        return Result.error("验证失败,非法访问");
    }

    @DeleteMapping("/{userId}/follow")
    public Result unfollowUser(@PathVariable Long userId) {
        log.info("用户正在取消关注用户:{}", userId);
        Long id = BaseContext.get();
        if (id != ERROR_CODE) {
            boolean success = followService.unfollowUser(id, userId);
            if (success) {
                return Result.success("取消关注成功");
            } else {
                return Result.error("取消关注失败");
            }
        }
        return Result.error("验证失败,非法访问");
    }

    @GetMapping("/{userId}/following")
    public Result getFollowing(@PathVariable Long userId) {
        log.info("获取用户 {} 的关注列表", userId);
        try {
            List<Long> followingIds = followService.getFollowingIds(userId);
            return Result.success(followingIds);
        } catch (Exception e) {
            log.error("获取关注列表失败: 用户 {}", userId, e);
            return Result.error("获取关注列表失败");
        }
    }

    @GetMapping("/{userId}/followers")
    public Result getFollowers(@PathVariable Long userId) {
        log.info("获取用户 {} 的粉丝列表", userId);
        try {
            List<Long> followerIds = followService.getFollowerIds(userId);
            return Result.success(followerIds);
        } catch (Exception e) {
            log.error("获取粉丝列表失败: 用户 {}", userId, e);
            return Result.error("获取粉丝列表失败");
        }
    }

    @GetMapping("/{userId}/following/count")
    public Result getFollowingCount(@PathVariable Long userId) {
        log.info("获取用户 {} 的关注数量", userId);
        try {
            int count = followService.getFollowingCount(userId);
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取关注数量失败: 用户 {}", userId, e);
            return Result.error("获取关注数量失败");
        }
    }

    @GetMapping("/{userId}/followers/count")
    public Result getFollowerCount(@PathVariable Long userId) {
        log.info("获取用户 {} 的粉丝数量", userId);
        try {
            int count = followService.getFollowerCount(userId);
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取粉丝数量失败: 用户 {}", userId, e);
            return Result.error("获取粉丝数量失败");
        }
    }

    @GetMapping("/{userId}/is-following")
    public Result isFollowing(@PathVariable Long userId) {
        log.info("检查当前用户是否关注用户 {}", userId);
        Long currentUserId = BaseContext.get();
        if (currentUserId != ERROR_CODE) {
            boolean isFollowing = followService.isFollowing(currentUserId, userId);
            return Result.success(isFollowing);
        }
        return Result.error("验证失败,非法访问");
    }





    @PutMapping("/user/profile")
    public Result updateUserProfile(@RequestBody UserUpdateDTO userUpdateDTO) {
        log.info("用户正在尝试更新个人信息:{}", userUpdateDTO);
        Long id=BaseContext.get();
        if (id != ERROR_CODE){
            userService.updateUserProfile(id, userUpdateDTO);

            if (StrUtil.isNotBlank(userUpdateDTO.getAvatar())) {
                try {
                    URL url = new URL(userUpdateDTO.getAvatar());
                    String objectKey = url.getPath().substring(1);
                    String fileType = objectKey.split("/")[0];

                    FileUploadResponse avatarResponse = FileUploadResponse.builder()
                            .fileId(objectKey)
                            .fileUrl(userUpdateDTO.getAvatar())
                            .fileType(fileType)
                            .fileName("用户更新文件")
                            .build();
                    attachmentService.saveAttachment(avatarResponse, "user", id, id);
                } catch (Exception e) {
                    log.error("Failed to save avatar attachment for user {}", id, e);
                }
            }

            if (StrUtil.isNotBlank(userUpdateDTO.getHomepageBackground())) {
                try {
                    URL url = new URL(userUpdateDTO.getHomepageBackground());
                    String objectKey = url.getPath().substring(1);
                    String fileType = objectKey.split("/")[0];

                    FileUploadResponse backgroundResponse = FileUploadResponse.builder()
                            .fileId(objectKey)
                            .fileUrl(userUpdateDTO.getHomepageBackground())
                            .fileType(fileType)
                            .build();
                    attachmentService.saveAttachment(backgroundResponse, "user", id, id);
                } catch (Exception e) {
                    log.error("Failed to save homepage background attachment for user {}", id, e);
                }
            }

            return Result.success("个人信息更新成功");
        }
        return Result.error("验证失败,非法访问");
    }

    @DeleteMapping("/{userId}")
    public Result softDeleteUser(@PathVariable Long userId) {
        log.info("执行软删除用户操作，用户ID: {}", userId);
        try {
            userService.softDeleteUser(userId);
            return Result.success("用户软删除成功");
        } catch (Exception e) {
            log.error("软删除用户失败，用户ID: {}", userId, e);
            return Result.error("用户软删除失败");
        }
    }

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
