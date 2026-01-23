package com.tmd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /*@Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Captcha-Id", "Authorization", "Content-Disposition") // 暴露自定义响应头
                .allowCredentials(true)
                .maxAge(3600);
    }*/
    // 注册AntPathMatcher为默认路径匹配器（关键：覆盖Spring Boot 2.6+的PathPatternParser）
   /* @Bean
    public PathMatcher pathMatcher() {
        AntPathMatcher pathMatcher = new AntPathMatcher();
        pathMatcher.setTrimTokens(false); // 不修剪token，允许路径变量含/
        return pathMatcher;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 显式指定使用AntPathMatcher
        configurer.setPathMatcher(pathMatcher());
        // 关闭后缀匹配（避免.jpg被当作扩展名截断）
        configurer.setUseSuffixPatternMatch(false);
    }*/
}
