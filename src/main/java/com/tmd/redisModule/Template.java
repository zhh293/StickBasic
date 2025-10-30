package com.tmd.redisModule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.tmd.config.ThreadPoolConfig;
import com.tmd.entity.dto.StickVO;
import com.tmd.entity.po.UserData;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/*@Component
@Slf4j
public class Template {
    //解决缓存穿透，缓存雪崩和缓存击穿
    //以查询磁贴数据举例
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ThreadPoolConfig threadPoolConfig;
    @Autowired
    private RedissonClient redissonClient;
    public  List<StickVO> getStickList(Long userId) throws InterruptedException {
        String key = "Stick:" + userId;
        String s = redisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(s)){
            RedisData bean = JSONUtil.toBean(s, RedisData.class);
            Long expireTime = bean.getExpireTime();
            if(expireTime > System.currentTimeMillis()){
                //缓存未过期
                return JSONUtil.toList(JSONUtil.parseArray(bean.getData()), StickVO.class);
            }
            else{
                //重新构建缓存，派出一个线程去执行，其他的先返回旧数据，redisson抢锁
                //先获取互斥锁
                String lockKey = "redisLock:" + key;
                //使用看门狗机制并且获取锁
                boolean flag = redissonClient.getLock(lockKey).tryLock(-1,30, TimeUnit.SECONDS);
                try{
                    if(!flag){
                        return JSONUtil.toList(JSONUtil.parseArray(bean.getData()), StickVO.class);
                    }else {
                        //缓存已过期
                        //派一个线程去更新缓存
                        threadPoolConfig.threadPoolExecutor().submit(() -> {
                            try {
                                log.info("更新缓存");
                                List<StickVO> stickList = getStickListFromDB(userId);
                                RedisData redisData = new RedisData(stickList, 60 * 1000L);
                                redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
                                log.info("更新缓存成功");
                            }catch (Exception e){
                                throw new RuntimeException(e);
                            }
                        });
                        return JSONUtil.toList(JSONUtil.parseArray(bean.getData()), StickVO.class);
                    }
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    redissonClient.getLock(lockKey).unlock();
                }
            }
        }
        else{
            //说明要不然是恶意攻击，要不就是缓存不小心丢失了
            if(s != null){
                log.info("缓存穿透");
                return null;
            }
            log.info("缓存未命中");
            //判断这个用户是否存在，不存在的话说明是恶意攻击，查数据库
            UserData user = getUser(userId);
            if(user == null){
                log.info("用户不存在");
                redisTemplate.opsForValue().set(key, "", 60 * 1000L,TimeUnit.SECONDS);
                return null;
            }else{
                log.info("用户存在");
                List<StickVO> stickList = getStickListFromDB(userId);
                RedisData redisData = new RedisData(stickList, 60 * 1000L);
                redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
                return stickList;
            }
        }
    }
}*/
