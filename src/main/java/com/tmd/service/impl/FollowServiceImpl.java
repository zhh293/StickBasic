package com.tmd.service.impl;

import com.tmd.entity.po.Follow;
import com.tmd.mapper.FollowMapper;
import com.tmd.service.FollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FollowServiceImpl implements FollowService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private FollowMapper followMapper;

    private static final String FOLLOW_KEY_PREFIX = "follow:";
    private static final String FOLLOWERS_KEY_PREFIX = "followers:";
    private static final String FOLLOW_COUNT_KEY_PREFIX = "follow_count:";
    private static final String FOLLOWER_COUNT_KEY_PREFIX = "follower_count:";

    @Override
    public boolean followUser(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            log.warn("用户不能关注自己: {}", followerId);
            return false;
        }

        try {
            // 检查是否已经关注
            String followingKey = FOLLOW_KEY_PREFIX + followerId;
            String followerKey = FOLLOWERS_KEY_PREFIX + followingId;

            // 使用Redis的Set结构存储关注关系
            Boolean isMember = redisTemplate.opsForSet().isMember(followingKey, followingId.toString());
            if (Boolean.TRUE.equals(isMember)) {
                log.info("用户 {} 已经关注了用户 {}", followerId, followingId);
                return true;
            }

            // 添加到Redis缓存
            redisTemplate.opsForSet().add(followingKey, followingId.toString());
            redisTemplate.opsForSet().add(followerKey, followerId.toString());

            // 设置过期时间（7天）
            redisTemplate.expire(followingKey, 7, TimeUnit.DAYS);
            redisTemplate.expire(followerKey, 7, TimeUnit.DAYS);

            // 更新计数缓存
            updateCountCache(followerId, true, FOLLOW_COUNT_KEY_PREFIX);
            updateCountCache(followingId, true, FOLLOWER_COUNT_KEY_PREFIX);

            // 异步写入数据库
            CompletableFuture.runAsync(() -> {
                try {
                    // 先检查数据库中是否已存在
                    if (followMapper.exists(followerId, followingId) == 0) {
                        Follow follow = new Follow();
                        follow.setFollowerId(followerId);
                        follow.setFollowingId(followingId);
                        follow.setCreatedAt(LocalDateTime.now());
                        followMapper.insert(follow);
                        log.info("异步写入数据库成功: 用户 {} 关注用户 {}", followerId, followingId);
                    }
                } catch (Exception e) {
                    log.error("异步写入数据库失败: 用户 {} 关注用户 {}", followerId, followingId, e);
                }
            });

            log.info("用户 {} 关注用户 {} 操作成功", followerId, followingId);
            return true;

        } catch (Exception e) {
            log.error("关注操作失败: 用户 {} 关注用户 {}", followerId, followingId, e);
            return false;
        }
    }

    @Override
    public boolean unfollowUser(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            log.warn("用户不能取消关注自己: {}", followerId);
            return false;
        }

        try {
            // 检查是否已关注
            String followingKey = FOLLOW_KEY_PREFIX + followerId;
            String followerKey = FOLLOWERS_KEY_PREFIX + followingId;

            Boolean isMember = redisTemplate.opsForSet().isMember(followingKey, followingId.toString());
            if (Boolean.FALSE.equals(isMember)) {
                log.info("用户 {} 未关注用户 {}", followerId, followingId);
                return true;
            }

            // 从Redis缓存中移除
            redisTemplate.opsForSet().remove(followingKey, followingId.toString());
            redisTemplate.opsForSet().remove(followerKey, followerId.toString());

            // 更新计数缓存
            updateCountCache(followerId, false, FOLLOW_COUNT_KEY_PREFIX);
            updateCountCache(followingId, false, FOLLOWER_COUNT_KEY_PREFIX);

            // 异步删除数据库记录
            CompletableFuture.runAsync(() -> {
                try {
                    followMapper.delete(followerId, followingId);
                    log.info("异步删除数据库记录成功: 用户 {} 取消关注用户 {}", followerId, followingId);
                } catch (Exception e) {
                    log.error("异步删除数据库记录失败: 用户 {} 取消关注用户 {}", followerId, followingId, e);
                }
            });

            log.info("用户 {} 取消关注用户 {} 操作成功", followerId, followingId);
            return true;

        } catch (Exception e) {
            log.error("取消关注操作失败: 用户 {} 取消关注用户 {}", followerId, followingId, e);
            return false;
        }
    }

    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        if (followerId == null || followingId == null) {
            return false;
        }

        try {
            String followingKey = FOLLOW_KEY_PREFIX + followerId;
            Boolean isMember = redisTemplate.opsForSet().isMember(followingKey, followingId.toString());

            if (isMember != null) {
                return isMember;
            }

            // 如果Redis中没有数据，从数据库查询并缓存
            boolean exists = followMapper.exists(followerId, followingId) > 0;
            if (exists) {
                redisTemplate.opsForSet().add(followingKey, followingId.toString());
                redisTemplate.expire(followingKey, 7, TimeUnit.DAYS);
            }

            return exists;

        } catch (Exception e) {
            log.error("检查关注状态失败: 用户 {} 是否关注用户 {}", followerId, followingId, e);
            // 降级到数据库查询
            return followMapper.exists(followerId, followingId) > 0;
        }
    }

    @Override
    public List<Long> getFollowingIds(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }

        try {
            String followingKey = FOLLOW_KEY_PREFIX + userId;
            Set<String> followingSet = redisTemplate.opsForSet().members(followingKey);

            if (followingSet != null && !followingSet.isEmpty()) {
                return followingSet.stream()
                        .map(Long::valueOf)
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            }

            // 如果Redis中没有数据，从数据库查询并缓存
            List<Long> followingIds = followMapper.findFollowingIds(userId);
            if (followingIds != null && !followingIds.isEmpty()) {
                for (Long followingId : followingIds) {
                    redisTemplate.opsForSet().add(followingKey, followingId.toString());
                }
                redisTemplate.expire(followingKey, 7, TimeUnit.DAYS);
            }

            return followingIds != null ? followingIds : new ArrayList<>();

        } catch (Exception e) {
            log.error("获取关注列表失败: 用户 {}", userId, e);
            // 降级到数据库查询
            return followMapper.findFollowingIds(userId);
        }
    }

    @Override
    public List<Long> getFollowerIds(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }

        try {
            String followerKey = FOLLOWERS_KEY_PREFIX + userId;
            Set<String> followerSet = redisTemplate.opsForSet().members(followerKey);

            if (followerSet != null && !followerSet.isEmpty()) {
                return followerSet.stream()
                        .map(Long::valueOf)
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            }

            // 如果Redis中没有数据，从数据库查询并缓存
            List<Long> followerIds = followMapper.findFollowerIds(userId);
            if (followerIds != null && !followerIds.isEmpty()) {
                for (Long followerId : followerIds) {
                    redisTemplate.opsForSet().add(followerKey, followerId.toString());
                }
                redisTemplate.expire(followerKey, 7, TimeUnit.DAYS);
            }

            return followerIds != null ? followerIds : new ArrayList<>();

        } catch (Exception e) {
            log.error("获取粉丝列表失败: 用户 {}", userId, e);
            // 降级到数据库查询
            return followMapper.findFollowerIds(userId);
        }
    }

    @Override
    public int getFollowingCount(Long userId) {
        if (userId == null) {
            return 0;
        }

        try {
            String countKey = FOLLOW_COUNT_KEY_PREFIX + userId;
            String countStr = redisTemplate.opsForValue().get(countKey);

            if (countStr != null) {
                return Integer.parseInt(countStr);
            }

            // 如果Redis中没有数据，从数据库查询并缓存
            int count = followMapper.countFollowing(userId);
            redisTemplate.opsForValue().set(countKey, String.valueOf(count), 7, TimeUnit.DAYS);

            return count;

        } catch (Exception e) {
            log.error("获取关注数量失败: 用户 {}", userId, e);
            // 降级到数据库查询
            return followMapper.countFollowing(userId);
        }
    }

    @Override
    public int getFollowerCount(Long userId) {
        if (userId == null) {
            return 0;
        }

        try {
            String countKey = FOLLOWER_COUNT_KEY_PREFIX + userId;
            String countStr = redisTemplate.opsForValue().get(countKey);

            if (countStr != null) {
                return Integer.parseInt(countStr);
            }

            // 如果Redis中没有数据，从数据库查询并缓存
            int count = followMapper.countFollowers(userId);
            redisTemplate.opsForValue().set(countKey, String.valueOf(count), 7, TimeUnit.DAYS);

            return count;

        } catch (Exception e) {
            log.error("获取粉丝数量失败: 用户 {}", userId, e);
            // 降级到数据库查询
            return followMapper.countFollowers(userId);
        }
    }

    /**
     * 更新计数缓存
     */
    private void updateCountCache(Long userId, boolean increment, String keyPrefix) {
        try {
            String countKey = keyPrefix + userId;
            if (increment) {
                redisTemplate.opsForValue().increment(countKey, 1);
            } else {
                redisTemplate.opsForValue().decrement(countKey, 1);
            }
            redisTemplate.expire(countKey, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("更新计数缓存失败: 用户 {} 键前缀 {}", userId, keyPrefix, e);
        }
    }
}