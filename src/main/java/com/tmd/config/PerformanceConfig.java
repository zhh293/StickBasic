package com.tmd.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;


@Configuration
@EnableScheduling
public class PerformanceConfig {

    /**
     * 配置HTTP请求交换信息仓库
     * 用于记录HTTP请求和响应的详细信息
     * 
     * @return HttpExchangeRepository实例
     */
    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        InMemoryHttpExchangeRepository repository = new InMemoryHttpExchangeRepository();
        repository.setCapacity(1000); // 最多保留1000条记录
        return repository;
    }

    /**
     * 配置定时任务切面
     * 用于方法级别的性能监控
     * 
     * @param registry 计量注册表
     * @return TimedAspect实例
     */
    @Bean
    @Primary
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * 配置简单的计量注册表
     * 用于收集和存储性能指标
     * 
     * @return SimpleMeterRegistry实例
     */
    @Bean
    public SimpleMeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}