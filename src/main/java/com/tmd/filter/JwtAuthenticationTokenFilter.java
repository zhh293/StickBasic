package com.tmd.filter;

import com.alibaba.fastjson.JSONObject;
import com.tmd.config.RedisCache;
import com.tmd.entity.po.LoginUser;
import com.tmd.tools.BaseContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.PublicKey;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {
    @Autowired
    private RedisCache redisCache;
    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException, ServletException {
        String authentication = httpServletRequest.getHeader("authentication");
        if(!StringUtils.hasText(authentication)){
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }
        logger.info("用户认证成功"+ authentication);
        Object loginuser  = redisCache.getCacheObject("login:" + authentication);
        //先判断是不是字符串类型的可以看出来是否为privatesecret
        Object cacheObject1 = redisCache.getCacheObject("loginPublic:" + authentication);
        if(cacheObject1 != null){
            logger.info("用户公钥认证成功");
            PublicKey publicKey = JSONObject.parseObject(cacheObject1.toString(), PublicKey.class);
            //帮助他授权并且放行
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(publicKey, null,null);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }
        //把这个jsonobject转换成loginuser
        LoginUser cacheObject = JSONObject.parseObject(loginuser.toString(), LoginUser.class);
        if(cacheObject == null){
            httpServletResponse.setStatus(401);
            //filterChain.doFilter(httpServletRequest, httpServletResponse);
            httpServletResponse.sendRedirect("http://localhost:5173/login");//重定向到登陆界面
            throw new RuntimeException("用户未登录");
        }else{
            //存入ContextHolder
            //TODO 获取权限信息封装到Authentication中
            BaseContext.set(cacheObject.getUser().getId());
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(cacheObject, null, cacheObject.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            logger.info("用户认证成功,准备放行");
            //刷新过期时间
            redisCache.setCacheObject("login:"+authentication,cacheObject,30, TimeUnit.MINUTES);
            log.info("获取的用户信息{}",cacheObject);
            filterChain.doFilter(httpServletRequest, httpServletResponse);
        }
    }
}
