package com.tmd.aspect;

import com.tmd.entity.dto.Result;
import com.tmd.tools.BaseContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {
    private final StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> LUA_TOKEN_BUCKET;
    static {
        LUA_TOKEN_BUCKET = new DefaultRedisScript<>();
        LUA_TOKEN_BUCKET.setResultType(Long.class);
        LUA_TOKEN_BUCKET.setScriptText(
                "local rate=tonumber(ARGV[1]);" +
                        "local last=tonumber(redis.call('get', KEYS[2]) or ARGV[2]);" +
                        "local now=tonumber(ARGV[3]);" +
                        "local capacity=tonumber(ARGV[4]);" +
                        "local tokens=tonumber(redis.call('get', KEYS[1]) or capacity);" +
                        "local delta=math.floor((now-last)/1000*rate);" +
                        "if delta>0 then tokens=math.min(capacity,tokens+delta); last=now; end;" +
                        "local allowed=0;" +
                        "if tokens>0 then tokens=tokens-1; allowed=1; end;" +
                        "redis.call('set', KEYS[1], tokens);" +
                        "redis.call('set', KEYS[2], last);" +
                        "redis.call('expire', KEYS[1], tonumber(ARGV[5]));" +
                        "redis.call('expire', KEYS[2], tonumber(ARGV[5]));" +
                        "return allowed;");
    }

    @Around("@annotation(com.tmd.aspect.RateLimit)")
    public Object limit(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature ms = (MethodSignature) pjp.getSignature();
        Method m = ms.getMethod();
        RateLimit cfg = m.getAnnotation(RateLimit.class);
        if (cfg == null) {
            return pjp.proceed();
        }
        int threshold = cfg.threshold();
        int window = cfg.windowSeconds();
        boolean byUser = cfg.byUser();
        String custom = cfg.key();
        boolean useTokenBucket = cfg.tokenBucket();
        int capacity = cfg.capacity();
        int refillPerSecond = cfg.refillPerSecond();
        String base = custom != null && !custom.isEmpty() ? custom : m.getName();
        String key = "rate:post:" + base;
        if (byUser) {
            Long uid = BaseContext.get();
            key = key + ":" + (uid == null ? "-" : String.valueOf(uid));
        }
        if (useTokenBucket) {
            String tokensKey = key + ":tb:tokens";
            String tsKey = key + ":tb:ts";
            long now = System.currentTimeMillis();
            List<String> keys = Arrays.asList(tokensKey, tsKey);
            Long allowed = stringRedisTemplate.execute(LUA_TOKEN_BUCKET, keys,
                    String.valueOf(refillPerSecond),
                    String.valueOf(now),
                    String.valueOf(now),
                    String.valueOf(capacity),
                    String.valueOf(window));
            if (allowed == null || allowed == 0L) {
                return Result.error("请求过于频繁");
            }
        } else {
            Long v = null;
            try {
                v = stringRedisTemplate.opsForValue().increment(key, 1);
                if (v != null && v == 1L) {
                    stringRedisTemplate.expire(key, window, TimeUnit.SECONDS);
                }
            } catch (Exception ignore) {
            }
            if (v != null && v > threshold) {
                return Result.error("请求过于频繁");
            }
        }
        return pjp.proceed();
    }
}
