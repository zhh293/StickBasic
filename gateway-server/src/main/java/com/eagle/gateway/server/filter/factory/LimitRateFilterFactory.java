package com.eagle.gateway.server.filter.factory;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.eagle.gateway.server.enums.ServerErrorCode;
import com.eagle.gateway.server.exception.ServerException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis的限流过滤器工厂
 * 支持令牌桶算法实现限流
 */
@Slf4j
@Component
public class LimitRateFilterFactory extends AbstractGatewayFilterFactory<LimitRateFilterFactory.Config> {

    /**
     * Redis Lua脚本，实现令牌桶算法
     * 1. 获取当前桶中的令牌数
     * 2. 计算距离上次请求的时间差，补充相应令牌数
     * 3. 如果令牌数超过桶容量，则设为桶容量
     * 4. 如果令牌数足够，则扣除请求所需令牌数并返回1，否则返回0
     */
    private static final String LIMIT_SCRIPT =
            "local key = KEYS[1] " +
                    "local capacity = tonumber(ARGV[1]) " +
                    "local refillRate = tonumber(ARGV[2]) " +
                    "local now = tonumber(ARGV[3]) " +
                    "local requestedTokens = tonumber(ARGV[4]) " +
                    "local lastRefillTimeKey = key .. ':lastRefillTime' " +
                    "local tokensKey = key .. ':tokens' " +
                    "local lastRefillTime = tonumber(redis.call('GET', lastRefillTimeKey)) " +
                    "if lastRefillTime == nil then " +
                    "  lastRefillTime = now " +
                    "  redis.call('SET', lastRefillTimeKey, lastRefillTime) " +
                    "  redis.call('SET', tokensKey, capacity) " +
                    "end " +
                    "local tokens = tonumber(redis.call('GET', tokensKey)) " +
                    "if tokens == nil then " +
                    "  tokens = capacity " +
                    "end " +
                    "local deltaTime = math.max(0, now - lastRefillTime) " +
                    "local newTokens = math.min(capacity, tokens + deltaTime * refillRate) " +
                    "if newTokens >= requestedTokens then " +
                    "  redis.call('SET', tokensKey, newTokens - requestedTokens) " +
                    "  redis.call('SET', lastRefillTimeKey, now) " +
                    "  return 1 " +
                    "else " +
                    "  redis.call('SET', tokensKey, newTokens) " +
                    "  redis.call('SET', lastRefillTimeKey, now) " +
                    "  return 0 " +
                    "end";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private RedisScript<Long> redisScript;

    public LimitRateFilterFactory() {
        super(Config.class);
        this.redisScript = RedisScript.of(LIMIT_SCRIPT, Long.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            try {
                // 构建限流key，支持基于IP、用户、API等维度的限流
                String key = buildRateLimitKey(exchange.getRequest(), config);

                // 获取当前时间戳（秒）
                long now = System.currentTimeMillis() / 1000;

                // 执行Lua脚本进行限流判断
                Long allowed = redisTemplate.execute(
                        redisScript,
                        Arrays.asList(key),
                        String.valueOf(config.getCapacity()),
                        String.valueOf(config.getRefillRate()),
                        String.valueOf(now),
                        String.valueOf(config.getRequestedTokens())
                );

                // 如果返回0表示被限流
                if (allowed != null && allowed == 0) {
                    log.warn("请求被限流，限流key: {}", key);
                    throw new ServerException(ServerErrorCode.REQUEST_FUSE_ERROR, "请求过于频繁，请稍后再试");
                }

                return chain.filter(exchange);
            } catch (ServerException e) {
                // 业务异常直接抛出
                throw e;
            } catch (Exception e) {
                // Redis异常处理，避免因限流服务不可用影响主流程
                log.error("限流检查异常，放行请求", e);
                // 出现异常时默认放行请求，保证服务可用性
                return chain.filter(exchange);
            }
        };
    }

    /**
     * 构建限流使用的Redis key
     * @param request 当前请求
     * @param config 限流配置
     * @return 限流key
     */
    private String buildRateLimitKey(org.springframework.http.server.reactive.ServerHttpRequest request,
                                     Config config) {
        StringBuilder keyBuilder = new StringBuilder("rate_limit:");

        // 根据配置选择限流维度
        switch (config.getLimitType()) {
            case IP:
                String remoteAddr = request.getRemoteAddress() != null ?
                        request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
                keyBuilder.append("ip:").append(remoteAddr);
                break;
            case API:
                keyBuilder.append("api:").append(request.getURI().getPath());
                break;
            case USER:
                // 从请求头获取用户标识，需要前置认证过滤器支持
                String userId = request.getHeaders().getFirst("X-User-Id");
                keyBuilder.append("user:").append(userId != null ? userId : "anonymous");
                break;
            default:
                keyBuilder.append("default");
        }

        return keyBuilder.toString();
    }

    /**
     * 限流配置类
     */
    @Data
    public static class Config {
        /**
         * 令牌桶容量
         */
        private int capacity = 100;

        /**
         * 令牌填充速率（每秒补充的令牌数）
         */
        private int refillRate = 10;

        /**
         * 单次请求消耗的令牌数
         */
        private int requestedTokens = 1;

        /**
         * 限流维度类型
         */
        private LimitType limitType = LimitType.IP;
    }

    /**
     * 限流维度枚举
     */
    public enum LimitType {
        /**
         * 基于IP限流
         */
        IP,

        /**
         * 基于API路径限流
         */
        API,

        /**
         * 基于用户限流
         */
        USER
    }
}

