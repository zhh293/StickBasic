/*
package com.tmd.sync;


import com.tmd.config.RedisCache1;
import com.tmd.spring.RedisCaffeineCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;


public class CacheMessageListener implements MessageListener {

    private final Logger logger = LoggerFactory.getLogger(CacheMessageListener.class);

    private RedisCache1 redisService;

    private RedisCaffeineCacheManager redisCaffeineCacheManager;

    public CacheMessageListener(RedisCache1 redisService,
                                RedisCaffeineCacheManager redisCaffeineCacheManager) {
        super();
        this.redisService = redisService;
        this.redisCaffeineCacheManager = redisCaffeineCacheManager;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        CacheMessage cacheMessage = (CacheMessage) redisService.getRedisTemplate().getValueSerializer().deserialize(message.getBody());
        logger.debug("recevice a redis topic message, clear local cache, the cacheName is {}, the key is {}", cacheMessage.getCacheName(), cacheMessage.getKey());
        redisCaffeineCacheManager.clearLocal(cacheMessage.getCacheName(), cacheMessage.getKey());
    }
}
*/
