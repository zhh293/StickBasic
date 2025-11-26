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
import com.tmd.service.AttachmentService;
import com.tmd.service.TopicService;
import com.tmd.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final long LIST_TTL_SECONDS = 60; // 列表缓存TTL
    private static final long TOTAL_TTL_SECONDS = 300; // 总数缓存TTL
    private static final int ZSET_PREFILL_LIMIT = 2000; // ZSet预热最大条数
    private static final String INDEX_POSTS = "posts";
    private static final String SHARE_TOKEN_KEY_FMT = "share:token:%s";
    private static final String POST_VIEW_PV_KEY_FMT = "post:view:pv:%d";
    private static final String POST_VIEW_UV_KEY_FMT = "post:view:uv:%d";
    private static final String POST_VIEW_SEEN_KEY_FMT = "post:view:seen:%d:%d"; // postId:userId
    private static final String POST_VIEW_DAILY_KEY_FMT = "post:view:daily:%s"; // yyyyMMdd
    private static final long VIEW_DEDUPE_TTL_MINUTES = 5;
    private static final String POST_VIEW_FLUSH_KEY_FMT = "post:view:flush:%d";
    private static final int POST_VIEW_FLUSH_THRESHOLD = 50;

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
                        // 批量查询本页所有帖子的附件，减少 N 次单查
                        List<Long> postIdsBatch = posts.stream().map(Post::getId).collect(Collectors.toList());
                        List<Attachment> allAttachments = attachmentService.getAttachmentsByBusinessBatch("post",
                                postIdsBatch);
                        java.util.Map<Long, java.util.List<Attachment>> attMap = (allAttachments == null)
                                ? new java.util.HashMap<>()
                                : allAttachments.stream()
                                        .filter(a -> a.getBusinessId() != null)
                                        .collect(java.util.stream.Collectors.groupingBy(Attachment::getBusinessId));

                        List<PostListItemVO> items = new ArrayList<>();
                        for (Post p : posts) {
                            // 作者信息（走缓存）
                            UserProfile profile = userService.getProfile(p.getUserId());
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
                        stringRedisTemplate.opsForValue().set(totalKey, String.valueOf(total), TOTAL_TTL_SECONDS,
                                TimeUnit.SECONDS);

                        // ZSet 分层缓存恢复
                        try {
                            String zsetKeyLatest = String.format(POSTS_ZSET_KEY_FMT, finalSort, typeKey, statusKey);
                            for (Post p : posts) {
                                double scoreLatest = p.getCreatedAt() == null ? 0D
                                        : p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                                stringRedisTemplate.opsForZSet().add(zsetKeyLatest, String.valueOf(p.getId()),
                                        scoreLatest);
                                double scoreHot = p.getViewCount() == null ? 0D : p.getViewCount().doubleValue();
                                String zsetKeyHot = String.format(POSTS_ZSET_KEY_FMT, "hot", typeKey, statusKey);
                                stringRedisTemplate.opsForZSet().add(zsetKeyHot, String.valueOf(p.getId()), scoreHot);
                            }
                        } catch (Exception e) {
                            log.warn("Restore ZSet cache failed: sort={}, type={}, status={}", finalSort, typeKey,
                                    statusKey, e);
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
                    .rows(Collections.emptyList())
                    .total(0L)
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

    public Result recordView(Long postId) {
        try {
            if (postId == null || postId <= 0) return Result.error("参数错误");

            // Bloom过滤器快速校验帖子是否存在（降低无效写）
            try {
                RBloomFilter<Long> postBloom = redissonClient.getBloomFilter("bf:post:id");
                postBloom.tryInit(10_000_000L, 0.03);
                if (!postBloom.contains(postId)) {
                    // 允许旁路计数以免影响体验，但不做UV
                     return Result.error("帖子不存在或已删除");
                }
            } catch (Exception ignore) {}

            Long uid = com.tmd.tools.BaseContext.get();
            boolean hasUser = uid != null && uid > 0;
            boolean firstInWindow = false;
            if (hasUser) {
                String seenKey = String.format(POST_VIEW_SEEN_KEY_FMT, postId, uid);
                try {
                    firstInWindow = Boolean.TRUE.equals(
                            stringRedisTemplate.opsForValue().setIfAbsent(seenKey, "1", VIEW_DEDUPE_TTL_MINUTES, java.util.concurrent.TimeUnit.MINUTES)
                    );
                } catch (Exception ignore) {}
            }

            final boolean doUv = hasUser && firstInWindow;

            // 高QPS下减少RTT：使用pipeline合并PV、UV、热度增量、日统计
            stringRedisTemplate.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
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

                        String day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                        String dayKey = String.format(POST_VIEW_DAILY_KEY_FMT, day);
                        operations.opsForHash().increment(dayKey, String.valueOf(postId), 1L);
                        operations.expire(dayKey, 7, java.util.concurrent.TimeUnit.DAYS);

                        // 记录待落库增量（轻量批量刷盘）
                        String flushKey = String.format(POST_VIEW_FLUSH_KEY_FMT, postId);
                        operations.opsForValue().increment(flushKey, 1);
                    } catch (Exception ignore) {}
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
                            try { pending = Integer.parseInt(val); } catch (Exception ignore) {}
                        }
                        if (pending >= POST_VIEW_FLUSH_THRESHOLD) {
                            String lockKey = "lock:post:view:flush:" + postId;
                            var lock = redissonClient.getLock(lockKey);
                            boolean ok = false;
                            try {
                                ok = lock.tryLock(5, 10, java.util.concurrent.TimeUnit.SECONDS);
                            } catch (InterruptedException ignored) {}
                            if (ok) {
                                try {
                                    String cur = stringRedisTemplate.opsForValue().get(flushKey);
                                    int delta = 0;
                                    if (StrUtil.isNotBlank(cur)) {
                                        try { delta = Integer.parseInt(cur); } catch (Exception ignore) {}
                                    }
                                    if (delta > 0) {
                                        postsMapper.incrViewCount(postId, delta);
                                        try {
                                            java.util.Map<String, Object> params = new java.util.HashMap<>();
                                            params.put("viewDelta", delta);
                                            Script script = new Script(ScriptType.INLINE, "painless", "ctx._source.viewCount = (ctx._source.viewCount != null ? ctx._source.viewCount : 0) + params.viewDelta", params);
                                            UpdateRequest ur = new UpdateRequest(INDEX_POSTS, String.valueOf(postId)).script(script);
                                            esClient.update(ur, RequestOptions.DEFAULT);
                                        } catch (Exception ignore) {}
                                        try { stringRedisTemplate.opsForValue().decrement(flushKey, delta); } catch (Exception ignore) {}
                                    }
                                } finally {
                                    try { lock.unlock(); } catch (Exception ignore) {}
                                }
                            }
                        }
                    } catch (Exception ignore) {}
                });

            return Result.success("记录成功");
        } catch (Exception e) {
            return Result.error("服务器繁忙");
        }
    }

    @Override
    public Result createPost(Long userId, PostCreateDTO dto) {
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

                // 计数缓存：尝试 +1（如果存在）
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
                        stringRedisTemplate.delete(listLatest);

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
                            UserProfile profile = userService.getProfile(p.getUserId());
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
                        stringRedisTemplate.opsForValue().set(listLatest, json, LIST_TTL_SECONDS, TimeUnit.SECONDS);
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
                            stringRedisTemplate.delete(topicListLatest);

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
                                UserProfile profile = userService.getProfile(p.getUserId());
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
                                itemsT.add(vo);
                            }
                            String jsonT = JSONUtil.toJsonStr(itemsT);
                            stringRedisTemplate.opsForValue().set(topicListLatest, jsonT, LIST_TTL_SECONDS,
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
                    UserProfile profile = userService.getProfile(post.getUserId());
                    String username = profile != null ? profile.getUsername() : null;
                    String avatar = profile != null ? profile.getAvatar() : null;
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
                try { postsMapper.incrShareCount(postId, 1); } catch (Exception ignored) {}
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
            if (attachments != null) for (Attachment a : attachments) {
                liteAttachments.add(AttachmentLite.builder().fileUrl(a.getFileUrl()).fileType(a.getFileType()).fileName(a.getFileName()).build());
            }
            UserProfile profile = userService.getProfile(post.getUserId());
            String username = profile != null ? profile.getUsername() : null;
            String avatar = profile != null ? profile.getAvatar() : null;
            String topicName = null;
            if (post.getTopicId() != null) {
                TopicVO topicVO = topicService.getTopicCachedById(post.getTopicId().intValue());
                topicName = topicVO != null ? topicVO.getName() : null;
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
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .shareCount(post.getShareCount())
                    .viewCount(post.getViewCount())
                    .createdAt(post.getCreatedAt())
                    .updatedAt(post.getUpdatedAt())
                    .attachments(liteAttachments)
                    .build();
            return Result.success(vo);
        } catch (Exception e) {
            return Result.error("解析分享链接失败");
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
}
