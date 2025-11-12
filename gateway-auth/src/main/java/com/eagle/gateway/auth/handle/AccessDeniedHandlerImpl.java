package com.eagle.gateway.auth.handle;

import com.alibaba.fastjson.JSON;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
@Component
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AccessDeniedException e) throws IOException, ServletException {
        Result<Object> objectResult = Result.success(HttpStatus.FORBIDDEN.value(), "你的权限不足请重新登录");
        String jsonString = JSON.toJSONString(objectResult);
        //处理异常
        WebUtils.renderString(httpServletResponse,jsonString );
    }
}
