package com.eagle.gateway.auth.handle;

import com.alibaba.fastjson.JSON;
import org.example.springsecurity.domain.Result;
import org.example.springsecurity.utils.WebUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
@Component
public class AuthenticationHandler implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AuthenticationException e) throws IOException, ServletException {
        Result<Object> objectResult = Result.success(HttpStatus.UNAUTHORIZED.value(), "认证失败请重新登录");
        String jsonString = JSON.toJSONString(objectResult);
        //处理异常
        WebUtils.renderString(httpServletResponse,jsonString );
    }
}
