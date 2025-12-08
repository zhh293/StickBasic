package com.tmd.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 企业级重试配置（结合Spring Retry + 自定义策略）
 */
@Slf4j
@Configuration
@EnableRetry // 启用Spring Retry注解
public class RetryConfig {

    /**
     * 1. 通用重试模板（支持指数退避 + 异常过滤）
     * 适用于Redis操作重试（网络抖动/连接超时场景）
     */
    @Bean("redisRetryTemplate")
    public RetryTemplate redisRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 1.1 重试策略：最多3次，仅重试指定异常（Redis相关）
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(org.springframework.data.redis.RedisConnectionFailureException.class, true);
        retryableExceptions.put(IllegalArgumentException.class, false);
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);

        // 1.2 退避策略：指数退避（企业级推荐，避免雪崩）
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000); // 初始间隔1秒
        backOffPolicy.setMultiplier(2); // 每次间隔翻倍（1s → 2s → 4s）
        backOffPolicy.setMaxInterval(5000); // 最大间隔5秒（防止无限增长）

        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // 1.3 重试监听（记录关键日志，便于问题排查）
        retryTemplate.registerListener(new org.springframework.retry.RetryListener() {
            @Override
            public <T, E extends Throwable> boolean open(org.springframework.retry.RetryContext context, org.springframework.retry.RetryCallback<T, E> callback) {
                log.info("Redis操作重试开始，最大重试次数：{}", retryPolicy.getMaxAttempts());
                return true;
            }

            @Override
            public <T, E extends Throwable> void close(org.springframework.retry.RetryContext context, org.springframework.retry.RetryCallback<T, E> callback, Throwable throwable) {
                if (throwable != null) {
                    log.error("Redis操作重试失败，已达到最大重试次数", throwable);
                } else {
                    log.info("Redis操作重试成功，重试次数：{}", context.getRetryCount());
                }
            }

            @Override
            public <T, E extends Throwable> void onError(org.springframework.retry.RetryContext context, org.springframework.retry.RetryCallback<T, E> callback, Throwable throwable) {
                log.warn("Redis操作重试失败，第{}次重试，异常：{}", 
                        context.getRetryCount(), throwable.getMessage());
            }
        });

        return retryTemplate;
    }

    /**
     * 2. 备用：固定间隔重试模板（适用于简单场景）
     */
    @Bean("fixedRetryTemplate")
    public RetryTemplate fixedRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(1000); // 固定1秒间隔
        
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }
}