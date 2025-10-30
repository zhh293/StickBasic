package com.tmd.controller;

import com.tmd.entity.dto.Result;
import com.tmd.service.FuncService;
import com.tmd.tools.SimpleTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static com.tmd.constants.common.ERROR_CODE;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/10
 */
@Slf4j
@RestController
public class FuncController {
    @Autowired
    private FuncService funcService;

    @GetMapping("/saying")
    public Result saying(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization ) {
        log.info("用户正在获取每日一句");
        var uid = SimpleTools.checkToken(authorization);
        if (uid != ERROR_CODE){
            return Result.success(funcService.saying());
        }
        return Result.error("验证失败,非法访问");
    }
}
