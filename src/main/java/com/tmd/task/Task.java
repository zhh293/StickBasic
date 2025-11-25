package com.tmd.task;

import com.tmd.entity.dto.AliOssUtil;
import com.tmd.entity.dto.User;
import com.tmd.entity.dto.UserStatus;
import com.tmd.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.Cursor;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDateTime;

@Component
@Slf4j
public class Task {
    //现在有两个定时任务，一个是每日书签，一个是每天把accountDay+1
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AliOssUtil aliOssUtil;
    @Scheduled(cron = "0 0 0 * * ?")
    public void dailyBookmark() {
        log.info("[定时任务] 每日书签");
        //删除bookmark为前缀的所有key，使用scan
        Set<String> redisKeysToDelete = new HashSet<>();
        String baseUrl = "https://" + aliOssUtil.getBucketName() + "." + aliOssUtil.getEndpoint() + "/";

        stringRedisTemplate.execute((RedisCallback<Long>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match("bookmark:*").build();
            Cursor<byte[]> cursor = connection.scan(options);
            while (cursor.hasNext()) {
                String redisKey = new String(cursor.next());
                String fileUrl = stringRedisTemplate.opsForValue().get(redisKey);

                if (fileUrl != null && fileUrl.startsWith(baseUrl)) {
                    String objectKey = fileUrl.substring(baseUrl.length());
                    boolean ossDeleted = aliOssUtil.delete(objectKey);
                    if (ossDeleted) {
                        log.info("OSS文件删除成功: objectKey={}", objectKey);
                        redisKeysToDelete.add(redisKey);
                    } else {
                        log.warn("OSS文件删除失败: objectKey={}", objectKey);
                    }
                } else {
                    log.warn("无法从Redis键 {} 的值 {} 中提取objectKey，或文件URL不符合预期，将直接删除Redis键", redisKey, fileUrl);
                    redisKeysToDelete.add(redisKey);
                }
            }
            return 0L;
        });

        if (!redisKeysToDelete.isEmpty()) {
            stringRedisTemplate.delete(redisKeysToDelete);
        }
        log.info("[定时任务] 删除成功");
    }
    @Scheduled(cron = "0 0 0 * * ?")
    public void addAccountDays() {
        log.info("[定时任务] 每日增加accountDays");
        User user=User.builder()
                        .updatedAt(LocalDateTime.now())
                                .status(UserStatus.active)
                                        .build();
//        userMapper.updateAccountDays(user);
        log.info("[定时任务] 增加成功");
    }
}
