package com.tmd.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.tmd.config.ThreadPoolConfig;
import com.tmd.entity.dto.*;
import com.tmd.mapper.*;
import com.tmd.service.AttachmentService;
import com.tmd.service.PostsService;
import com.tmd.service.TopicService;
import com.tmd.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tmd.constants.common.ERROR_CODE;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostsServiceImpl implements PostsService {

    private final PostsMapper postsMapper;
    private final AttachmentMapper attachmentMapper;
    private final UserMapper userMapper;
    private final TopicMapper topicMapper;
    private final LikesMapper likesMapper;
    private final FavoriteMapper favoriteMapper;
    private final PostCommentMapper postCommentMapper;

    @Autowired
    private AttachmentService attachmentService;
    @Autowired
    private TopicService topicService;
    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ThreadPoolConfig threadPoolConfig;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RestHighLevelClient esClient;
    @Autowired
    private com.tmd.tools.RedisIdWorker redisIdWorker;

    private static final String POSTS_LIST_KEY_FMT = "post:list:%s:%s:%s:%d:%d"; // type:status:sort:page:size
    private static final String POSTS_TOTAL_KEY_FMT = "post:total:%s:%s:%s"; // type:status:sort
    private static final String POSTS_ZSET_KEY_FMT = "post:zset:%s:%s:%s"; // sort:type:status
    private static final long LIST_TTL_SECONDS = 200; // 列表缓存TTL
    private static final long TOTAL_TTL_SECONDS = 300; // 总数缓存TTL
    private static final int ZSET_PREFILL_LIMIT = 2000; // ZSet预热最大条数
    private static final String INDEX_POSTS = "posts";
    private static final int RATE_LIMIT_LIST_THRESHOLD = 50;
    private static final int RATE_LIMIT_LIST_WINDOW_SECONDS = 1;
    private static final int RATE_LIMIT_CREATE_THRESHOLD = 5;
    private static final int RATE_LIMIT_CREATE_WINDOW_SECONDS = 1;
    private static final String SHARE_TOKEN_KEY_FMT = "share:token:%s";
    private static final String POST_VIEW_PV_KEY_FMT = "post:view:pv:%d";
    private static final String POST_VIEW_UV_KEY_FMT = "post:view:uv:%d";
    private static final String POST_VIEW_SEEN_KEY_FMT = "post:view:seen:%d:%d";
    private static final String POST_VIEW_DAILY_KEY_FMT = "post:view:daily:%s";
    private static final long VIEW_DEDUPE_TTL_MINUTES = 5;
    private static final String POST_VIEW_FLUSH_KEY_FMT = "post:view:flush:%d";
    private static final int POST_VIEW_FLUSH_THRESHOLD = 50;

    private static final String POST_LIKE_USERS_SET_FMT = "post:like:users:%d";
    private static final String USER_LIKES_SET_FMT = "user:likes:set:%d";
    private static final String POST_LIKE_FLUSH_KEY_FMT = "post:like:flush:%d";
    private static final int POST_LIKE_FLUSH_THRESHOLD = 50;

    private static final String POST_FAV_USERS_SET_FMT = "post:fav:users:%d";
    private static final String USER_FAVS_SET_FMT = "user:favs:set:%d";
    private static final String POST_FAV_FLUSH_KEY_FMT = "post:fav:flush:%d";
    private static final int POST_FAV_FLUSH_THRESHOLD = 50;

    /**
     * 缓存帖子列表到Redis，带有重试机制
     */
    public void cachePostsList(String sort, String typeKey, String statusKey, List<PostListItemVO> items,
            String type, String status, String listKey, String totalKey, long total) {
        // 使用手动重试机制替代 @Retryable 注解
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("开始缓存帖子列表: sort={}, type={}, status={}, attempt={}", sort, typeKey, statusKey, attempt);
                String json = JSONUtil.toJsonStr(items);
                stringRedisTemplate.opsForValue().set(listKey, json, ttlJitter(LIST_TTL_SECONDS), TimeUnit.SECONDS);

                stringRedisTemplate.opsForValue().set(totalKey, String.valueOf(total), ttlJitter(TOTAL_TTL_SECONDS),
                        TimeUnit.SECONDS);
                log.info("帖子列表缓存成功: sort={}, type={}, status={}, listKey={}, totalKey={}", sort, typeKey, statusKey,
                        listKey,
                        totalKey);
                return; // 成功则返回
            } catch (Exception e) {
                log.warn("缓存帖子列表失败，第{}次尝试，错误: {}", attempt, e.getMessage());
                if (attempt == maxAttempts) {
                    log.error("缓存帖子列表最终失败: sort={}, type={}, status={}", sort, typeKey, statusKey, e);
                    throw e; // 最后一次尝试失败，抛出异常
                }
                // 指数退避
                try {
                    Thread.sleep(1000L * (1L << (attempt - 1))); // 1s, 2s, 4s
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }

    @Override
    public Result getPosts(Integer page, Integer size, String type, String status, String sort)
            throws InterruptedException {
        if (page == null || page < 1)
            page = 1;
        if (size == null || size < 1)
            size = 10;
        if (StrUtil.isBlank(sort))
            sort = "latest";

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
                try {
                    total = Long.parseLong(totalStr);
                } catch (Exception ignore) {
                }
            }
            // 如果总数不存在，退化为当前页大小，下一次会恢复
            if (total == null)
                total = (long) rows.size();
            PageResult pageResult = PageResult.builder().total(total).rows(rows).build();
            return Result.success(pageResult);
        }
        List<PostListItemVO> items = new ArrayList<>();
        AtomicLong total = new AtomicLong();
        log.info("为什么不在getPosts方法里面打印");
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
                        log.error("为啥不打印消息呢");
                        log.error("我到这里了。准备开始查询");
                        // 查询数据库分页数据
                        List<Post> posts = postsMapper.selectPage(type, status, finalSort, offset, finalSize);

                        // 批量查询本页所有帖子的附件，减少 N 次单查
                        List<Long> postIdsBatch = posts.stream().map(Post::getId).collect(Collectors.toList());
                        log.error("批量查询帖子附件: postIds={}", postIdsBatch);
                        List<Attachment> allAttachments = attachmentService.getAttachmentsByBusinessBatch("post",
                                postIdsBatch);
                        java.util.Map<Long, java.util.List<Attachment>> attMap = (allAttachments == null)
                                ? new java.util.HashMap<>()
                                : allAttachments.stream()
                                        .filter(a -> a.getBusinessId() != null)
                                        .collect(java.util.stream.Collectors.groupingBy(Attachment::getBusinessId));

                        log.error("批量查询帖子附件结果: attMap={}", attMap);
                        for (Post p : posts) {
                            // 作者信息（走缓存）
                            UserProfile profile = userMapper.getProfile(p.getUserId());
                            String username = profile != null ? profile.getUsername() : null;
                            String avatar = profile != null ? profile.getAvatar() : null;
                            // 话题信息
                            String topicName = null;
                            if (p.getTopicId() != null) {
                                TopicVO topicVO = topicService.getTopicCachedById(p.getTopicId().intValue());
                                topicName = topicVO != null ? topicVO.getName() : null;
                            }
                            // 附件（走缓存，批量结果映射）
                            List<Attachment> attachments = attMap.getOrDefault(p.getId(),
                                    java.util.Collections.emptyList());
                            List<AttachmentLite> liteAttachments = new ArrayList<>();
                            for (Attachment a : attachments) {
                                liteAttachments.add(AttachmentLite.builder()
                                        .fileUrl(a.getFileUrl())
                                        .fileType(a.getFileType())
                                        .fileName(a.getFileName())
                                        .build());
                            }
                            // 获取Redis中的增量数据（点赞、评论、阅读）
                            String likeKey = String.format(POST_LIKE_FLUSH_KEY_FMT, p.getId());
                            String commentKey = String.format(POST_COMMENT_FLUSH_KEY_FMT, p.getId());
                            String viewKey = String.format(POST_VIEW_FLUSH_KEY_FMT, p.getId());

                            List<String> keys = java.util.Arrays.asList(likeKey, commentKey, viewKey);
                            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

                            int likeDelta = 0;
                            int commentDelta = 0;
                            int viewDelta = 0;

                            if (values != null && values.size() == 3) {
                                if (StrUtil.isNotBlank(values.get(0)))
                                    likeDelta = Integer.parseInt(values.get(0));
                                if (StrUtil.isNotBlank(values.get(1)))
                                    commentDelta = Integer.parseInt(values.get(1));
                                if (StrUtil.isNotBlank(values.get(2)))
                                    viewDelta = Integer.parseInt(values.get(2));
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
                                    .likeCount((p.getLikeCount() == null ? 0 : p.getLikeCount()) + likeDelta)
                                    .commentCount(
                                            (p.getCommentCount() == null ? 0 : p.getCommentCount()) + commentDelta)
                                    .shareCount(p.getShareCount() == null ? 0 : p.getShareCount())
                                    .viewCount((p.getViewCount() == null ? 0 : p.getViewCount()) + viewDelta)
                                    .createdAt(p.getCreatedAt())
                                    .updatedAt(p.getUpdatedAt())
                                    .attachments(liteAttachments)
                                    .build();
                            items.add(vo);
                        }
                        // 手动重试
                        try {
                            long total1 = postsMapper.count(type, status);
                            total.set(total1);
                            cachePostsList(finalSort, typeKey, statusKey, items, type, status, listKey, totalKey,
                                    total1);
                        } catch (Exception e) {
                            log.error("Cache posts list failed after all retries: sort={}, type={}, status={}",
                                    finalSort, typeKey,
                                    statusKey, e);
                        }

                        /*
                         * try {
                         * // 获取本类的代理对象
                         * PostsServiceImpl proxy = (PostsServiceImpl) AopContext.currentProxy();
                         * proxy.cachePostsList(finalSort, typeKey, statusKey, items, type, status,
                         * listKey, totalKey);
                         * } catch (Exception e) {
                         * log.
                         * error("Cache posts list failed after all retries: sort={}, type={}, status={}"
                         * ,
                         * finalSort, typeKey,
                         * statusKey, e);
                         * }
                         */
                        // ZSet 分层缓存恢复
                        // try {
                        // String zsetKeyLatest = String.format(POSTS_ZSET_KEY_FMT, finalSort, typeKey,
                        // statusKey);
                        // for (Post p : posts) {
                        // double scoreLatest = p.getCreatedAt() == null ? 0D
                        // : p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        // stringRedisTemplate.opsForZSet().add(zsetKeyLatest,
                        // String.valueOf(p.getId()),
                        // scoreLatest);
                        // double scoreHot = p.getViewCount() == null ? 0D :
                        // p.getViewCount().doubleValue();
                        // String zsetKeyHot = String.format(POSTS_ZSET_KEY_FMT, "hot", typeKey,
                        // statusKey);
                        // stringRedisTemplate.opsForZSet().add(zsetKeyHot, String.valueOf(p.getId()),
                        // scoreHot);
                        // }
                        // } catch (Exception e) {
                        // log.warn("Restore ZSet cache failed: sort={}, type={}, status={}", finalSort,
                        // typeKey,
                        // statusKey, e);
                        // }

                        // Bloom 过滤器维护（用户、话题、帖子）
                        try {
                            RBloomFilter<Long> postBloom = redissonClient.getBloomFilter("bf:post:id");
                            postBloom.tryInit(10_000_000L, 0.03);
                            RBloomFilter<Long> userBloom = redissonClient.getBloomFilter("bf:user:id");
                            userBloom.tryInit(10_000_000L, 0.03);
                            RBloomFilter<Long> topicBloom = redissonClient.getBloomFilter("bf:topic:id");
                            topicBloom.tryInit(5_000_000L, 0.03);
                            for (Post p : posts) {
                                if (p.getId() != null)
                                    postBloom.add(p.getId());
                                if (p.getUserId() != null)
                                    userBloom.add(p.getUserId());
                                if (p.getTopicId() != null)
                                    topicBloom.add(p.getTopicId());
                            }
                        } catch (Exception e) {
                            log.warn("Restore bloom filters failed: type={}, status={}, sort={}", typeKey, statusKey,
                                    finalSort, e);
                        }
                    } catch (Exception e) {
                        log.error("Restore posts cache failed for key {}", listKey, e);
                    }
                });
            } finally {
                redissonClient.getLock(lockKey).unlock();
            }
            PageResult pageResult = PageResult.builder()
                    .rows(items)
                    .total(total.get())
                    .build();
            return Result.success(pageResult);
        } else {
            PageResult pageResult = PageResult.builder()
                    .rows(Collections.emptyList())
                    .total(0L)
                    .build();
            return Result.success(pageResult);
        }
    }

    private long ttlJitter(long baseSeconds) {
        long maxExtra = Math.max(1, baseSeconds / 5);
        long extra = ThreadLocalRandom.current().nextLong(0, maxExtra + 1);
        return baseSeconds + extra;
    }

    public Result recordView(Long postId) {
        try {
            if (postId == null || postId <= 0)
                return Result.error("参数错误");

            // Bloom过滤器快速校验帖子是否存在（降低无效写）
            try {
                RBloomFilter<Long> postBloom = redissonClient.getBloomFilter("bf:post:id");
                postBloom.tryInit(10_000_000L, 0.03);
                if (!postBloom.contains(postId)) {
                    // 允许旁路计数以免影响体验，但不做UV
                    return Result.error("帖子不存在或已删除");
                }
            } catch (Exception ignore) {
            }

            Long uid = com.tmd.tools.BaseContext.get();
            boolean hasUser = uid != null && uid > 0;
            boolean firstInWindow = false;
            if (hasUser) {
                String seenKey = String.format(POST_VIEW_SEEN_KEY_FMT, postId, uid);
                try {
                    firstInWindow = Boolean.TRUE.equals(
                            stringRedisTemplate.opsForValue().setIfAbsent(seenKey, "1", VIEW_DEDUPE_TTL_MINUTES,
                                    java.util.concurrent.TimeUnit.MINUTES));
                } catch (Exception e) {
                    log.warn("Set post view seen failed: postId={}, uid={}", postId, uid, e);
                }
            }

            final boolean doUv = hasUser && firstInWindow;

            // 高QPS下减少RTT：使用pipeline合并PV、UV、热度增量、日统计
            stringRedisTemplate.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
                @SuppressWarnings("unchecked")
                @Override
                public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                    try {
                        String pvKey = String.format(POST_VIEW_PV_KEY_FMT, postId);
                        operations.opsForValue().increment(pvKey, 1);

                        if (doUv) {
                            String uvKey = String.format(POST_VIEW_UV_KEY_FMT, postId);
                            operations.opsForHyperLogLog().add(uvKey, String.valueOf(uid));
                        }

                        // 不使用ZSet热度排序增量，简化为仅记录PV/UV与日统计
                        //
                        // String day = java.time.LocalDate.now()
                        // .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                        // String dayKey = String.format(POST_VIEW_DAILY_KEY_FMT, day);
                        // operations.opsForHash().increment(dayKey, String.valueOf(postId), 1L);
                        // operations.expire(dayKey, 7, java.util.concurrent.TimeUnit.DAYS);

                        // 记录待落库增量（轻量批量刷盘）
                        String flushKey = String.format(POST_VIEW_FLUSH_KEY_FMT, postId);
                        operations.opsForValue().increment(flushKey, 1);
                    } catch (Exception e) {
                        log.error("Record post view failed{}", e.getMessage());
                    }
                    return null;
                }
            });

            // 条件落库：达到阈值时把增量刷到 MySQL，避免每次写库
            threadPoolConfig.threadPoolExecutor().execute(() -> {
                try {
                    try {
                        String flushKey = String.format(POST_VIEW_FLUSH_KEY_FMT, postId);
                        String val = stringRedisTemplate.opsForValue().get(flushKey);
                        int pending = 0;
                        if (StrUtil.isNotBlank(val)) {
                            try {
                                pending = Integer.parseInt(val);
                            } catch (Exception ignore) {
                            }
                        }
                        if (pending >= POST_VIEW_FLUSH_THRESHOLD) {
                            String lockKey = "lock:post:view:flush:" + postId;
                            var lock = redissonClient.getLock(lockKey);
                            boolean ok = false;
                            try {
                                ok = lock.tryLock(5, 10, java.util.concurrent.TimeUnit.SECONDS);
                            } catch (InterruptedException ignored) {
                            }
                            if (ok) {
                                try {
                                    String cur = stringRedisTemplate.opsForValue().get(flushKey);
                                    int delta = 0;
                                    if (StrUtil.isNotBlank(cur)) {
                                        try {
                                            delta = Integer.parseInt(cur);
                                        } catch (Exception ignore) {
                                        }
                                    }
                                    if (delta > 0) {
                                        postsMapper.incrViewCount(postId, delta);
                                        try {
                                            java.util.Map<String, Object> params = new java.util.HashMap<>();
                                            params.put("viewDelta", delta);
                                            Script script = new Script(ScriptType.INLINE, "painless",
                                                    "ctx._source.viewCount = (ctx._source.viewCount != null ? ctx._source.viewCount : 0) + params.viewDelta",
                                                    params);
                                            UpdateRequest ur = new UpdateRequest(INDEX_POSTS, String.valueOf(postId))
                                                    .script(script);
                                            esClient.update(ur, RequestOptions.DEFAULT);
                                        } catch (Exception ignore) {
                                        }
                                        try {
                                            stringRedisTemplate.opsForValue().decrement(flushKey, delta);
                                        } catch (Exception ignore) {
                                        }
                                    }
                                } finally {
                                    try {
                                        if (lock.isHeldByCurrentThread()) {
                                            lock.unlock();
                                        }
                                    } catch (Exception e) {
                                        log.error("解锁异常", e);
                                    }
                                }
                            }
                        }

                    } catch (Exception ignore) {
                    }
                } catch (Exception e) {
                    log.error("记录浏览量失败", e);
                }
            });
            return Result.success("记录成功");
        } catch (Exception e) {
            return Result.error("服务器繁忙");
        }
    }

    @Override
    public Result toggleLike(Long postId) {
        try {
            Long uid = com.tmd.tools.BaseContext.get();
            if (uid == null || uid <= 0)
                return Result.error("未登录");
            if (postId == null || postId <= 0)
                return Result.error("参数错误");

            String lockKey = "lock:post:like:" + postId + ":" + uid;
            var lock = redissonClient.getLock(lockKey);
            boolean ok = false;
            try {
                ok = lock.tryLock(3, 5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            if (!ok)
                return Result.error("频繁操作");
            try {
                String postSetKey = String.format(POST_LIKE_USERS_SET_FMT, postId);
                Boolean mem = stringRedisTemplate.opsForSet().isMember(postSetKey, String.valueOf(uid));
                boolean liked;
                if (mem == null || !mem) {
                    int c = likesMapper.exists(uid, "post", postId);
                    liked = c > 0;
                    if (liked)
                        stringRedisTemplate.opsForSet().add(postSetKey, String.valueOf(uid));
                } else {
                    liked = true;
                }

                final boolean targetLike = !liked;
                stringRedisTemplate.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                        String postSet = String.format(POST_LIKE_USERS_SET_FMT, postId);
                        String userSet = String.format(USER_LIKES_SET_FMT, uid);
                        String flushKey = String.format(POST_LIKE_FLUSH_KEY_FMT, postId);
                        if (targetLike) {
                            operations.opsForSet().add(postSet, String.valueOf(uid));
                            operations.opsForSet().add(userSet, String.valueOf(postId));
                            operations.opsForValue().increment(flushKey, 1);
                        } else {
                            operations.opsForSet().remove(postSet, String.valueOf(uid));
                            operations.opsForSet().remove(userSet, String.valueOf(postId));
                            operations.opsForValue().increment(flushKey, -1);
                        }
                        return null;
                    }
                });

                threadPoolConfig.threadPoolExecutor().execute(() -> {
                    try {
                        if (targetLike) {
                            try {
                                likesMapper.insert(uid, "post", postId);
                            } catch (Exception ignore) {
                                log.error("记录点赞失败", ignore);
                            }
                        } else {
                            try {
                                likesMapper.delete(uid, "post", postId);
                            } catch (Exception ignore) {
                                log.error("记录取消点赞失败", ignore);
                            }
                        }
                        // 列表缓存不做双删，依赖集合的精确 SADD/SREM 与读路径按需重建
                        try {
                            String flushKey = String.format(POST_LIKE_FLUSH_KEY_FMT, postId);
                            String val = stringRedisTemplate.opsForValue().get(flushKey);
                            int pending = 0;
                            if (StrUtil.isNotBlank(val)) {
                                try {
                                    pending = Integer.parseInt(val);
                                } catch (Exception ignore) {
                                }
                            }
                            if (pending >= POST_LIKE_FLUSH_THRESHOLD) {
                                String lk = "lock:post:like:flush:" + postId;
                                var l = redissonClient.getLock(lk);
                                boolean ok2 = false;
                                try {
                                    ok2 = l.tryLock(5, 10, java.util.concurrent.TimeUnit.SECONDS);
                                } catch (InterruptedException ignored) {
                                }
                                if (ok2) {
                                    try {
                                        String cur = stringRedisTemplate.opsForValue().get(flushKey);
                                        int delta = 0;
                                        if (StrUtil.isNotBlank(cur)) {
                                            try {
                                                delta = Integer.parseInt(cur);
                                            } catch (Exception ignore) {
                                            }
                                        }
                                        if (delta != 0) {
                                            postsMapper.incrLikeCount(postId, delta);
                                            try {
                                                java.util.Map<String, Object> params = new java.util.HashMap<>();
                                                params.put("likeDelta", delta);
                                                Script script = new Script(ScriptType.INLINE, "painless",
                                                        "ctx._source.likeCount = (ctx._source.likeCount != null ? ctx._source.likeCount : 0) + params.likeDelta",
                                                        params);
                                                UpdateRequest ur = new UpdateRequest(INDEX_POSTS,
                                                        String.valueOf(postId)).script(script);
                                                esClient.update(ur, RequestOptions.DEFAULT);
                                            } catch (Exception ignore) {
                                            }
                                            try {
                                                stringRedisTemplate.opsForValue().decrement(flushKey, delta);
                                            } catch (Exception ignore) {
                                            }
                                        }
                                    } finally {
                                        try {
                                            l.unlock();
                                        } catch (Exception ignore) {
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignore) {
                        }
                    } catch (Exception ignore) {
                    }
                });

                // 保证点击之后用户能看见立刻的变化，维护体验感
                // 写入数据库可以不用立刻，批量刷盘
                Integer likeCountDb = 0;
                try {
                    var post = postsMapper.selectById(postId);
                    if (post != null && post.getLikeCount() != null)
                        likeCountDb = post.getLikeCount();
                } catch (Exception ignore) {
                }
                int pendingDelta = 0;
                try {
                    String fk = String.format(POST_LIKE_FLUSH_KEY_FMT, postId);
                    String v = stringRedisTemplate.opsForValue().get(fk);
                    if (StrUtil.isNotBlank(v))
                        pendingDelta = Integer.parseInt(v);
                } catch (Exception ignore) {
                }
                boolean finalLiked = targetLike;
                // 数据库旧数据加上缓存中缓存的点赞的增量才是当前这个帖子的真正点赞数
                int likeCount = likeCountDb + pendingDelta;
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("isLiked", finalLiked);
                data.put("likeCount", likeCount);
                return Result.success(data);
            } finally {
                try {
                    lock.unlock();
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            return Result.error("服务器繁忙");
        }
    }

    @Override
    public Result getUserLikes(Integer page, Integer size, String targetType) {
        try {
            Long uid = com.tmd.tools.BaseContext.get();
            if (uid == null || uid <= 0)
                return Result.error("未登录");
            if (page == null || page < 1)
                page = 1;
            if (size == null || size < 1)
                size = 20;
            int offset = (page - 1) * size;
            java.util.List<java.util.Map<String, Object>> list = likesMapper.selectByUser(uid, targetType, offset,
                    size);
            return Result.success(list);
        } catch (Exception e) {
            return Result.error("服务器繁忙");
        }
    }

    @Override
    public Result toggleFavorite(Long postId) {
        try {
            Long uid = com.tmd.tools.BaseContext.get();
            if (uid == null || uid <= 0)
                return Result.error("未登录");
            if (postId == null || postId <= 0)
                return Result.error("参数错误");

            String lockKey = "lock:post:fav:" + postId + ":" + uid;
            var lock = redissonClient.getLock(lockKey);
            boolean ok = false;
            try {
                ok = lock.tryLock(3, 5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            if (!ok)
                return Result.error("频繁操作");
            try {
                String postSetKey = String.format(POST_FAV_USERS_SET_FMT, postId);
                Boolean mem = stringRedisTemplate.opsForSet().isMember(postSetKey, String.valueOf(uid));
                boolean collected;
                if (mem == null || !mem) {
                    int c = favoriteMapper.exists(uid, postId);
                    collected = c > 0;
                    if (collected)
                        stringRedisTemplate.opsForSet().add(postSetKey, String.valueOf(uid));
                } else {
                    collected = true;
                }

                final boolean targetCollect = !collected;
                stringRedisTemplate.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                        String postSet = String.format(POST_FAV_USERS_SET_FMT, postId);
                        String userSet = String.format(USER_FAVS_SET_FMT, uid);
                        String flushKey = String.format(POST_FAV_FLUSH_KEY_FMT, postId);
                        if (targetCollect) {
                            operations.opsForSet().add(postSet, String.valueOf(uid));
                            operations.opsForSet().add(userSet, String.valueOf(postId));
                            operations.opsForValue().increment(flushKey, 1);
                        } else {
                            operations.opsForSet().remove(postSet, String.valueOf(uid));
                            operations.opsForSet().remove(userSet, String.valueOf(postId));
                            operations.opsForValue().increment(flushKey, -1);
                        }
                        return null;
                    }
                });

                threadPoolConfig.threadPoolExecutor().execute(() -> {
                    try {
                        if (targetCollect) {
                            try {
                                favoriteMapper.insert(uid, postId);
                            } catch (Exception ignore) {
                            }
                        } else {
                            try {
                                favoriteMapper.delete(uid, postId);
                            } catch (Exception ignore) {
                            }
                        }
                        try {
                            String flushKey = String.format(POST_FAV_FLUSH_KEY_FMT, postId);
                            String val = stringRedisTemplate.opsForValue().get(flushKey);
                            int pending = 0;
                            if (StrUtil.isNotBlank(val)) {
                                try {
                                    pending = Integer.parseInt(val);
                                } catch (Exception ignore) {
                                    throw new RuntimeException("非法刷盘值");
                                }
                            }
                            if (pending >= POST_FAV_FLUSH_THRESHOLD) {
                                String lk = "lock:post:fav:flush:" + postId;
                                var l = redissonClient.getLock(lk);
                                boolean ok2 = false;
                                try {
                                    ok2 = l.tryLock(5, 10, java.util.concurrent.TimeUnit.SECONDS);
                                } catch (InterruptedException ignored) {
                                    log.error("锁异常");
                                    throw new RuntimeException("莫名中断");
                                }
                                if (ok2) {
                                    try {
                                        String cur = stringRedisTemplate.opsForValue().get(flushKey);
                                        int delta = 0;
                                        if (StrUtil.isNotBlank(cur)) {
                                            try {
                                                delta = Integer.parseInt(cur);
                                            } catch (Exception ignore) {
                                            }
                                        }
                                        if (delta != 0) {
                                            postsMapper.incrCollectCount(postId, delta);
                                            try {
                                                java.util.Map<String, Object> params = new java.util.HashMap<>();
                                                params.put("collectDelta", delta);
                                                Script script = new Script(ScriptType.INLINE, "painless",
                                                        "ctx._source.collectCount = (ctx._source.collectCount != null ? ctx._source.collectCount : 0) + params.collectDelta",
                                                        params);
                                                UpdateRequest ur = new UpdateRequest(INDEX_POSTS,
                                                        String.valueOf(postId)).script(script);
                                                esClient.update(ur, RequestOptions.DEFAULT);
                                            } catch (Exception ignore) {
                                                log.error("收藏数更新异常");
                                            }
                                            try {
                                                stringRedisTemplate.opsForValue().decrement(flushKey, delta);
                                            } catch (Exception ignore) {
                                                log.error("扣减数目异常");
                                            }
                                        }
                                    } finally {
                                        try {
                                            l.unlock();
                                        } catch (Exception ignore) {
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignore) {
                        }
                    } catch (Exception ignore) {
                    }
                });

                Integer collectCountDb = 0;
                try {
                    var post = postsMapper.selectById(postId);
                    if (post != null && post.getCollectCount() != null)
                        collectCountDb = post.getCollectCount();
                } catch (Exception ignore) {
                    log.error("获取数据库中帖子收藏数异常");
                    throw new RuntimeException("服务器繁忙");
                }
                int pendingDelta = 0;
                try {
                    String fk = String.format(POST_FAV_FLUSH_KEY_FMT, postId);
                    String v = stringRedisTemplate.opsForValue().get(fk);
                    if (StrUtil.isNotBlank(v))
                        pendingDelta = Integer.parseInt(v);
                } catch (Exception ignore) {
                    log.error("获取收藏数异常");
                    throw new RuntimeException("服务器繁忙");
                }
                boolean finalCollected = targetCollect;
                int collectCount = collectCountDb + pendingDelta;
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("isCollected", finalCollected);
                data.put("collectCount", collectCount);
                return Result.success(data);
            } finally {
                try {
                    lock.unlock();
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            return Result.error("服务器繁忙");
        }
    }

    @Override
    public Result getUserFavorites(Integer page, Integer size) {
        try {
            Long uid = com.tmd.tools.BaseContext.get();
            if (uid == null || uid <= 0)
                return Result.error("未登录");
            if (page == null || page < 1)
                page = 1;
            if (size == null || size < 1)
                size = 20;
            int offset = (page - 1) * size;
            java.util.List<java.util.Map<String, Object>> list = favoriteMapper.selectByUser(uid, offset, size);
            return Result.success(list);
        } catch (Exception e) {
            return Result.error("服务器繁忙");
        }
    }

    // 评论的缓存key就这么设计吧，两个Zset负责存储根评论和子评论，一个Zset负责存储根评论的子评论数量
    //
    private static final String POST_COMMENT_KEY_FMT = "post:comment:%d";
    private static final String POST_COMMENT_CHILD_KEY_FMT = "post:comment:child:%d";
    private static final String POST_COMMENT_CHILD_COUNT_KEY_FMT = "post:comment:child:count:%d";
    private static final String POST_COMMENT_FLUSH_KEY_FMT = "post:comment:flush:%d";
    private static final int POST_COMMENT_FLUSH_THRESHOLD = 50;
    private static final String POST_COMMENT_CONTENT_KEY_FMT = "post:comment:content:%d";
    private static final String POST_COMMENT_DIRTY_SET_KEY = "post:comment:dirty_set";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createComment(Long userId, Long postId, CommentCreateDTO dto) {
        try {
            RBloomFilter<Long> postBloom = redissonClient.getBloomFilter("bf:post:id");
            postBloom.tryInit(10_000_000L, 0.03);
            if (!postBloom.contains(postId)) {
                // 允许旁路计数以免影响体验，但不做UV
                return Result.error("帖子不存在或已删除");
            }
            RBloomFilter<Long> userBloom = redissonClient.getBloomFilter("bf:user:id");
            userBloom.tryInit(10_000_000L, 0.03);
            if (!userBloom.contains(userId)) {
                // 允许旁路计数以免影响体验，但不做UV
                return Result.error("用户不存在或已删除");
            }
        } catch (Exception e) {
            log.error("Bloom filter check failed", e);
            return Result.error("服务器繁忙");
        }
        // 清洗数据
        String content = dto.getContent();
        if (StrUtil.isBlank(content)) {
            return Result.error("请输入内容");
        }
        // XSS 过滤
        content = cn.hutool.http.HtmlUtil.filter(content);

        long l = redisIdWorker.nextId("postcomment");
        PostComment comment = PostComment.builder()
                .id(l)
                .postId(postId)
                .commenterId(userId)
                .createdAt(LocalDateTime.now())
                .dislikes(0)
                .likes(0)
                .parentId(0L)
                .rootId(l)
                .replyCount(0)
                .content(content)
                .build();

        // 构建实体，准备更新数据库，这里我选择批量刷盘
        postCommentMapper.insert(comment);
        // 更新缓存，更新评论和帖子的相关缓存,先更新评论相关的缓存，在更新帖子相关的缓存，最后批量刷盘
        String RootCommentKey = String.format(POST_COMMENT_KEY_FMT, postId);
        stringRedisTemplate.opsForZSet().add(RootCommentKey, String.valueOf(l),
                comment.getCreatedAt().toInstant(ZoneOffset.of("+8")).toEpochMilli());

        // 帖子评论id找内容的字符串缓存
        String contentKey = String.format(POST_COMMENT_CONTENT_KEY_FMT, l);
        stringRedisTemplate.opsForValue().set(contentKey, content, ttlJitter(LIST_TTL_SECONDS), TimeUnit.SECONDS);

        // 标记为脏数据（用于定时任务兜底）
        stringRedisTemplate.opsForSet().add(POST_COMMENT_DIRTY_SET_KEY, String.valueOf(postId));

        // 异步更新帖子评论数量（批量刷盘）
        threadPoolConfig.threadPoolExecutor().execute(() -> {
            try {
                String flushKey = String.format(POST_COMMENT_FLUSH_KEY_FMT, postId);
                // 增加待更新计数
                Long val = stringRedisTemplate.opsForValue().increment(flushKey, 1);
                int pending = val != null ? val.intValue() : 0;

                if (pending >= POST_COMMENT_FLUSH_THRESHOLD) {
                    flushCommentCount(postId);
                }
            } catch (Exception e) {
                log.error("Async flush comment count failed for post: {}", postId, e);
            }
        });

        long commentId = l;
        // 更新完可以选择预热一下
        return Result.success(commentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createReplyComment(Long userId, Long postId, Long commentId, CommentCreateDTO dto) {
        try {
            RBloomFilter<Long> postBloom = redissonClient.getBloomFilter("bf:post:id");
            postBloom.tryInit(10_000_000L, 0.03);
            if (!postBloom.contains(postId)) {
                // 允许旁路计数以免影响体验，但不做UV
                return Result.error("帖子不存在或已删除");
            }
            RBloomFilter<Long> userBloom = redissonClient.getBloomFilter("bf:user:id");
            userBloom.tryInit(10_000_000L, 0.03);
            if (!userBloom.contains(userId)) {
                // 允许旁路计数以免影响体验，但不做UV
                return Result.error("用户不存在或已删除");
            }
        } catch (Exception e) {
            log.error("Bloom filter check failed in reply comment", e);
            return Result.error("服务器繁忙");
        }
        // 清洗数据
        String content = dto.getContent();
        if (StrUtil.isBlank(content)) {
            return Result.error("请输入内容");
        }
        // XSS 过滤
        content = cn.hutool.http.HtmlUtil.filter(content);

        // 查找父评论，确定rootId
        PostComment parent = postCommentMapper.selectById(commentId);
        if (parent == null) {
            return Result.error("回复的评论不存在");
        }

        long rootId = (parent.getParentId() == 0L) ? parent.getId() : parent.getRootId();

        long l = redisIdWorker.nextId("postcomment");
        PostComment comment = PostComment.builder()
                .id(l)
                .postId(postId)
                .commenterId(userId)
                .createdAt(LocalDateTime.now())
                .dislikes(0)
                .likes(0)
                .parentId(commentId)
                .rootId(rootId)
                .replyCount(0)
                .content(content)
                .build();

        // 构建实体，准备更新数据库
        postCommentMapper.insert(comment);

        // 更新缓存：子评论ZSet (rootId维度)
        // Key: post:comment:child:{rootId}
        String childCommentKey = String.format(POST_COMMENT_CHILD_KEY_FMT, rootId);
        stringRedisTemplate.opsForZSet().add(childCommentKey, String.valueOf(l),
                comment.getCreatedAt().toInstant(ZoneOffset.of("+8")).toEpochMilli());

        // 更新缓存：评论内容 (id维度)
        String contentKey = String.format(POST_COMMENT_CONTENT_KEY_FMT, l);
        stringRedisTemplate.opsForValue().set(contentKey, content, ttlJitter(LIST_TTL_SECONDS), TimeUnit.SECONDS);

        // 更新缓存：根评论的子评论数量 (postId维度, member=rootId, score=count)
        String childCountKey = String.format(POST_COMMENT_CHILD_COUNT_KEY_FMT, postId);
        stringRedisTemplate.opsForZSet().incrementScore(childCountKey, String.valueOf(rootId), 1);

        // 标记为脏数据（用于定时任务兜底）
        stringRedisTemplate.opsForSet().add(POST_COMMENT_DIRTY_SET_KEY, String.valueOf(postId));

        // 异步更新逻辑
        threadPoolConfig.threadPoolExecutor().execute(() -> {
            try {
                // 1. 更新帖子维度的评论总数 (批量刷盘)
                String flushKey = String.format(POST_COMMENT_FLUSH_KEY_FMT, postId);
                Long val = stringRedisTemplate.opsForValue().increment(flushKey, 1);
                int pending = val != null ? val.intValue() : 0;
                if (pending >= POST_COMMENT_FLUSH_THRESHOLD) {
                    flushCommentCount(postId);
                }

                // 2. 异步直接更新数据库中根评论的回复数 (简单直接)
                postCommentMapper.incrReplyCount(rootId, 1);
            } catch (Exception e) {
                log.error("Async flush reply count failed for post: {}", postId, e);
            }
        });
        long replyCommentId = l;
        return Result.success(replyCommentId);
    }

    /**
     * 定时任务：每分钟检查并刷新未达到阈值的评论计数
     */

    @Override
    public Result getPostComments(Long postId, Integer page, Integer size, String sortBy) {
        if (postId == null || postId <= 0) {
            return Result.error("参数错误");
        }
        if (page == null || page < 1)
            page = 1;
        if (size == null || size < 1)
            size = 10;
        int offset = (page - 1) * size;

        // 1. Fetch Root Comments (DB Direct)
        List<PostComment> roots = postCommentMapper.selectRoots(postId, offset, size, sortBy);
        long total = postCommentMapper.countRootsByPostId(postId);

        if (roots == null || roots.isEmpty()) {
            return Result.success(PageResult.builder()
                    .rows(Collections.emptyList())
                    .total(total)
                    .currentPage(page)
                    .build());
        }

        // 2. Fetch Replies for these roots (DB Direct)
        List<Long> rootIds = roots.stream().map(PostComment::getId).collect(Collectors.toList());
        List<PostComment> replies = postCommentMapper.selectRepliesByRootIds(rootIds);

        // 3. Map all comments for easy lookup
        Map<Long, PostComment> commentMap = new java.util.HashMap<>();
        for (PostComment c : roots)
            commentMap.put(c.getId(), c);
        for (PostComment c : replies)
            commentMap.put(c.getId(), c);

        // 4. Collect User IDs and fetch User Profiles
        Set<Long> userIds = new java.util.HashSet<>();
        roots.forEach(c -> userIds.add(c.getCommenterId()));
        replies.forEach(c -> userIds.add(c.getCommenterId()));

        Map<Long, UserProfile> userMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            List<UserProfile> profiles = userMapper.selectBatchProfiles(new ArrayList<>(userIds));
            for (UserProfile p : profiles) {
                if (p.getId() != null) {
                    userMap.put(p.getId(), p);
                }
            }
        }

        // 5. Build VOs
        List<PostCommentVO> vos = new ArrayList<>();

        // Group replies by rootId
        Map<Long, List<PostComment>> repliesByRoot = replies.stream()
                .collect(Collectors.groupingBy(PostComment::getRootId));

        for (PostComment root : roots) {
            UserProfile author = userMap.get(root.getCommenterId());

            List<PostCommentVO.ReplyVO> replyVOs = new ArrayList<>();
            List<PostComment> rootReplies = repliesByRoot.getOrDefault(root.getId(), Collections.emptyList());

            for (PostComment child : rootReplies) {
                UserProfile childAuthor = userMap.get(child.getCommenterId());

                // Determine replyToUser
                PostCommentVO.UserInfo replyToUserVO = null;
                if (child.getParentId() != null && !child.getParentId().equals(root.getId())) {
                    PostComment parent = commentMap.get(child.getParentId());
                    if (parent != null) {
                        UserProfile parentAuthor = userMap.get(parent.getCommenterId());
                        if (parentAuthor != null) {
                            replyToUserVO = PostCommentVO.UserInfo.builder()
                                    .userId(parentAuthor.getId())
                                    .username(parentAuthor.getUsername())
                                    .avatar(parentAuthor.getAvatar())
                                    .build();
                        }
                    }
                }

                replyVOs.add(PostCommentVO.ReplyVO.builder()
                        .commentId(child.getId())
                        .content(child.getContent())
                        .likeCount(child.getLikes())
                        .author(childAuthor != null ? PostCommentVO.UserInfo.builder()
                                .userId(childAuthor.getId())
                                .username(childAuthor.getUsername())
                                .avatar(childAuthor.getAvatar())
                                .build() : null)
                        .replyToUser(replyToUserVO)
                        .createdAt(child.getCreatedAt().toString())
                        .isLiked(false)
                        .build());
            }

            vos.add(PostCommentVO.builder()
                    .commentId(root.getId())
                    .content(root.getContent())
                    .likeCount(root.getLikes())
                    .replyCount(root.getReplyCount())
                    .status("normal")
                    .author(author != null ? PostCommentVO.UserInfo.builder()
                            .userId(author.getId())
                            .username(author.getUsername())
                            .avatar(author.getAvatar())
                            .build() : null)
                    .replies(replyVOs)
                    .isLiked(false)
                    .createdAt(root.getCreatedAt().toString())
                    .updatedAt(root.getCreatedAt().toString())
                    .build());
        }

        return Result.success(PageResult.builder()
                .rows(vos)
                .total(total)
                .currentPage(page)
                .build());
    }

    @Override
    public Result updatePosts(PostUpdateDTO postUpdateDTO) {
        Integer postIdInt = postUpdateDTO.getPostId();
        if (postIdInt == null || postIdInt <= 0) {
            return Result.error("参数错误");
        }
        Long postId = postIdInt.longValue();

        // 1. Bloom Filter Check
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("bf:post:id");
        if (!bloomFilter.contains(postId)) {
            return Result.error("帖子不存在");
        }

        // 2. Validate Fields
        if (StrUtil.isBlank(postUpdateDTO.getTitle()) && StrUtil.isBlank(postUpdateDTO.getContent())
                && postUpdateDTO.getTopicId() == null && StrUtil.isBlank(postUpdateDTO.getStatus())) {
            return Result.error("没有需要更新的内容");
        }

        // 3. Check Topic Existence if updating topic
        if (postUpdateDTO.getTopicId() != null) {
            TopicVO topic = topicService.getTopicCachedById(postUpdateDTO.getTopicId());
            if (topic == null) {
                return Result.error("话题不存在");
            }
        }

        // 4. Fetch Existing Post
        Post existingPost = postsMapper.selectById(postId);
        if (existingPost == null) {
            return Result.error("帖子不存在");
        }

        // 5. Update DB
        Post updatePost = new Post();
        updatePost.setId(postId);
        if (StrUtil.isNotBlank(postUpdateDTO.getTitle())) {
            updatePost.setTitle(postUpdateDTO.getTitle());
            existingPost.setTitle(postUpdateDTO.getTitle());
        }
        if (StrUtil.isNotBlank(postUpdateDTO.getContent())) {
            // XSS Filter
            String content = cn.hutool.http.HtmlUtil.filter(postUpdateDTO.getContent());
            updatePost.setContent(content);
            existingPost.setContent(content);
        }
        if (postUpdateDTO.getTopicId() != null) {
            updatePost.setTopicId(postUpdateDTO.getTopicId().longValue());
            existingPost.setTopicId(postUpdateDTO.getTopicId().longValue());
        }
        if (StrUtil.isNotBlank(postUpdateDTO.getStatus())) {
            try {
                PostStatus status = PostStatus.valueOf(postUpdateDTO.getStatus());
                updatePost.setStatus(status);
                existingPost.setStatus(status);
            } catch (IllegalArgumentException e) {
                return Result.error("无效的状态");
            }
        }

        postsMapper.update(updatePost);

        // 6. Async Cache Update (Delete & Preheat & ES)
        threadPoolConfig.threadPoolExecutor().execute(() -> {
            try {
                // 6.1 Delete Caches
                Set<String> keysToDelete = new java.util.HashSet<>();
                // Scan for list keys
                ScanOptions options = ScanOptions.scanOptions().match("post:list:*").count(100).build();
                try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
                    while (cursor.hasNext()) {
                        keysToDelete.add(cursor.next());
                    }
                }
                // Scan for total keys
                ScanOptions optionsTotal = ScanOptions.scanOptions().match("post:total:*").count(100).build();
                try (Cursor<String> cursor = stringRedisTemplate.scan(optionsTotal)) {
                    while (cursor.hasNext()) {
                        keysToDelete.add(cursor.next());
                    }
                }
                // Scan for topic list keys
                ScanOptions optionsTopicList = ScanOptions.scanOptions().match("topic:post:list:*").count(100).build();
                try (Cursor<String> cursor = stringRedisTemplate.scan(optionsTopicList)) {
                    while (cursor.hasNext()) {
                        keysToDelete.add(cursor.next());
                    }
                }
                // Scan for topic total keys
                ScanOptions optionsTopicTotal = ScanOptions.scanOptions().match("topic:post:total:*").count(100)
                        .build();
                try (Cursor<String> cursor = stringRedisTemplate.scan(optionsTopicTotal)) {
                    while (cursor.hasNext()) {
                        keysToDelete.add(cursor.next());
                    }
                }

                if (!keysToDelete.isEmpty()) {
                    stringRedisTemplate.delete(keysToDelete);
                }

                // 6.2 Preheat Cache (Latest List - Global)
                // Reuse logic from createPost
                String typeKey = existingPost.getPostType() == null ? "-" : existingPost.getPostType();
                String statusKey = existingPost.getStatus() == null ? "-" : existingPost.getStatus().name();

                try {
                    int[] sizes = new int[] { 10, 20 };
                    for (int s : sizes) {
                        String listLatest = String.format(POSTS_LIST_KEY_FMT, typeKey, statusKey, "latest", 1, s);
                        List<Post> firstPage = postsMapper.selectPage(typeKey.equals("-") ? null : typeKey,
                                statusKey.equals("-") ? null : statusKey, "latest", 0, s);

                        cacheListHelper(firstPage, listLatest);
                    }
                } catch (Exception e) {
                    log.warn("Update post cache warm failed: id={}", postId, e);
                }

                // 6.3 Preheat Cache (Latest List - Topic)
                if (existingPost.getTopicId() != null) {
                    try {
                        Long topicId = existingPost.getTopicId();
                        int[] sizesT = new int[] { 10, 20 };
                        for (int s : sizesT) {
                            String topicListLatest = String.format("topic:post:list:%d:%s:%s:%d:%d", topicId, statusKey,
                                    "latest", 1, s);
                            List<Post> firstPageByTopic = postsMapper.selectPageByTopic(topicId,
                                    statusKey.equals("-") ? null : statusKey, "latest", 0, s);

                            cacheListHelper(firstPageByTopic, topicListLatest);
                        }
                    } catch (Exception e) {
                        log.warn("Update topic post cache warm failed: id={}", postId, e);
                    }
                }

                // 6.4 Update ES
                try {
                    UserProfile profile = userMapper.getProfile(existingPost.getUserId());
                    String username = profile != null ? profile.getUsername() : null;
                    String avatar = profile != null ? profile.getAvatar() : null;
                    String topicName = null;
                    if (existingPost.getTopicId() != null) {
                        TopicVO topicVO = topicService.getTopicCachedById(existingPost.getTopicId().intValue());
                        topicName = topicVO != null ? topicVO.getName() : null;
                    }
                    java.util.Map<String, Object> doc = new java.util.HashMap<>();
                    doc.put("id", existingPost.getId());
                    doc.put("userId", existingPost.getUserId());
                    doc.put("topicId", existingPost.getTopicId());
                    doc.put("title", existingPost.getTitle());
                    doc.put("content", existingPost.getContent());
                    doc.put("authorUsername", username);
                    doc.put("authorAvatar", avatar);
                    doc.put("topicName", topicName);
                    doc.put("collectCount", existingPost.getCollectCount());
                    doc.put("likeCount", existingPost.getLikeCount());
                    doc.put("commentCount", existingPost.getCommentCount());
                    doc.put("shareCount", existingPost.getShareCount());
                    doc.put("viewCount", existingPost.getViewCount());
                    doc.put("createdAt",
                            existingPost.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                    doc.put("updatedAt", System.currentTimeMillis()); // Update time
                    doc.put("status", existingPost.getStatus().name());

                    IndexRequest indexRequest = new IndexRequest(INDEX_POSTS).id(String.valueOf(existingPost.getId()))
                            .source(doc);
                    esClient.index(indexRequest, RequestOptions.DEFAULT);
                } catch (Exception e) {
                    log.warn("Update ES index failed: id={}", postId, e);
                }

                // 6.5 Maintain Bloom Filter
                try {
                    RBloomFilter<Long> postBloomUpdate = redissonClient.getBloomFilter("bf:post:id");
                    postBloomUpdate.add(postId);
                } catch (Exception e) {
                    log.warn("Update bloom failed: id={}", postId, e);
                }

            } catch (Exception e) {
                log.error("Update post async tasks failed: id={}", postId, e);
            }
        });

        return Result.success("更新成功");
    }

    @Override
    public Result getPost(Long postId) {
        return null;
    }

    @Override
    public Result getPostsByUser(Long userId, Integer page, Integer size, String type, String status, String sort) {
        try {
            if (userId == null || userId <= 0)
                return Result.error("参数错误");
            if (page == null || page < 1)
                page = 1;
            if (size == null || size < 1)
                size = 10;
            if (StrUtil.isBlank(sort))
                sort = "latest";

            int offset = (page - 1) * size;
            List<Post> posts = postsMapper.selectPageByUser(userId, type, status, sort, offset, size);

            List<Long> postIdsBatch = posts.stream().map(Post::getId).collect(Collectors.toList());
            List<Attachment> allAttachments = attachmentService.getAttachmentsByBusinessBatch("post", postIdsBatch);
            Map<Long, List<Attachment>> attMap = (allAttachments == null)
                    ? new java.util.HashMap<>()
                    : allAttachments.stream()
                            .filter(a -> a.getBusinessId() != null)
                            .collect(Collectors.groupingBy(Attachment::getBusinessId));

            List<PostListItemVO> items = new ArrayList<>();
            for (Post p : posts) {
                UserProfile profile = userMapper.getProfile(p.getUserId());
                String username = profile != null ? profile.getUsername() : null;
                String avatar = profile != null ? profile.getAvatar() : null;
                String topicName = null;
                if (p.getTopicId() != null) {
                    TopicVO topicVO = topicService.getTopicCachedById(p.getTopicId().intValue());
                    topicName = topicVO != null ? topicVO.getName() : null;
                }
                List<Attachment> attachments = attMap.getOrDefault(p.getId(), java.util.Collections.emptyList());
                List<AttachmentLite> liteAttachments = new ArrayList<>();
                for (Attachment a : attachments) {
                    liteAttachments.add(AttachmentLite.builder()
                            .fileUrl(a.getFileUrl())
                            .fileType(a.getFileType())
                            .fileName(a.getFileName())
                            .build());
                }

                String likeKey = String.format(POST_LIKE_FLUSH_KEY_FMT, p.getId());
                String commentKey = String.format(POST_COMMENT_FLUSH_KEY_FMT, p.getId());
                String viewKey = String.format(POST_VIEW_FLUSH_KEY_FMT, p.getId());

                List<String> keys = java.util.Arrays.asList(likeKey, commentKey, viewKey);
                List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

                int likeDelta = 0;
                int commentDelta = 0;
                int viewDelta = 0;

                if (values != null && values.size() == 3) {
                    if (StrUtil.isNotBlank(values.get(0)))
                        likeDelta = Integer.parseInt(values.get(0));
                    if (StrUtil.isNotBlank(values.get(1)))
                        commentDelta = Integer.parseInt(values.get(1));
                    if (StrUtil.isNotBlank(values.get(2)))
                        viewDelta = Integer.parseInt(values.get(2));
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
                        .likeCount((p.getLikeCount() == null ? 0 : p.getLikeCount()) + likeDelta)
                        .commentCount((p.getCommentCount() == null ? 0 : p.getCommentCount()) + commentDelta)
                        .shareCount(p.getShareCount() == null ? 0 : p.getShareCount())
                        .viewCount((p.getViewCount() == null ? 0 : p.getViewCount()) + viewDelta)
                        .createdAt(p.getCreatedAt())
                        .updatedAt(p.getUpdatedAt())
                        .attachments(liteAttachments)
                        .build();
                items.add(vo);
            }

            long total = postsMapper.countByUser(userId, type, status);
            PageResult pageResult = PageResult.builder()
                    .rows(items)
                    .total(total)
                    .currentPage(page)
                    .build();
            return Result.success(pageResult);
        } catch (Exception e) {
            return Result.error("服务器繁忙");
        }
    }

    private void cacheListHelper(List<Post> posts, String cacheKey) {
        if (posts == null || posts.isEmpty()) {
            stringRedisTemplate.opsForValue().set(cacheKey, "[]", ttlJitter(LIST_TTL_SECONDS), TimeUnit.SECONDS);
            return;
        }
        List<Long> postIdsBatch = posts.stream().map(Post::getId).collect(Collectors.toList());
        List<Attachment> allAttachments = attachmentService.getAttachmentsByBusinessBatch("post", postIdsBatch);
        java.util.Map<Long, java.util.List<Attachment>> attMap = (allAttachments == null)
                ? new java.util.HashMap<>()
                : allAttachments.stream()
                        .filter(a -> a.getBusinessId() != null)
                        .collect(java.util.stream.Collectors.groupingBy(Attachment::getBusinessId));
        List<PostListItemVO> items = new ArrayList<>();
        for (Post p : posts) {
            UserProfile profile = userMapper.getProfile(p.getUserId());
            String username = profile != null ? profile.getUsername() : null;
            String avatar = profile != null ? profile.getAvatar() : null;
            String topicName = null;
            if (p.getTopicId() != null) {
                TopicVO topicVO = topicService.getTopicCachedById(p.getTopicId().intValue());
                topicName = topicVO != null ? topicVO.getName() : null;
            }
            List<Attachment> attachments = attMap.getOrDefault(p.getId(), java.util.Collections.emptyList());
            List<AttachmentLite> liteAttachments = new ArrayList<>();
            for (Attachment a : attachments) {
                liteAttachments.add(AttachmentLite.builder()
                        .fileUrl(a.getFileUrl())
                        .fileType(a.getFileType())
                        .fileName(a.getFileName())
                        .build());
            }

            // Increments
            String likeKey = String.format(POST_LIKE_FLUSH_KEY_FMT, p.getId());
            String commentKey = String.format(POST_COMMENT_FLUSH_KEY_FMT, p.getId());
            String viewKey = String.format(POST_VIEW_FLUSH_KEY_FMT, p.getId());
            List<String> keys = java.util.Arrays.asList(likeKey, commentKey, viewKey);
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
            int likeDelta = 0;
            int commentDelta = 0;
            int viewDelta = 0;
            if (values != null && values.size() == 3) {
                if (StrUtil.isNotBlank(values.get(0)))
                    likeDelta = Integer.parseInt(values.get(0));
                if (StrUtil.isNotBlank(values.get(1)))
                    commentDelta = Integer.parseInt(values.get(1));
                if (StrUtil.isNotBlank(values.get(2)))
                    viewDelta = Integer.parseInt(values.get(2));
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
                    .likeCount((p.getLikeCount() == null ? 0 : p.getLikeCount()) + likeDelta)
                    .commentCount((p.getCommentCount() == null ? 0 : p.getCommentCount()) + commentDelta)
                    .shareCount(p.getShareCount() == null ? 0 : p.getShareCount())
                    .viewCount((p.getViewCount() == null ? 0 : p.getViewCount()) + viewDelta)
                    .createdAt(p.getCreatedAt())
                    .updatedAt(p.getUpdatedAt())
                    .attachments(liteAttachments)
                    .build();
            items.add(vo);
        }
        String json = JSONUtil.toJsonStr(items);
        stringRedisTemplate.opsForValue().set(cacheKey, json, ttlJitter(LIST_TTL_SECONDS), TimeUnit.SECONDS);
    }

    @Scheduled(cron = "0 0/1 * * * ?")
    public void scheduledFlushCommentCounts() {
        try {
            Set<String> dirtyIds = stringRedisTemplate.opsForSet().members(POST_COMMENT_DIRTY_SET_KEY);
            if (dirtyIds != null && !dirtyIds.isEmpty()) {
                for (String idStr : dirtyIds) {
                    Long postId = Long.valueOf(idStr);
                    threadPoolConfig.threadPoolExecutor().execute(() -> flushCommentCount(postId));
                }
            }
        } catch (Exception ignore) {
        }
    }

    /**
     * 执行评论数刷盘逻辑
     */
    private void flushCommentCount(Long postId) {
        String lockKey = "lock:post:comment:flush:" + postId;
        var lock = redissonClient.getLock(lockKey);
        boolean ok = false;
        try {
            ok = lock.tryLock(5, 10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        if (ok) {
            try {
                String flushKey = String.format(POST_COMMENT_FLUSH_KEY_FMT, postId);
                String cur = stringRedisTemplate.opsForValue().get(flushKey);
                int delta = 0;
                if (StrUtil.isNotBlank(cur)) {
                    try {
                        delta = Integer.parseInt(cur);
                    } catch (Exception ignore) {
                        log.error("Invalid flush count: {}", cur);
                        throw new RuntimeException("Invalid flush count: " + cur);
                    }
                }
                if (delta != 0) {
                    // 更新数据库
                    postsMapper.incrCommentCount(postId, delta);
                    // 更新 ES
                    try {
                        Map<String, Object> params = new java.util.HashMap<>();
                        params.put("commentDelta", delta);
                        Script script = new Script(ScriptType.INLINE, "painless",
                                "ctx._source.commentCount = (ctx._source.commentCount != null ? ctx._source.commentCount : 0) + params.commentDelta",
                                params);
                        UpdateRequest ur = new UpdateRequest(INDEX_POSTS, String.valueOf(postId))
                                .script(script);
                        esClient.update(ur, RequestOptions.DEFAULT);
                    } catch (Exception ignore) {
                    }
                    // 扣减 Redis 计数
                    try {
                        // 如果 delta 为正，扣减 delta (decrement positive) -> 减少计数
                        // 如果 delta 为负，扣减 delta (decrement negative) -> 增加计数（即归零）
                        Long remaining = stringRedisTemplate.opsForValue().decrement(flushKey, delta);
                        // 如果剩余计数为0（或者符号改变，说明处理完了），从脏集合中移除
                        // 简单判断：如果 remaining == 0，则移除
                        if (remaining != null && remaining == 0) {
                            stringRedisTemplate.opsForSet().remove(POST_COMMENT_DIRTY_SET_KEY, String.valueOf(postId));
                        }
                    } catch (Exception ignore) {
                    }
                } else {
                    // 没有待更新的计数，直接移除脏标记
                    stringRedisTemplate.opsForSet().remove(POST_COMMENT_DIRTY_SET_KEY, String.valueOf(postId));
                }
            } finally {
                try {
                    lock.unlock();
                } catch (Exception ignore) {
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteComment(Long userId, Long commentId) {
        if (userId == null || userId == ERROR_CODE) {
            return Result.error("验证失败,非法访问");
        }
        if (commentId == null || commentId <= 0) {
            return Result.error("评论 ID 无效");
        }
        // 检查评论是否存在
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) {
            return Result.error("评论不存在");
        }
        // 检查用户是否有权限删除
        if (!comment.getCommenterId().equals(userId)) {
            return Result.error("没有权限删除此评论");
        }

        Long postId = comment.getPostId();
        List<Long> idsToDelete = new ArrayList<>();
        idsToDelete.add(commentId);

        // 如果是根评论，需要删除所有子评论
        if (comment.getParentId() == 0L) {
            List<Long> childIds = postCommentMapper.selectIdsByRootId(commentId);
            if (childIds != null && !childIds.isEmpty()) {
                idsToDelete.addAll(childIds);
            }
        }
        // 如果是子评论，仅删除自己（已加入 idsToDelete）

        // 批量删除数据库
        postCommentMapper.deleteBatchIds(idsToDelete);

        // 更新缓存
        // 1. 删除内容缓存
        for (Long id : idsToDelete) {
            String contentKey = String.format(POST_COMMENT_CONTENT_KEY_FMT, id);
            stringRedisTemplate.delete(contentKey);
        }

        if (comment.getParentId() == 0L) {
            // 是根评论
            // 2. 删除帖子下的该根评论 (ZSet)
            String rootCommentKey = String.format(POST_COMMENT_KEY_FMT, postId);
            stringRedisTemplate.opsForZSet().remove(rootCommentKey, String.valueOf(commentId));

            // 3. 删除该根评论下的所有子评论 (ZSet)
            String childCommentKey = String.format(POST_COMMENT_CHILD_KEY_FMT, commentId);
            stringRedisTemplate.delete(childCommentKey);

            // 4. 删除帖子下该根评论的子评论计数 (ZSet)
            String childCountKey = String.format(POST_COMMENT_CHILD_COUNT_KEY_FMT, postId);
            stringRedisTemplate.opsForZSet().remove(childCountKey, String.valueOf(commentId));

        } else {
            // 是子评论
            long rootId = comment.getRootId();
            // 2. 从根评论的子评论列表移除 (ZSet)
            String childCommentKey = String.format(POST_COMMENT_CHILD_KEY_FMT, rootId);
            stringRedisTemplate.opsForZSet().remove(childCommentKey, String.valueOf(commentId));

            // 3. 更新根评论的子评论计数 (ZSet)
            String childCountKey = String.format(POST_COMMENT_CHILD_COUNT_KEY_FMT, postId);
            stringRedisTemplate.opsForZSet().incrementScore(childCountKey, String.valueOf(rootId), -1);

            // 4. 更新数据库中根评论的 reply_count (直接减少1)
            // 同步更新以保证数据强一致性（在事务内）
            postCommentMapper.incrReplyCount(rootId, -1);
        }

        // 异步批量刷盘更新帖子总评论数
        int deletedCount = idsToDelete.size();
        threadPoolConfig.threadPoolExecutor().execute(() -> {
            try {
                // 减少待更新计数 (delta 为负数)
                String flushKey = String.format(POST_COMMENT_FLUSH_KEY_FMT, postId);
                // increment 传入负数即为减
                Long val = stringRedisTemplate.opsForValue().increment(flushKey, -deletedCount);

                // 标记脏数据
                stringRedisTemplate.opsForSet().add(POST_COMMENT_DIRTY_SET_KEY, String.valueOf(postId));

                // 触发检查（如果是负数积累多了也需要刷新吗？当然）
                // 绝对值判断？或者只要有值就判断？
                // 之前的逻辑是 pending >= THRESHOLD，现在 pending 可能是负数。
                // 假设 THRESHOLD 是 50。 如果 pending 是 -50，也应该刷。
                long currentPending = val != null ? val : 0;
                if (Math.abs(currentPending) >= POST_COMMENT_FLUSH_THRESHOLD) {
                    flushCommentCount(postId);
                }
            } catch (Exception e) {
                log.error("Async flush comment count failed in delete for post: {}", postId, e);
            }
        });

        return Result.success("评论删除成功");
    }

    @Override
    public Result createPost(Long userId, PostCreateDTO dto) {
        String rk = "rate:post:create:" + userId;
        Long rcv = null;
        try {
            rcv = stringRedisTemplate.opsForValue().increment(rk, 1);
            if (rcv != null && rcv == 1L) {
                stringRedisTemplate.expire(rk, RATE_LIMIT_CREATE_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception ignore) {
        }
        if (rcv != null && rcv > RATE_LIMIT_CREATE_THRESHOLD) {
            return Result.error("请求过于频繁");
        }
        if (userId == null || userId <= 0) {
            return Result.error("验证失败,非法访问");
        }
        if (dto == null || StrUtil.isBlank(dto.getContent())) {
            return Result.error("帖子内容不能为空");
        }

        // 构造 Post 实体并入库
        var now = java.time.LocalDateTime.now();
        Post post = Post.builder()
                .userId(userId)
                .topicId(dto.getTopicId())
                .title(dto.getTitle())
                .content(dto.getContent())
                .postType(dto.getType())
                .status(StrUtil.isBlank(dto.getStatus()) ? PostStatus.draft : PostStatus.valueOf(dto.getStatus()))
                .likeCount(0)
                .commentCount(0)
                .shareCount(0)
                .viewCount(0)
                .publishLocation(dto.getPublishLocation())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .createdAt(now)
                .updatedAt(now)
                .build();

        postsMapper.insert(post);
        if (post.getId() == null) {
            return Result.error("帖子创建失败");
        }
        if (dto.getTopicId() != null && dto.getTopicId() > 0) {
            try {
                topicService.incrementTopicPostCount(dto.getTopicId().longValue(), 1);
            } catch (Exception ignore) {
            }
        }

        // 同步：附件批量入库并与帖子关联
        try {
            if (dto.getAttachments() != null && !dto.getAttachments().isEmpty()) {
                List<Attachment> toSave = new ArrayList<>();
                for (AttachmentLite lite : dto.getAttachments()) {
                    if (lite == null || StrUtil.isBlank(lite.getFileUrl()))
                        continue;
                    String fileId = extractFileIdFromUrl(lite.getFileUrl());
                    String fileName = StrUtil.isNotBlank(lite.getFileName()) ? lite.getFileName()
                            : extractFileNameFromFileId(fileId);
                    String fileType = StrUtil.isNotBlank(lite.getFileType()) ? lite.getFileType()
                            : extractFileTypeFromFileId(fileId);
                    String mimeType = detectMimeTypeFromFileName(fileName);
                    if (StrUtil.isBlank(fileId)) {
                        // 无法解析 fileId 时，跳过该附件，避免违反 NOT NULL 约束
                        continue;
                    }
                    Long id = redisIdWorker.nextId("attachment");
                    Attachment a = Attachment.builder()
                            .id(id)
                            .fileId(fileId)
                            .fileUrl(lite.getFileUrl())
                            .fileName(fileName)
                            .fileSize(null)
                            .fileType(StrUtil.isBlank(fileType) ? "image" : fileType)
                            .mimeType(mimeType)
                            .businessType("post")
                            .businessId(post.getId())
                            .uploaderId(userId)
                            .uploadTime(now)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    toSave.add(a);
                }
                if (!toSave.isEmpty()) {
                    attachmentMapper.batchInsert(toSave);
                }
            }
        } catch (Exception e) {
            log.warn("Create post attachments save failed: id={}", post.getId(), e);
        }

        // 异步：更新缓存、ZSet、Bloom、ES 索引
        threadPoolConfig.threadPoolExecutor().execute(() -> {
            try {
                String typeKey = StrUtil.isBlank(dto.getType()) ? "-" : dto.getType();
                String statusKey = StrUtil.isBlank(dto.getStatus()) ? "-" : dto.getStatus();

                // 不使用 ZSet：改为直接更新/失效传统分页缓存

                // 计数缓存：尝试 +1（如果存在），帖子总数+1(这里根据类型分别缓存了多个组的帖子数量)
                // 我准备来个ttl抖动防止缓存雪崩
                try {
                    String totalLatestKey = String.format(POSTS_TOTAL_KEY_FMT, typeKey, statusKey, "latest");
                    String totalHotKey = String.format(POSTS_TOTAL_KEY_FMT, typeKey, statusKey, "hot");
                    stringRedisTemplate.opsForValue().increment(totalLatestKey);
                    stringRedisTemplate.opsForValue().increment(totalHotKey);
                } catch (Exception e) {
                    log.warn("Create total cache inc failed: id={}", post.getId(), e);
                }

                // 列表缓存首屏：先失效，再预热 latest 的第一页
                try {
                    int[] sizes = new int[] { 10, 20 };
                    for (int s : sizes) {
                        String listLatest = String.format(POSTS_LIST_KEY_FMT, typeKey, statusKey, "latest", 1, s);

                        // 预热 latest 的第一页，避免下次读触发锁和冷启动
                        List<Post> firstPage = postsMapper.selectPage(dto.getType(), dto.getStatus(), "latest", 0, s);
                        // 批量查询该页所有帖子的附件
                        List<Long> postIdsBatch = firstPage.stream().map(Post::getId).collect(Collectors.toList());
                        List<Attachment> allAttachments = attachmentService.getAttachmentsByBusinessBatch("post",
                                postIdsBatch);
                        java.util.Map<Long, java.util.List<Attachment>> attMap = (allAttachments == null)
                                ? new java.util.HashMap<>()
                                : allAttachments.stream()
                                        .filter(a -> a.getBusinessId() != null)
                                        .collect(java.util.stream.Collectors.groupingBy(Attachment::getBusinessId));
                        List<PostListItemVO> items = new ArrayList<>();
                        for (Post p : firstPage) {
                            UserProfile profile = userMapper.getProfile(p.getUserId());
                            String username = profile != null ? profile.getUsername() : null;
                            String avatar = profile != null ? profile.getAvatar() : null;
                            String topicName = null;
                            if (p.getTopicId() != null) {
                                TopicVO topicVO = topicService.getTopicCachedById(p.getTopicId().intValue());
                                topicName = topicVO != null ? topicVO.getName() : null;
                            }
                            List<Attachment> attachments = attMap.getOrDefault(p.getId(),
                                    java.util.Collections.emptyList());
                            List<AttachmentLite> liteAttachments = new ArrayList<>();
                            for (Attachment a : attachments) {
                                liteAttachments.add(AttachmentLite.builder()
                                        .fileUrl(a.getFileUrl())
                                        .fileType(a.getFileType())
                                        .fileName(a.getFileName())
                                        .build());
                            }
                            // 获取Redis中的增量数据（点赞、评论、阅读）
                            String likeKey = String.format(POST_LIKE_FLUSH_KEY_FMT, p.getId());
                            String commentKey = String.format(POST_COMMENT_FLUSH_KEY_FMT, p.getId());
                            String viewKey = String.format(POST_VIEW_FLUSH_KEY_FMT, p.getId());

                            List<String> keys = java.util.Arrays.asList(likeKey, commentKey, viewKey);
                            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

                            int likeDelta = 0;
                            int commentDelta = 0;
                            int viewDelta = 0;

                            if (values != null && values.size() == 3) {
                                if (StrUtil.isNotBlank(values.get(0)))
                                    likeDelta = Integer.parseInt(values.get(0));
                                if (StrUtil.isNotBlank(values.get(1)))
                                    commentDelta = Integer.parseInt(values.get(1));
                                if (StrUtil.isNotBlank(values.get(2)))
                                    viewDelta = Integer.parseInt(values.get(2));
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
                                    .likeCount((p.getLikeCount() == null ? 0 : p.getLikeCount()) + likeDelta)
                                    .commentCount(
                                            (p.getCommentCount() == null ? 0 : p.getCommentCount()) + commentDelta)
                                    .shareCount(p.getShareCount() == null ? 0 : p.getShareCount())
                                    .viewCount((p.getViewCount() == null ? 0 : p.getViewCount()) + viewDelta)
                                    .createdAt(p.getCreatedAt())
                                    .updatedAt(p.getUpdatedAt())
                                    .attachments(liteAttachments)
                                    .build();
                            items.add(vo);
                        }
                        String json = JSONUtil.toJsonStr(items);
                        stringRedisTemplate.opsForValue().set(listLatest, json, ttlJitter(LIST_TTL_SECONDS),
                                TimeUnit.SECONDS);
                    }
                } catch (Exception e) {
                    log.warn("Create list cache warm failed: id={}", post.getId(), e);
                }

                // 话题维度：失效并预热该话题的最新列表第一页与计数
                try {
                    if (post.getTopicId() != null) {
                        Long topicId = post.getTopicId();
                        int[] sizesT = new int[] { 10, 20 };
                        for (int s : sizesT) {
                            String topicListLatest = String.format("topic:post:list:%d:%s:%s:%d:%d", topicId, statusKey,
                                    "latest", 1, s);

                            List<Post> firstPageByTopic = postsMapper.selectPageByTopic(topicId, dto.getStatus(),
                                    "latest", 0, s);
                            // 批量查询话题页所有帖子的附件
                            List<Long> topicPostIds = firstPageByTopic.stream().map(Post::getId)
                                    .collect(Collectors.toList());
                            List<Attachment> topicAllAttachments = attachmentService
                                    .getAttachmentsByBusinessBatch("post", topicPostIds);
                            java.util.Map<Long, java.util.List<Attachment>> topicAttMap = (topicAllAttachments == null)
                                    ? new java.util.HashMap<>()
                                    : topicAllAttachments.stream()
                                            .filter(a -> a.getBusinessId() != null)
                                            .collect(java.util.stream.Collectors.groupingBy(Attachment::getBusinessId));
                            List<PostListItemVO> itemsT = new ArrayList<>();
                            for (Post p : firstPageByTopic) {
                                UserProfile profile = userMapper.getProfile(p.getUserId());
                                String username = profile != null ? profile.getUsername() : null;
                                String avatar = profile != null ? profile.getAvatar() : null;
                                String topicName = null;
                                if (p.getTopicId() != null) {
                                    TopicVO topicVO = topicService.getTopicCachedById(p.getTopicId().intValue());
                                    topicName = topicVO != null ? topicVO.getName() : null;
                                }
                                List<Attachment> attachments = topicAttMap.getOrDefault(p.getId(),
                                        java.util.Collections.emptyList());
                                List<AttachmentLite> liteAttachments = new ArrayList<>();
                                for (Attachment a : attachments) {
                                    liteAttachments.add(AttachmentLite.builder()
                                            .fileUrl(a.getFileUrl())
                                            .fileType(a.getFileType())
                                            .fileName(a.getFileName())
                                            .build());
                                }
                                // 获取Redis中的增量数据（点赞、评论、阅读）
                                String likeKey = String.format(POST_LIKE_FLUSH_KEY_FMT, p.getId());
                                String commentKey = String.format(POST_COMMENT_FLUSH_KEY_FMT, p.getId());
                                String viewKey = String.format(POST_VIEW_FLUSH_KEY_FMT, p.getId());

                                List<String> keys = java.util.Arrays.asList(likeKey, commentKey, viewKey);
                                List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

                                int likeDelta = 0;
                                int commentDelta = 0;
                                int viewDelta = 0;

                                if (values != null && values.size() == 3) {
                                    if (StrUtil.isNotBlank(values.get(0)))
                                        likeDelta = Integer.parseInt(values.get(0));
                                    if (StrUtil.isNotBlank(values.get(1)))
                                        commentDelta = Integer.parseInt(values.get(1));
                                    if (StrUtil.isNotBlank(values.get(2)))
                                        viewDelta = Integer.parseInt(values.get(2));
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
                                        .likeCount((p.getLikeCount() == null ? 0 : p.getLikeCount()) + likeDelta)
                                        .commentCount(
                                                (p.getCommentCount() == null ? 0 : p.getCommentCount()) + commentDelta)
                                        .shareCount(p.getShareCount() == null ? 0 : p.getShareCount())
                                        .viewCount((p.getViewCount() == null ? 0 : p.getViewCount()) + viewDelta)
                                        .createdAt(p.getCreatedAt())
                                        .updatedAt(p.getUpdatedAt())
                                        .attachments(liteAttachments)
                                        .build();
                                itemsT.add(vo);
                            }
                            String jsonT = JSONUtil.toJsonStr(itemsT);
                            stringRedisTemplate.opsForValue().set(topicListLatest, jsonT, ttlJitter(LIST_TTL_SECONDS),
                                    TimeUnit.SECONDS);
                        }

                        // 话题维度的计数尝试 +1（如果存在）
                        try {
                            String topicTotalLatestKey = String.format("topic:post:total:%d:%s:%s", topicId, statusKey,
                                    "latest");
                            String topicTotalHotKey = String.format("topic:post:total:%d:%s:%s", topicId, statusKey,
                                    "hot");
                            stringRedisTemplate.opsForValue().increment(topicTotalLatestKey);
                            stringRedisTemplate.opsForValue().increment(topicTotalHotKey);
                        } catch (Exception e) {
                            log.warn("Create topic total cache inc failed: id={}", post.getId(), e);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Create topic list cache warm failed: id={}", post.getId(), e);
                }

                // Bloom 过滤器维护
                try {
                    RBloomFilter<Long> postBloom = redissonClient.getBloomFilter("bf:post:id");
                    postBloom.tryInit(10_000_000L, 0.03);
                    RBloomFilter<Long> userBloom = redissonClient.getBloomFilter("bf:user:id");
                    userBloom.tryInit(10_000_000L, 0.03);
                    RBloomFilter<Long> topicBloom = redissonClient.getBloomFilter("bf:topic:id");
                    topicBloom.tryInit(5_000_000L, 0.03);
                    postBloom.add(post.getId());
                    userBloom.add(post.getUserId());
                    if (post.getTopicId() != null)
                        topicBloom.add(post.getTopicId());
                } catch (Exception e) {
                    log.warn("Create bloom add failed: id={}", post.getId(), e);
                }

                // ES 索引写入
                try {
                    UserProfile profile = userMapper.getProfile(post.getUserId());
                    String username = profile != null ? profile.getUsername() : null;
                    String avatar = profile != null ? profile.getAvatar() : null;
                    log.info("用户信息为{},{}", username, avatar);
                    String topicName = null;
                    if (post.getTopicId() != null) {
                        TopicVO topicVO = topicService.getTopicCachedById(post.getTopicId().intValue());
                        topicName = topicVO != null ? topicVO.getName() : null;
                    }
                    java.util.Map<String, Object> doc = new java.util.HashMap<>();
                    doc.put("id", post.getId());
                    doc.put("userId", post.getUserId());
                    doc.put("topicId", post.getTopicId());
                    doc.put("title", post.getTitle());
                    doc.put("content", post.getContent());
                    doc.put("authorUsername", username);
                    doc.put("authorAvatar", avatar);
                    doc.put("topicName", topicName);
                    doc.put("collectCount", post.getCollectCount());
                    doc.put("likeCount", post.getLikeCount());
                    doc.put("commentCount", post.getCommentCount());
                    doc.put("shareCount", post.getShareCount());
                    doc.put("viewCount", post.getViewCount());
                    doc.put("createdAt", now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                    doc.put("updatedAt", now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                    doc.put("status", post.getStatus().name());
                    IndexRequest indexRequest = new IndexRequest(INDEX_POSTS).id(String.valueOf(post.getId()))
                            .source(doc);
                    esClient.index(indexRequest, RequestOptions.DEFAULT);
                } catch (Exception e) {
                    log.warn("Create ES index failed: id={}", post.getId(), e);
                }
            } catch (Exception e) {
                log.error("Create post async tasks failed: id={}", post.getId(), e);
            }
        });

        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("postId", post.getId());
        return Result.success(resp);
    }

    // 从 OSS URL 中提取 fileId (objectKey)
    private String extractFileIdFromUrl(String url) {
        if (StrUtil.isBlank(url))
            return null;
        try {
            String path = url;
            if (path.startsWith("https://") || path.startsWith("http://")) {
                int protocolEnd = path.indexOf("://") + 3;
                path = path.substring(protocolEnd);
            }
            int firstSlash = path.indexOf('/');
            if (firstSlash >= 0 && firstSlash < path.length() - 1) {
                return path.substring(firstSlash + 1);
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private String extractFileNameFromFileId(String fileId) {
        if (StrUtil.isBlank(fileId))
            return null;
        try {
            int lastSlash = fileId.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < fileId.length() - 1) {
                return fileId.substring(lastSlash + 1);
            }
            return fileId;
        } catch (Exception ignore) {
            return null;
        }
    }

    private String extractFileTypeFromFileId(String fileId) {
        if (StrUtil.isBlank(fileId))
            return null;
        try {
            int firstSlash = fileId.indexOf('/');
            if (firstSlash > 0) {
                String type = fileId.substring(0, firstSlash);
                if ("image".equals(type) || "video".equals(type)) {
                    return type;
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private String detectMimeTypeFromFileName(String fileName) {
        if (StrUtil.isBlank(fileName))
            return "image/jpeg";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".png"))
            return "image/png";
        if (lower.endsWith(".gif"))
            return "image/gif";
        if (lower.endsWith(".webp"))
            return "image/webp";
        if (lower.endsWith(".mp4"))
            return "video/mp4";
        if (lower.endsWith(".avi"))
            return "video/avi";
        if (lower.endsWith(".mov"))
            return "video/quicktime";
        return "application/octet-stream";
    }

    @Override
    public Result getPostsScroll(Integer size,
            String type,
            String status,
            String sort,
            Long max,
            Integer offset) throws InterruptedException {
        if (size == null || size < 1)
            size = 10;
        if (offset == null || offset < 0)
            offset = 0;
        if (StrUtil.isBlank(sort))
            sort = "latest";

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
                        double score = (p.getCreatedAt() == null ? 0D
                                : p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                        stringRedisTemplate.opsForZSet().add(zsetKey, String.valueOf(p.getId()), score);
                    }
                }
            } catch (Exception e) {
                log.warn("Prefill ZSet failed: {}", zsetKey, e);
            }
        }

        double maxScore = (max == null) ? ("hot".equals(sort) ? Double.MAX_VALUE : System.currentTimeMillis())
                : max.doubleValue();
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
        // 批量查询本页所有帖子的附件，减少 N 次单查
        List<Long> postIdsBatch = posts.stream().map(Post::getId).collect(Collectors.toList());
        List<Attachment> allAttachments = attachmentService.getAttachmentsByBusinessBatch("post", postIdsBatch);
        java.util.Map<Long, java.util.List<Attachment>> attMap = (allAttachments == null)
                ? new java.util.HashMap<>()
                : allAttachments.stream()
                        .filter(a -> a.getBusinessId() != null)
                        .collect(java.util.stream.Collectors.groupingBy(Attachment::getBusinessId));
        List<PostListItemVO> items = new ArrayList<>();
        double nextMax = maxScore;
        for (Post p : posts) {
            // 作者信息
            UserProfile profile = userService.getProfile(p.getUserId());
            String username = profile != null ? profile.getUsername() : null;
            String avatar = profile != null ? profile.getAvatar() : null;
            // 话题信息
            String topicName = null;
            if (p.getTopicId() != null) {
                TopicVO topicVO = topicService.getTopicCachedById(p.getTopicId().intValue());
                topicName = topicVO != null ? topicVO.getName() : null;
            }
            // 附件（轻量，批量结果映射）
            List<Attachment> attachments = attMap.getOrDefault(p.getId(), java.util.Collections.emptyList());
            List<AttachmentLite> liteAttachments = new ArrayList<>();
            for (Attachment a : attachments) {
                liteAttachments.add(AttachmentLite.builder()
                        .fileUrl(a.getFileUrl())
                        .fileType(a.getFileType())
                        .fileName(a.getFileName())
                        .build());
            }

            // 获取Redis中的增量数据（点赞、评论、阅读）
            String likeKey = String.format(POST_LIKE_FLUSH_KEY_FMT, p.getId());
            String commentKey = String.format(POST_COMMENT_FLUSH_KEY_FMT, p.getId());
            String viewKey = String.format(POST_VIEW_FLUSH_KEY_FMT, p.getId());

            List<String> keys = java.util.Arrays.asList(likeKey, commentKey, viewKey);
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

            int likeDelta = 0;
            int commentDelta = 0;
            int viewDelta = 0;

            if (values != null && values.size() == 3) {
                if (StrUtil.isNotBlank(values.get(0)))
                    likeDelta = Integer.parseInt(values.get(0));
                if (StrUtil.isNotBlank(values.get(1)))
                    commentDelta = Integer.parseInt(values.get(1));
                if (StrUtil.isNotBlank(values.get(2)))
                    viewDelta = Integer.parseInt(values.get(2));
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
                    .likeCount((p.getLikeCount() == null ? 0 : p.getLikeCount()) + likeDelta)
                    .commentCount((p.getCommentCount() == null ? 0 : p.getCommentCount()) + commentDelta)
                    .shareCount(p.getShareCount() == null ? 0 : p.getShareCount())
                    .viewCount((p.getViewCount() == null ? 0 : p.getViewCount()) + viewDelta)
                    .createdAt(p.getCreatedAt())
                    .updatedAt(p.getUpdatedAt())
                    .attachments(liteAttachments)
                    .build();
            items.add(vo);

            // 计算 nextMax 供游标式分页继续
            double score = "hot".equals(sort)
                    ? (p.getViewCount() == null ? 0D : p.getViewCount().doubleValue())
                    : (p.getCreatedAt() == null ? 0D
                            : p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            if (score < nextMax)
                nextMax = score;
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
                if (p.getId() != null)
                    postBloom.add(p.getId());
                if (p.getUserId() != null)
                    userBloom.add(p.getUserId());
                if (p.getTopicId() != null)
                    topicBloom.add(p.getTopicId());
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

    @Override
    public Result deletePost(Long userId, Long postId) {
        // 1) 参数校验
        if (userId == null || userId <= 0 || postId == null || postId <= 0) {
            return Result.error("验证失败,非法访问");
        }

        // 2) 查询帖子并校验权限（作者可删；如需管理员可在此扩展）
        Post post = postsMapper.selectById(postId);
        if (post == null) {
            return Result.error("帖子不存在或已删除");
        }
        if (post.getUserId() == null || !post.getUserId().equals(userId)) {
            return Result.error("无权限删除该帖子");
        }

        // 3) 同步：MySQL 硬删除
        try {
            postsMapper.deleteById(postId);
        } catch (Exception e) {
            log.error("删除帖子失败: id={}", postId, e);
            return Result.error("删除失败");
        }

        // 4) 异步：附件删除、缓存清理、ES 删除
        threadPoolConfig.threadPoolExecutor().execute(() -> {
            try {
                // 4.1 附件删除（数据库与OSS；内部含缓存处理）
                try {
                    attachmentService.deleteAttachmentsByBusiness("post", postId);
                } catch (Exception e) {
                    log.warn("删除帖子附件失败: postId={}", postId, e);
                }

                String typeKey = StrUtil.isBlank(post.getPostType()) ? "-" : post.getPostType();
                String statusKey = (post.getStatus() == null) ? "-" : post.getStatus().name();

                // 4.3 列表缓存：按模式清理所有分页缓存（使用 SCAN，避免阻塞）
                try {
                    deleteKeysByPattern(String.format("post:list:%s:%s:*", typeKey, statusKey));
                } catch (Exception e) {
                    log.warn("删除帖子后清理列表缓存失败: id={}", postId, e);
                }

                // 4.4 计数缓存：删除总数键（回源重建更稳妥）
                try {
                    String totalLatestKey = String.format(POSTS_TOTAL_KEY_FMT, typeKey, statusKey, "latest");
                    String totalHotKey = String.format(POSTS_TOTAL_KEY_FMT, typeKey, statusKey, "hot");
                    stringRedisTemplate.delete(totalLatestKey);
                    stringRedisTemplate.delete(totalHotKey);
                } catch (Exception e) {
                    log.warn("删除帖子后删除计数缓存失败: id={}", postId, e);
                }

                // 4.5 话题维度：清理该话题的列表与计数缓存
                try {
                    if (post.getTopicId() != null) {
                        Long topicId = post.getTopicId();
                        deleteKeysByPattern(String.format("topic:post:list:%d:%s:*", topicId, statusKey));
                        String topicTotalLatestKey = String.format("topic:post:total:%d:%s:%s", topicId, statusKey,
                                "latest");
                        String topicTotalHotKey = String.format("topic:post:total:%d:%s:%s", topicId, statusKey, "hot");
                        stringRedisTemplate.delete(topicTotalLatestKey);
                        stringRedisTemplate.delete(topicTotalHotKey);
                    }
                } catch (Exception e) {
                    log.warn("删除帖子后话题维度缓存处理失败: id={}", postId, e);
                }

                // 4.6 ES 索引删除（容错，不影响主流程）
                try {
                    DeleteRequest req = new DeleteRequest(INDEX_POSTS, String.valueOf(postId));
                    esClient.delete(req, RequestOptions.DEFAULT);
                } catch (Exception e) {
                    log.warn("删除ES索引文档失败: id={}", postId, e);
                }
            } catch (Exception e) {
                log.error("删除帖子后异步同步出错: id={}", postId, e);
            }
        });

        // 5) 立即返回成功
        return Result.success("删除成功");
    }

    @Override
    public Result createShareLink(Long userId, Long postId, String channel) {
        try {
            org.redisson.api.RBloomFilter<Long> postBloom = redissonClient.getBloomFilter("bf:post:id");
            if (postBloom != null && !postBloom.contains(postId)) {
                return Result.error("帖子不存在");
            }
            String token = java.lang.Long.toString(redisIdWorker.nextId("share"), 36);
            String key = String.format(SHARE_TOKEN_KEY_FMT, token);
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("postId", String.valueOf(postId));
            map.put("userId", String.valueOf(userId));
            map.put("channel", channel);
            map.put("createdAt", String.valueOf(System.currentTimeMillis()));
            stringRedisTemplate.opsForHash().putAll(key, map);
            stringRedisTemplate.expire(key, 7, java.util.concurrent.TimeUnit.DAYS);

            threadPoolConfig.threadPoolExecutor().execute(() -> {
                try {
                    postsMapper.incrShareCount(postId, 1);
                } catch (Exception ignored) {
                }
            });

            java.util.Map<String, String> resp = new java.util.HashMap<>();
            resp.put("token", token);
            resp.put("url", "/posts/share/" + token);
            return Result.success(resp);
        } catch (Exception e) {
            return Result.error("生成分享链接失败");
        }
    }

    @Override
    public Result openShareLink(String token) {
        try {
            String key = String.format(SHARE_TOKEN_KEY_FMT, token);
            java.util.Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
            if (map == null || map.isEmpty()) {
                return Result.error("链接不存在或已过期");
            }
            Long postId = java.lang.Long.valueOf(String.valueOf(map.get("postId")));
            Post post = postsMapper.selectById(postId);
            if (post == null) {
                return Result.error("帖子不存在");
            }
            java.util.List<Attachment> attachments = attachmentService.getAttachmentsByBusiness("post", postId);
            java.util.List<AttachmentLite> liteAttachments = new java.util.ArrayList<>();
            if (attachments != null)
                for (Attachment a : attachments) {
                    liteAttachments.add(AttachmentLite.builder().fileUrl(a.getFileUrl()).fileType(a.getFileType())
                            .fileName(a.getFileName()).build());
                }
            UserProfile profile = userMapper.getProfile(post.getUserId());
            String username = profile != null ? profile.getUsername() : null;
            String avatar = profile != null ? profile.getAvatar() : null;
            String topicName = null;
            if (post.getTopicId() != null) {
                TopicVO topicVO = topicService.getTopicCachedById(post.getTopicId().intValue());
                topicName = topicVO != null ? topicVO.getName() : null;
            }
            // 获取Redis中的增量数据（点赞、评论、阅读）
            String likeKey = String.format(POST_LIKE_FLUSH_KEY_FMT, post.getId());
            String commentKey = String.format(POST_COMMENT_FLUSH_KEY_FMT, post.getId());
            String viewKey = String.format(POST_VIEW_FLUSH_KEY_FMT, post.getId());

            List<String> keys = java.util.Arrays.asList(likeKey, commentKey, viewKey);
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

            int likeDelta = 0;
            int commentDelta = 0;
            int viewDelta = 0;

            if (values != null && values.size() == 3) {
                if (StrUtil.isNotBlank(values.get(0)))
                    likeDelta = Integer.parseInt(values.get(0));
                if (StrUtil.isNotBlank(values.get(1)))
                    commentDelta = Integer.parseInt(values.get(1));
                if (StrUtil.isNotBlank(values.get(2)))
                    viewDelta = Integer.parseInt(values.get(2));
            }

            PostListItemVO vo = PostListItemVO.builder()
                    .id(post.getId())
                    .title(post.getTitle())
                    .content(post.getContent())
                    .userId(post.getUserId())
                    .authorUsername(username)
                    .authorAvatar(avatar)
                    .topicId(post.getTopicId())
                    .topicName(topicName)
                    .likeCount((post.getLikeCount() == null ? 0 : post.getLikeCount()) + likeDelta)
                    .commentCount((post.getCommentCount() == null ? 0 : post.getCommentCount()) + commentDelta)
                    .shareCount(post.getShareCount() == null ? 0 : post.getShareCount())
                    .viewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + viewDelta)
                    .createdAt(post.getCreatedAt())
                    .updatedAt(post.getUpdatedAt())
                    .attachments(liteAttachments)
                    .build();
            return Result.success(vo);
        } catch (Exception e) {
            return Result.error("解析分享链接失败");
        }finally {
            stringRedisTemplate.delete(String.format(SHARE_TOKEN_KEY_FMT, token));
            log.info("用户打开分享链接成功:{}", token);
        }
    }

    // 使用 SCAN 按模式删除键，避免 KEYS 阻塞；异常时回退 KEYS（规模小时可接受）
    private void deleteKeysByPattern(String pattern) {
        try {
            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(1000).build();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    java.util.List<byte[]> batch = new java.util.ArrayList<>();
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());
                        if (batch.size() >= 1000) {
                            connection.del(batch.toArray(new byte[0][]));
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) {
                        connection.del(batch.toArray(new byte[0][]));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        } catch (Exception e) {
            try {
                java.util.Set<String> keys = stringRedisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    stringRedisTemplate.delete(keys);
                }
            } catch (Exception e2) {
                log.warn("按模式删除缓存失败: pattern={}", pattern, e2);
            }
        }
    }

    @Scheduled(cron = "0 0 */2 * * ?")
    public void flushLikeDataToDatabase() {
        String lockKey = "lock:post:like:global:flush";
        var lock = redissonClient.getLock(lockKey);
        boolean isLocked = false;
        try {
            // 尝试获取锁，等待0秒，锁定10分钟
            isLocked = lock.tryLock(0, 600, TimeUnit.SECONDS);
            if (!isLocked) {
                log.info("Another instance is performing the flush task. Skipping.");
                return;
            }

            log.info("Start flushing like data from Redis to DB...");

            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match("post:like:flush:*")
                    .count(1000)
                    .build();

            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next()));
                    }
                }
                return null;
            });

            if (keys.isEmpty()) {
                log.info("No like data to flush.");
                return;
            }

            int batchSize = 1000;
            for (int i = 0; i < keys.size(); i += batchSize) {
                int end = Math.min(i + batchSize, keys.size());
                processFlushBatch(keys.subList(i, end));
            }

            log.info("Flushed {} keys to DB.", keys.size());

        } catch (InterruptedException e) {
            log.warn("Flush task interrupted", e);
        } catch (Exception e) {
            log.error("Flush task failed", e);
        } finally {
            if (isLocked) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.error("Unlock failed", e);
                }
            }
        }
    }

    private void processFlushBatch(List<String> keys) {
        if (keys == null || keys.isEmpty())
            return;

        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        if (values == null || values.isEmpty())
            return;

        List<Map<String, Object>> updateList = new ArrayList<>();
        List<String> keysToDecr = new ArrayList<>();
        List<Integer> deltasToDecr = new ArrayList<>();

        for (int i = 0; i < keys.size(); i++) {
            String val = values.get(i);
            if (StrUtil.isBlank(val))
                continue;

            try {
                int delta = Integer.parseInt(val);
                if (delta == 0)
                    continue;

                String key = keys.get(i);
                // key format: post:like:flush:{id}
                String idStr = key.substring(key.lastIndexOf(":") + 1);
                Long postId = Long.parseLong(idStr);

                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", postId);
                map.put("delta", delta);
                updateList.add(map);

                keysToDecr.add(key);
                deltasToDecr.add(delta);
            } catch (Exception e) {
                log.error("Error processing key: " + keys.get(i), e);
            }
        }

        if (updateList.isEmpty())
            return;

        try {
            // Batch update DB
            postsMapper.updateLikeCountBatch(updateList);

            // Decrement Redis
            stringRedisTemplate.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
                @Override
                public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                    for (int i = 0; i < keysToDecr.size(); i++) {
                        operations.opsForValue().decrement(keysToDecr.get(i), deltasToDecr.get(i));
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.error("Batch update failed", e);
        }
    }
}
