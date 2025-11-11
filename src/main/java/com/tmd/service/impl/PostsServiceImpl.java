package com.tmd.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.tmd.config.ThreadPoolConfig;
import com.tmd.entity.dto.*;
import com.tmd.mapper.AttachmentMapper;
import com.tmd.mapper.PostsMapper;
import com.tmd.mapper.TopicMapper;
import com.tmd.mapper.UserMapper;
import com.tmd.service.PostsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostsServiceImpl implements PostsService {

    private final PostsMapper postsMapper;
    private final AttachmentMapper attachmentMapper;
    private final UserMapper userMapper;
    private final TopicMapper topicMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ThreadPoolConfig threadPoolConfig;
    @Autowired
    private RedissonClient redissonClient;

    private static final String POSTS_LIST_KEY_FMT = "post:list:%s:%s:%s:%d:%d"; // type:status:sort:page:size
    private static final String POSTS_TOTAL_KEY_FMT = "post:total:%s:%s:%s"; // type:status:sort
    private static final String POSTS_ZSET_KEY_FMT = "post:zset:%s:%s:%s"; // sort:type:status
    private static final long LIST_TTL_SECONDS = 60; // 列表缓存TTL
    private static final long TOTAL_TTL_SECONDS = 300; // 总数缓存TTL
    private static final int ZSET_PREFILL_LIMIT = 2000; // ZSet预热最大条数

    @Override
    public Result getPosts(Integer page, Integer size, String type, String status, String sort) throws InterruptedException {
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 10;
        if (StrUtil.isBlank(sort)) sort = "latest";

        String typeKey = StrUtil.isBlank(type) ? "-" : type;
        String statusKey = StrUtil.isBlank(status) ? "-" : status;
        String listKey = String.format(POSTS_LIST_KEY_FMT, typeKey, statusKey, sort, page, size);
        String totalKey = String.format(POSTS_TOTAL_KEY_FMT, typeKey, statusKey, sort);

        // 先尝试读取列表缓存
        String cachedListJson = stringRedisTemplate.opsForValue().get(listKey);
        if (StrUtil.isNotBlank(cachedListJson)) {
            List<PostListItemVO> rows = JSONUtil.toList(JSONUtil.parseArray(cachedListJson), PostListItemVO.class);
            Long total = null;
            String totalStr = stringRedisTemplate.opsForValue().get(totalKey);
            if (StrUtil.isNotBlank(totalStr)) {
                try { total = Long.parseLong(totalStr); } catch (Exception ignore) {}
            }
            // 如果总数不存在，退化为当前页大小，下一次会恢复
            if (total == null) total = (long) rows.size();
            PageResult pageResult = PageResult.builder().total(total).rows(rows).build();
            return Result.success(pageResult);
        }

        // 缓存未命中，尝试加锁并异步恢复缓存；返回提示
        String lockKey = "redisLock:" + listKey;
        boolean locked = redissonClient.getLock(lockKey).tryLock(-1, 30, TimeUnit.SECONDS);
        if (locked) {
            try {
                final int offset = (page - 1) * size;
                String finalSort = sort;
                Integer finalSize = size;
                threadPoolConfig.threadPoolExecutor().execute(() -> {
                    try {
                        // 查询数据库分页数据
                        List<Post> posts = postsMapper.selectPage(type, status, finalSort, offset, finalSize);
                        List<PostListItemVO> items = new ArrayList<>();
                        for (Post p : posts) {
                            // 作者信息
                            UserProfile profile = userMapper.getProfile(p.getUserId());
                            String username = profile != null ? profile.getUsername() : null;
                            String avatar = profile != null ? profile.getAvatar() : null;
                            // 话题信息
                            String topicName = null;
                            if (p.getTopicId() != null) {
                                TopicVO topicVO = topicMapper.getTopicById(p.getTopicId().intValue());
                                topicName = topicVO != null ? topicVO.getName() : null;
                            }
                            // 附件
                            List<Attachment> attachments = attachmentMapper.selectByBusiness("post", p.getId());
                            List<AttachmentLite> liteAttachments = new ArrayList<>();
                            for (Attachment a : attachments) {
                                liteAttachments.add(AttachmentLite.builder()
                                        .fileUrl(a.getFileUrl())
                                        .fileType(a.getFileType())
                                        .fileName(a.getFileName())
                                        .build());
                            }

                            PostListItemVO vo = PostListItemVO.builder()
                                    .id(p.getId())
                                    .title(p.getTitle())
                                    .content(p.getContent())
                                    .userId(p.getUserId())
                                    .authorUsername(username)
                                    .authorAvatar(avatar)
                                    .topicId(p.getTopicId())
                                    .topicName(topicName)
                                    .likeCount(p.getLikeCount())
                                    .commentCount(p.getCommentCount())
                                    .shareCount(p.getShareCount())
                                    .viewCount(p.getViewCount())
                                    .createdAt(p.getCreatedAt())
                                    .updatedAt(p.getUpdatedAt())
                                    .attachments(liteAttachments)
                                    .build();
                            items.add(vo);
                        }

                        String json = JSONUtil.toJsonStr(items);
                        stringRedisTemplate.opsForValue().set(listKey, json, LIST_TTL_SECONDS, TimeUnit.SECONDS);
                        long total = postsMapper.count(type, status);
                        stringRedisTemplate.opsForValue().set(totalKey, String.valueOf(total), TOTAL_TTL_SECONDS, TimeUnit.SECONDS);

                        // ZSet 分层缓存恢复
                        try {
                            String zsetKeyLatest = String.format(POSTS_ZSET_KEY_FMT, finalSort, typeKey, statusKey);
                            for (Post p : posts) {
                                double scoreLatest = p.getCreatedAt() == null ? 0D :
                                        p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                                stringRedisTemplate.opsForZSet().add(zsetKeyLatest, String.valueOf(p.getId()), scoreLatest);
                                double scoreHot = p.getViewCount() == null ? 0D : p.getViewCount().doubleValue();
                                String zsetKeyHot = String.format(POSTS_ZSET_KEY_FMT, "hot", typeKey, statusKey);
                                stringRedisTemplate.opsForZSet().add(zsetKeyHot, String.valueOf(p.getId()), scoreHot);
                            }
                        } catch (Exception e) {
                            log.warn("Restore ZSet cache failed: sort={}, type={}, status={}", finalSort, typeKey, statusKey, e);
                        }

                        // Bloom 过滤器维护（用户、话题、帖子）
                        try {
                            RBloomFilter<Long> postBloom = redissonClient.getBloomFilter("bf:post:id");
                            postBloom.tryInit(10_000_000L, 0.03);
                            RBloomFilter<Long> userBloom = redissonClient.getBloomFilter("bf:user:id");
                            userBloom.tryInit(10_000_000L, 0.03);
                            RBloomFilter<Long> topicBloom = redissonClient.getBloomFilter("bf:topic:id");
                            topicBloom.tryInit(5_000_000L, 0.03);
                            for (Post p : posts) {
                                if (p.getId() != null) postBloom.add(p.getId());
                                if (p.getUserId() != null) userBloom.add(p.getUserId());
                                if (p.getTopicId() != null) topicBloom.add(p.getTopicId());
                            }
                        } catch (Exception e) {
                            log.warn("Restore bloom filters failed: type={}, status={}, sort={}", typeKey, statusKey, finalSort, e);
                        }
                    } catch (Exception e) {
                        log.error("Restore posts cache failed for key {}", listKey, e);
                    }
                });
            } finally {
                redissonClient.getLock(lockKey).unlock();
            }
            return Result.success("哎呀，一不小心走心了，再试试吧");
        } else {
            return Result.success("哎呀，一不小心走心了，再试试吧");
        }
    }

    @Override
    public Result getPostsScroll(Integer size,
                                 String type,
                                 String status,
                                 String sort,
                                 Long max,
                                 Integer offset) throws InterruptedException {
        if (size == null || size < 1) size = 10;
        if (offset == null || offset < 0) offset = 0;
        if (StrUtil.isBlank(sort)) sort = "latest";

        String typeKey = StrUtil.isBlank(type) ? "-" : type;
        String statusKey = StrUtil.isBlank(status) ? "-" : status;
        String zsetKey = String.format(POSTS_ZSET_KEY_FMT, sort, typeKey, statusKey);

        // 若 ZSet 尚未预热或为空，尝试从 DB 预热一批
        Long zsetSize = stringRedisTemplate.opsForZSet().size(zsetKey);
        if (zsetSize == null || zsetSize == 0) {
            try {
                if ("hot".equals(sort)) {
                    List<Post> hotSeed = postsMapper.selectPage(type, status, "hot", 0, ZSET_PREFILL_LIMIT);
                    for (Post p : hotSeed) {
                        double score = p.getViewCount() == null ? 0D : p.getViewCount().doubleValue();
                        stringRedisTemplate.opsForZSet().add(zsetKey, String.valueOf(p.getId()), score);
                    }
                } else {
                    List<Post> latest = postsMapper.selectLatestIds(type, status, ZSET_PREFILL_LIMIT);
                    for (Post p : latest) {
                        double score = (p.getCreatedAt() == null ? 0D : p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                        stringRedisTemplate.opsForZSet().add(zsetKey, String.valueOf(p.getId()), score);
                    }
                }
            } catch (Exception e) {
                log.warn("Prefill ZSet failed: {}", zsetKey, e);
            }
        }

        double maxScore = (max == null) ? ("hot".equals(sort) ? Double.MAX_VALUE : System.currentTimeMillis()) : max.doubleValue();
        Set<String> idStrSet;
        try {
            idStrSet = stringRedisTemplate.opsForZSet()
                    .reverseRangeByScore(zsetKey, 0, maxScore, offset, size);
        } catch (Exception e) {
            log.error("ZSet range failed: key={}, max={}, offset={}, size={}", zsetKey, max, offset, size, e);
            return Result.error("滚动查询失败");
        }
        if (idStrSet == null || idStrSet.isEmpty()) {
            return Result.success(ScrollResult.builder().data(new ArrayList<>()).max(max).scroll(offset).build());
        }

        List<Long> ids = idStrSet.stream().map(Long::valueOf).collect(Collectors.toList());

        // 根据 id 批量查详情
        List<Post> posts = postsMapper.selectByIds(ids);
        List<PostListItemVO> items = new ArrayList<>();
        double nextMax = maxScore;
        for (Post p : posts) {
            // 作者信息
            UserProfile profile = userMapper.getProfile(p.getUserId());
            String username = profile != null ? profile.getUsername() : null;
            String avatar = profile != null ? profile.getAvatar() : null;
            // 话题信息
            String topicName = null;
            if (p.getTopicId() != null) {
                TopicVO topicVO = topicMapper.getTopicById(p.getTopicId().intValue());
                topicName = topicVO != null ? topicVO.getName() : null;
            }
            // 附件（轻量）
            List<Attachment> attachments = attachmentMapper.selectByBusiness("post", p.getId());
            List<AttachmentLite> liteAttachments = new ArrayList<>();
            for (Attachment a : attachments) {
                liteAttachments.add(AttachmentLite.builder()
                        .fileUrl(a.getFileUrl())
                        .fileType(a.getFileType())
                        .fileName(a.getFileName())
                        .build());
            }

            PostListItemVO vo = PostListItemVO.builder()
                    .id(p.getId())
                    .title(p.getTitle())
                    .content(p.getContent())
                    .userId(p.getUserId())
                    .authorUsername(username)
                    .authorAvatar(avatar)
                    .topicId(p.getTopicId())
                    .topicName(topicName)
                    .likeCount(p.getLikeCount())
                    .commentCount(p.getCommentCount())
                    .shareCount(p.getShareCount())
                    .viewCount(p.getViewCount())
                    .createdAt(p.getCreatedAt())
                    .updatedAt(p.getUpdatedAt())
                    .attachments(liteAttachments)
                    .build();
            items.add(vo);

            // 计算 nextMax 供游标式分页继续
            double score = "hot".equals(sort)
                    ? (p.getViewCount() == null ? 0D : p.getViewCount().doubleValue())
                    : (p.getCreatedAt() == null ? 0D : p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            if (score < nextMax) nextMax = score;
        }

        // Bloom 过滤器维护（在滚动查询时亦补充）
        try {
            RBloomFilter<Long> postBloom = redissonClient.getBloomFilter("bf:post:id");
            postBloom.tryInit(10_000_000L, 0.03);
            RBloomFilter<Long> userBloom = redissonClient.getBloomFilter("bf:user:id");
            userBloom.tryInit(10_000_000L, 0.03);
            RBloomFilter<Long> topicBloom = redissonClient.getBloomFilter("bf:topic:id");
            topicBloom.tryInit(5_000_000L, 0.03);
            for (Post p : posts) {
                if (p.getId() != null) postBloom.add(p.getId());
                if (p.getUserId() != null) userBloom.add(p.getUserId());
                if (p.getTopicId() != null) topicBloom.add(p.getTopicId());
            }
        } catch (Exception e) {
            log.warn("Scroll bloom fill failed: sort={}, type={}, status={}", sort, typeKey, statusKey, e);
        }

        ScrollResult scrollResult = ScrollResult.builder()
                .data(items)
                .max((long) nextMax)
                .scroll(offset + items.size())
                .build();
        return Result.success(scrollResult);
    }
}