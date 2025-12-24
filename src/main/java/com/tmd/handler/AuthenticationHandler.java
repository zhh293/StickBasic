package com.tmd.handler;

import com.alibaba.fastjson.JSON;
import com.tmd.entity.dto.Result;
import com.tmd.tools.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthenticationHandler implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AuthenticationException e) throws IOException {
        Result objectResult = Result.success(HttpStatus.UNAUTHORIZED.value());
        String jsonString = JSON.toJSONString(objectResult);
        //处理异常
        WebUtils.renderString(httpServletResponse,jsonString );
    }
}
