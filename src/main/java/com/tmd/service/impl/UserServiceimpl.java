package com.tmd.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.tmd.config.RedisCache;
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
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
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
        LoginUser loginUser = (LoginUser) principal;
        //生成jwt令牌，并且存入Redis中
        Map<String,Long> map=new HashMap<>();
        map.put("id",loginUser.getUser().getId());
        String jwt = JwtUtil.makeToken(userData.getId());
        userData.setToken(jwt);
        redisCache.setCacheObject("login:"+jwt,loginUser);
        UserData user = loginUser.getUser();
        user.setToken(jwt);
        user.setId(user.getId());
        log.info("用户登陆成功{}",user);
        return user;
    }

    @Override
    public UserProfile getProfile(Long userId) {
        // 优先从缓存读取基础资料
        log.info("正在读取用户基础资料: userId={}", userId);
        String profileKey = "user:profile:" + userId;
        String s = stringRedisTemplate.opsForValue().get(profileKey);
        UserProfile userProfile = JSONUtil.toBean(s, UserProfile.class);
        if (userProfile == null) {
            userProfile = userMapper.getProfile(userId);
            log.info(":读取的数据{}", userProfile);
            if (userProfile != null) {
                // 缓存基础资料 10 分钟，避免频繁 DB 访问
                stringRedisTemplate.opsForValue().set("user:profile:" + userId, JSONUtil.toJsonStr(userProfile), 10, TimeUnit.MINUTES);
            }
        }
        String dailyBookmark=redisCache.getCacheObject("bookmark:"+userId);
        log.info(":读取的书签{}", dailyBookmark);
        if(StrUtil.isNotBlank(dailyBookmark)){
            userProfile.setIsFirst(false);
            userProfile.setDailyBookmark(dailyBookmark);
            return userProfile;
        }else{
            //生成书签
            log.info("生成书签: userId={}", userId);
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
        // 更新资料后主动失效缓存，确保读取到最新信息
        try {
            redisCache.deleteObject("user:profile:" + id);
        } catch (Exception ignore) {}
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
        if(!StringUtils.hasText(username)){
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
