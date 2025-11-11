package com.tmd.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.tmd.config.RedisCache;
import com.tmd.entity.dto.Result;
import com.tmd.entity.dto.UserProfile;
import com.tmd.entity.dto.UserUpdateDTO;
import com.tmd.entity.po.LoginUser;
import com.tmd.entity.po.UserData;
import com.tmd.mapper.UserMapper;
import com.tmd.publisher.MessageProducer;
import com.tmd.service.UserService;
import com.tmd.tools.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class UserServiceimpl implements UserService, UserDetailsService {

    @Autowired
    @Lazy
    private AuthenticationManager authenticationManager;
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Override
    public boolean register(UserData userData)
    {
        if(userMapper.findByUsername(userData) == null)
        {
            userMapper.register(userData);
            return true;
        }
        return false;
    }
    @Override
    public UserData login(UserData userData) {
        Authentication authenticate = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(userData.getUsername(), userData.getPassword()));
        if(authenticate==null){
            throw new RuntimeException("用户名或密码错误");
        }
        Object principal = authenticate.getPrincipal();
        LoginUser loginUser = JSONObject.parseObject(principal.toString(), LoginUser.class);
        //生成jwt令牌，并且存入Redis中
        Map<String,Long> map=new HashMap<>();
        map.put("id",loginUser.getUser().getId());
        String jwt = JwtUtil.makeToken(userData.getId());
        userData.setToken(jwt);
        redisCache.setCacheObject("login:"+jwt,loginUser);
        UserData user = loginUser.getUser();
        user.setToken(jwt);
        return user;
    }

    @Override
    public UserProfile getProfile(Long userId) {
        UserProfile userProfile = userMapper.getProfile(userId);
        String dailyBookmark=redisCache.getCacheObject("bookmark:"+userId);
        if(StrUtil.isNotBlank(dailyBookmark)){
            userProfile.setIsFirst(false);
            userProfile.setDailyBookmark(dailyBookmark);
            return userProfile;
        }else{
            //生成书签
            messageProducer.sendDirectMessage(userId, true);
            userProfile.setIsFirst(true);
            return userProfile;
        }
    }

    @Override
    public boolean updatePassword(long uid, String oldPassword, String newPassword) {
        try {
            userMapper.updatePassword(uid, oldPassword, newPassword);
        }
        catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public void updateUserProfile(Long id, UserUpdateDTO userUpdateDTO) {
        userMapper.update(id, userUpdateDTO);
    }

    @Override
    public boolean softDeleteUser(Long userId) {
        try {
            log.info("执行软删除用户: userId={}", userId);
            userMapper.softDelete(userId);
            return true;
        } catch (Exception e) {
            log.error("软删除用户失败: userId={}", userId, e);
            return false;
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("[用户登录] 尝试登录用户: {}", username);
        //开始匹配并且放入
        if(StringUtils.hasText(username)){
            throw new UsernameNotFoundException("不要填空值");
        }
        UserData user = userMapper.check(username);
        if(user==null){
            throw new UsernameNotFoundException("用户不存在");
        }
        LoginUser loginUser = new LoginUser(user);
        return loginUser;
    }

}
