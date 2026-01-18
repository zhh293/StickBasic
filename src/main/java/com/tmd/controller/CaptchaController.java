package com.tmd.controller;


import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
public class CaptchaController {
    private final StringRedisTemplate stringRedisTemplate;
    @GetMapping("/generate")
    public void generate(HttpServletResponse  response) {
        //TODO 生成验证码
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        captcha.setCharType(Captcha.TYPE_DEFAULT);

        String uuid = UUID.randomUUID().toString();
        String captchaCode= captcha.text().toLowerCase();

        stringRedisTemplate.opsForValue().set("captcha:" + uuid, captchaCode,2, TimeUnit.MINUTES);

        //设置响应头·
        response.setHeader("Captcha-Id", uuid);
        response.setContentType("image/png");
        try {
            captcha.out(response.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
