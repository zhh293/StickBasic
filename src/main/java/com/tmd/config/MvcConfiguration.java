package com.tmd.config;

import com.tmd.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.config.annotation.*;

import java.util.List;

@Configuration
@Slf4j
public class MvcConfiguration extends WebMvcConfigurationSupport {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition");;
    }
    @Bean(name = "mvcTaskExecutor")
    public AsyncTaskExecutor mvcTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数（默认线程数）
        executor.setCorePoolSize(10);
        // 最大线程数
        executor.setMaxPoolSize(20);
        // 队列容量（当核心线程都在忙时，新任务会进入队列等待）
        executor.setQueueCapacity(100);
        // 线程空闲时间（超过核心线程数的线程，空闲多久后销毁）
        executor.setKeepAliveSeconds(60);
        // 线程名称前缀（便于日志排查）
        executor.setThreadNamePrefix("mvc-async-");
        // 拒绝策略（当队列满且线程数达到最大值时如何处理新任务）
        // CallerRunsPolicy：让提交任务的线程自己执行（避免任务丢失，适合生产环境）
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // 初始化线程池
        executor.initialize();
        return executor;
    }
    @Bean
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
    }

    /**
     * 配置Spring MVC的异步支持，指定使用自定义的线程池
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 设置异步处理的线程池执行器
        configurer.setTaskExecutor(mvcTaskExecutor());
        // 可选：设置异步请求的超时时间（毫秒）
        configurer.setDefaultTimeout(30000);
    }
    @Override
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("开始设置静态资源映射");
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
    @Override
    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("扩展消息转化器");
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(new JacksonObjectMapper());
        converters.add(0,converter);
    }
}
