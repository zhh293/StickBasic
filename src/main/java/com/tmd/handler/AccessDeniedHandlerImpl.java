package com.tmd.handler;

import com.alibaba.fastjson.JSON;

import com.tmd.entity.dto.Result;
import com.tmd.tools.WebUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AccessDeniedException e) throws IOException, ServletException {
        Result  objectResult = Result.success(HttpStatus.FORBIDDEN.value());
        String jsonString = JSON.toJSONString(objectResult);
        //处理异常
        WebUtils.renderString(httpServletResponse,jsonString );
    }

}
