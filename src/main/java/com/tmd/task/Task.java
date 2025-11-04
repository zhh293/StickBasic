package com.tmd.task;

import com.tmd.entity.dto.User;
import com.tmd.entity.dto.UserStatus;
import com.tmd.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class Task {
    //现在有两个定时任务，一个是每日书签，一个是每天把accountDay+1
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;
    @Scheduled(cron = "0 0 0 * * ?")
    public void dailyBookmark() {
        log.info("[定时任务] 每日书签");
        stringRedisTemplate.delete("dailyBookmark");
        log.info("[定时任务] 删除成功");
    }
    @Scheduled(cron = "0 0 0 * * ?")
    public void addAccountDays() {
        log.info("[定时任务] 每日增加accountDays");
        User user=User.builder()
                        .updatedAt(LocalDateTime.now())
                                .status(UserStatus.active)
                                        .build();
        userMapper.updateAccountDays(user);
        log.info("[定时任务] 增加成功");
    }
}
