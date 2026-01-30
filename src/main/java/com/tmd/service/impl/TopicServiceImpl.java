package com.tmd.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.tmd.config.ThreadPoolConfig;
import com.tmd.entity.dto.*;
import com.tmd.mapper.*;
import com.tmd.publisher.MessageProducer;
import com.tmd.publisher.TopicModerationMessage;
import com.tmd.service.TopicService;
import com.tmd.tools.BaseContext;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service

@lombok.extern.slf4j.Slf4j
public class TopicServiceImpl implements TopicService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TopicMapper topicMapper;

    @Autowired
    private TopicFollowMapper topicFollowMapper;

    @Autowired
    private ThreadPoolConfig threadPoolConfig;
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private AttachmentMapper attachmentMapper;

    @Autowired
    private PostsMapper postsMapper;

    @Autowired
    private com.tmd.service.AttachmentService attachmentService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    @Qualifier("moderationClient")
    private ChatClient moderationClient;

    @Autowired
    private MessageProducer messageProducer;

    private static final String TOPIC_POSTS_LIST_KEY_FMT = "topic:post:list:%d:%s:%s:%d:%d"; // topicId:status:sort:page:size
    private static final String TOPIC_POSTS_TOTAL_KEY_FMT = "topic:post:total:%d:%s:%s"; // topicId:status:sort
    private static final long TOPIC_LIST_TTL_SECONDS = 60; // 列表缓存TTL
    private static final long TOPIC_TOTAL_TTL_SECONDS = 300; // 总数缓存TTL
    private static final long TOPIC_BY_ID_TTL_SECONDS = 300; // 话题按ID缓存TTL

    @Override
    public Result getAllTopics(Integer page, Integer size, String status) throws InterruptedException {
        // 先查redis中是否有数据
        // 把这一堆放到redis的集合当中
        String key = "topics:all";
        // 根据时间大小从高到低取元素，根据page和size查询
        Set<String> set = stringRedisTemplate.opsForZSet().reverseRangeByScore(key, 0, System.currentTimeMillis(),
                (long) (page - 1) * size, size);
        // 如果为空的话，就从数据库中查
        if (set == null || set.isEmpty()) {
            // redisson获取锁
            RLock lock = redissonClient.getLock("lock:topics:all");
            try {
                boolean b = lock.tryLock(10, -1, TimeUnit.SECONDS);
                // 从数据库中查出来所有的帖子，然后插入到redis中
                if (b) {
                    threadPoolConfig.threadPoolExecutor().execute(() -> {
                        List<Topic> topics = topicMapper.getAllTopics();
                        log.info("从数据库中查出来所有的帖子，然后插入到redis中{}", topics);
                        log.error("从数据库中查出来所有的帖子，然后插入到redis中{}", topics);
                        if (topics == null || topics.isEmpty()) return;
                        int batchSize = 500;
                        for (int i = 0; i < topics.size(); i += batchSize) {
                            int end = Math.min(i + batchSize, topics.size());
                            java.util.Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples = new java.util.HashSet<>();
                            for (int j = i; j < end; j++) {
                                Topic topic = topics.get(j);
                                java.time.LocalDateTime createdAt = topic.getCreatedAt();
                                double score = createdAt == null ? (double) System.currentTimeMillis() : (double) createdAt.toInstant(java.time.ZoneOffset.of("+8")).toEpochMilli();
                                tuples.add(new org.springframework.data.redis.core.DefaultTypedTuple<>(JSONUtil.toJsonStr(topic), score));
                            }
                            stringRedisTemplate.opsForZSet().add(key, tuples);
                        }
                    });
                }
                return Result.success("哎呀，一不小心走心了，再试试吧");
            } catch (Exception e) {
                return Result.error("服务器错误");
            } finally {
                lock.unlock();
            }
        } else {
            List<Topic> list = set.stream().map((json) -> {
                return JSONUtil.toBean(json, Topic.class);
            }).toList();
            for (Topic topic : list){
                long l = postsMapper.countByTopic(topic.getId(), "published");
                topic.setPostCount((int) l);
                topic.setFollowerCount(Objects.requireNonNull(stringRedisTemplate.opsForZSet().count("topic:follow:" + topic.getId(), 0, System.currentTimeMillis())).intValue());
            }
            Long total = stringRedisTemplate.opsForZSet().zCard(key);
            PageResult pageResult = PageResult.builder()
                    .currentPage(page)
                    .total(total)
                    .rows(list)
                    .build();
            return Result.success(pageResult);
        }
    }

    @Override
    public Result getTopicById(Integer topicId) {
        // 查数据库了，不管了，根据id查感觉也不慢，况且话题本来也不多
        TopicVO topic = topicMapper.getTopicById(topicId);
        return Result.success(topic);
    }

    @Override
    public TopicVO getTopicCachedById(Integer topicId) {
        if (topicId == null || topicId <= 0) return null;
        try {
            String cacheKey = "topic:id:" + topicId;
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                return JSONUtil.toBean(cached, TopicVO.class);
            }
            TopicVO vo = topicMapper.getTopicById(topicId);
            if (vo != null) {
                stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(vo), TOPIC_BY_ID_TTL_SECONDS, TimeUnit.SECONDS);
            } else {
                // 防止穿透，缓存空字符串，短TTL
                stringRedisTemplate.opsForValue().set(cacheKey, "", 60, TimeUnit.SECONDS);
            }
            return vo;
        } catch (Exception e) {
            log.warn("Get topic by id with cache failed: id={}", topicId, e);
            return topicMapper.getTopicById(topicId);
        }
    }

    @Override
    public Result createTopic(TopicDTO topic) {
        try {
            // 基础校验：至少需要文本或图片之一
            boolean hasText = topic.getName() != null && !topic.getName().trim().isEmpty()
                    || topic.getDescription() != null && !topic.getDescription().trim().isEmpty();
            boolean hasImage = topic.getCoverImage() != null && !topic.getCoverImage().trim().isEmpty();
            if (!hasText && !hasImage) {
                return Result.error("话题内容不能为空");
            }

            Long uploaderId = BaseContext.get();
            String coverImageUrl = topic.getCoverImage();

            // 先入库
            Topic topicEntity = Topic.builder()
                    .createdAt(LocalDateTime.now())
                    .coverImage(coverImageUrl)
                    .description(topic.getDescription())
                    .updatedAt(LocalDateTime.now())
                    .name(topic.getName())
                    .userId(uploaderId)
                    .build();
            topicMapper.insert(topicEntity);
            Long topicId = topicEntity.getId();

            // 保存封面附件关联（如有）
            if (coverImageUrl != null && !coverImageUrl.trim().isEmpty()) {
                try {
                    String fileId = extractFileIdFromUrl(coverImageUrl);
                    if (fileId != null) {
                        Attachment existingAttachment = attachmentMapper.selectByFileId(fileId);
                        if (existingAttachment != null) {
                            if (existingAttachment.getBusinessId() == null ||
                                    !existingAttachment.getBusinessId().equals(topicId) ||
                                    !"topic".equals(existingAttachment.getBusinessType())) {
                                Attachment newAttachment = Attachment.builder()
                                        .fileId(existingAttachment.getFileId())
                                        .fileUrl(existingAttachment.getFileUrl())
                                        .fileName(existingAttachment.getFileName())
                                        .fileSize(existingAttachment.getFileSize())
                                        .fileType(existingAttachment.getFileType())
                                        .mimeType(existingAttachment.getMimeType())
                                        .businessType("topic")
                                        .businessId(topicId)
                                        .uploaderId(uploaderId)
                                        .uploadTime(LocalDateTime.now())
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();
                                attachmentMapper.insert(newAttachment);
                            }
                        } else {
                            String fileName = extractFileNameFromFileId(fileId);
                            String fileType = extractFileTypeFromFileId(fileId);
                            Attachment newAttachment = Attachment.builder()
                                    .fileId(fileId)
                                    .fileUrl(coverImageUrl)
                                    .fileName(fileName != null ? fileName : "cover_image")
                                    .fileSize(null)
                                    .fileType(fileType != null ? fileType : "image")
                                    .mimeType(detectMimeTypeFromFileName(fileName))
                                    .businessType("topic")
                                    .businessId(topicId)
                                    .uploaderId(uploaderId)
                                    .uploadTime(LocalDateTime.now())
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            attachmentMapper.insert(newAttachment);
                        }
                    }
                } catch (Exception e) {
                    log.error("保存话题封面图片附件关联失败", e);
                    throw new RuntimeException("保存话题封面图片附件关联失败");
                }
            }

            // 写入Redis首屏集合
            String key = "topics:all";
            stringRedisTemplate.opsForZSet().add(key, JSONUtil.toJsonStr(topicEntity), System.currentTimeMillis());

            // 异步审核：发布消息到队列
            TopicModerationMessage payload = TopicModerationMessage.builder()
                    .topicId(topicId)
                    .name(topic.getName())
                    .description(topic.getDescription())
                    .coverImageUrl(coverImageUrl)
                    .uploaderId(uploaderId)
                    .build();
            messageProducer.sendTopicModeration(payload);

            return Result.success("话题已创建，正在审核中。如未通过将自动撤回");
        } catch (Exception e) {
            return Result.error("服务器错误，创建失败");
        }
    }

    @Override
    public Result followTopic(Integer topicId) {
        // 先看看redis里面有没有数据，然后从redis先取出来
        String key = "topic:follow:" + topicId;
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, System.currentTimeMillis());
        // range里面都是用户的id
        // 看看有没有呗
        if (range.contains(BaseContext.get().toString())) {
            TopicFollowVO topicFollowVO = TopicFollowVO.builder()
                    .isFollowed(false)
                    .followerCount(range.size() - 1)
                    .topicId(topicId.longValue())
                    .build();
            stringRedisTemplate.opsForZSet().remove(key, BaseContext.get().toString());
            Long l = BaseContext.get();
            // 把数据库中的改成false，数量减去一
            threadPoolConfig.threadPoolExecutor().execute(() -> {
                topicMapper.updateFollowCount(topicFollowVO);
                topicFollowMapper.deleteByTopicId(topicId,l);
            });
            return Result.success(topicFollowVO);
        } else {
            TopicFollowVO topicFollowVO = TopicFollowVO.builder()
                    .isFollowed(true)
                    .followerCount(range.size() + 1)
                    .topicId(topicId.longValue())
                    .build();
            stringRedisTemplate.opsForZSet().add(key, BaseContext.get().toString(), System.currentTimeMillis());
            Long l = BaseContext.get();
            threadPoolConfig.threadPoolExecutor().execute(() -> {
                topicMapper.updateFollowCount(topicFollowVO);
                topicFollowMapper.insert(TopicFollow.builder()
                        .topicId(Long.valueOf(topicId))
                        .userId(l)
                        .createdAt(LocalDateTime.now())
                        .build());
            });
            return Result.success(topicFollowVO);
        }
    }

    @Override
    public Result getTopicFollowers(Integer topicId, Integer page, Integer size) {
        // 直接查数据库算了，不想用缓存了，太累了
        PageHelper.startPage(page, size);
        Page<TopicFollowVO> page1 = topicFollowMapper.getTopicFollowers(topicId);
        PageResult pageResult = PageResult
                .builder()
                .total(page1.getTotal())
                .rows(page1.getResult())
                .currentPage(page)
                .build();
        return Result.success(pageResult);
    }

    @Override
    public void incrementTopicPostCount(Long topicId, Integer delta) {
        if (topicId == null || topicId <= 0) return;
        Integer d = delta == null ? 1 : delta;
        try {
            topicMapper.incrPostCount(topicId, d);
        } catch (Exception ignore) {}
        try {
            String cacheKey = "topic:id:" + topicId;
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cn.hutool.core.util.StrUtil.isNotBlank(cached)) {
                com.tmd.entity.dto.TopicVO vo = cn.hutool.json.JSONUtil.toBean(cached, com.tmd.entity.dto.TopicVO.class);
                if (vo != null) {
                    Integer pc = vo.getPostCount() == null ? 0 : vo.getPostCount();
                    vo.setPostCount(pc + d);
                    stringRedisTemplate.opsForValue().set(cacheKey, cn.hutool.json.JSONUtil.toJsonStr(vo), TOPIC_BY_ID_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
                }
            }
        } catch (Exception ignore) {}
    }

    @Override
    public Result getTopicPosts(Integer topicId,
            Integer page,
            Integer size,
            String sort,
            String status) throws InterruptedException {
//        RBloomFilter<Integer> postBloom = redissonClient.getBloomFilter("bf:topic:id");
//        if (!postBloom.contains(topicId)) {
//            return Result.error("话题不存在");
//        }
        if (page == null || page < 1)
            page = 1;
        if (size == null || size < 1)
            size = 10;
        if (cn.hutool.core.util.StrUtil.isBlank(sort))
            sort = "latest";
        String statusKey = cn.hutool.core.util.StrUtil.isBlank(status) ? "-" : status;
        String listKey= String.format(TOPIC_POSTS_LIST_KEY_FMT, topicId, statusKey, sort, page, size);
        String totalKey = String.format(TOPIC_POSTS_TOTAL_KEY_FMT, topicId, statusKey, sort);
        //拿缓存
        String cachedListJson = stringRedisTemplate.opsForValue().get(listKey);
        if (StrUtil.isNotBlank(cachedListJson)){
            List<PostListItemVO> posts = cn.hutool.json.JSONUtil.toList(cachedListJson, PostListItemVO.class);
            String total= stringRedisTemplate.opsForValue().get(totalKey);
            Long totalResult;
            if(StrUtil.isNotBlank(total)){
                totalResult = Long.parseLong(total);
            }else{
                totalResult = postsMapper.countByTopic(Long.valueOf(topicId), status);
                stringRedisTemplate.opsForValue().set(totalKey, totalResult.toString(), TOPIC_TOTAL_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            }
            return Result.success(
                    PageResult.builder()
                            .total(totalResult)
                            .currentPage(page)
                            .rows(posts)
                            .build()
            );
        }

//         未命中：尝试加锁，命中锁则同步恢复缓存；无论如何直接查询并返回数据
        String lockKey = "lock:" + listKey;
        org.redisson.api.RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignore) {
            log.error("加锁失败{}", ignore.getMessage());
            return Result.error("服务器繁忙，请稍后再试");
        }
        if (locked) {
            try{
                final int offset= (page - 1) * size;
                List<Post> posts = postsMapper.selectPageByTopic(Long.valueOf(topicId), status, sort, offset, size);
                List<Long>postIds=posts.stream().map(Post::getId).toList();
                List<Attachment> allAttachments = attachmentService.getAttachmentsByBusinessBatch("post", postIds);
                Map<Long, List<Attachment>> attMap = (allAttachments == null)
                ? new java.util.HashMap<>()
                : allAttachments.stream()
                        .filter(a -> a.getBusinessId() != null)
                        .collect(java.util.stream.Collectors.groupingBy(Attachment::getBusinessId));
                List<PostListItemVO>items=new ArrayList<>();
                TopicVO topicById = topicMapper.getTopicById(topicId);
                String topicName = topicById != null ? topicById.getName() : null;
                for (Post p : posts){
                    UserProfile profile = userMapper.getProfile(p.getUserId());
                    String username = profile != null ? profile.getUsername() : null;
                    String avatar = profile != null ? profile.getAvatar() : null;
                    java.util.List<Attachment> attachments = attMap.getOrDefault(p.getId(), java.util.Collections.emptyList());
                    java.util.List<AttachmentLite> liteAttachments = new java.util.ArrayList<>();
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
                threadPoolConfig.threadPoolExecutor().execute(() -> {
                    long total=postsMapper.countByTopic(Long.valueOf(topicId), status);
                    //开始恢复缓存
                    try {
                        String json = cn.hutool.json.JSONUtil.toJsonStr(items);
                        stringRedisTemplate.opsForValue().set(listKey, json, TOPIC_LIST_TTL_SECONDS,
                                java.util.concurrent.TimeUnit.SECONDS);
                        stringRedisTemplate.opsForValue().set(totalKey, String.valueOf(total), TOPIC_TOTAL_TTL_SECONDS,
                                java.util.concurrent.TimeUnit.SECONDS);
                    }catch (Exception e){
                        log.error("缓存失败{}", e.getMessage());
                        return;
                    }
                });
                return Result.success(
                        PageResult.builder()
                                .rows( items)
                                .total(postsMapper.countByTopic(Long.valueOf(topicId), status))
                                .currentPage(page)
                                .build()
                );
            }catch (Exception e){
                log.error("查询失败{}", e.getMessage());
                return Result.error("服务器繁忙，请稍后再试");
            }finally {
                // 优化点2：释放锁前校验，避免未持有锁时unlock抛异常
                if (lock.isHeldByCurrentThread()) {
                    try {
                        lock.unlock();
                    } catch (Exception e) {
                        log.error("释放分布式锁失败", e);
                    }
                }
            }
        }
        return Result.success(
                PageResult.builder()
                        .rows(new java.util.ArrayList<>())
                        .total(0L)
                        .currentPage(page)
                        .build()
        );
    }



//    @Override
//    public Result getTopicPosts(Integer topicId,
//            Integer page,
//            Integer size,
//            String sort,
//            String status) throws InterruptedException {
//        if (topicId == null || topicId <= 0) {
//            return Result.error("话题ID不能为空");
//        }
//        if (page == null || page < 1)
//            page = 1;
//        if (size == null || size < 1)
//            size = 10;
//        if (cn.hutool.core.util.StrUtil.isBlank(sort))
//            sort = "latest";
//
//        String statusKey = cn.hutool.core.util.StrUtil.isBlank(status) ? "-" : status;
//        String listKey = String.format(TOPIC_POSTS_LIST_KEY_FMT, topicId, statusKey, sort, page, size);
//        String totalKey = String.format(TOPIC_POSTS_TOTAL_KEY_FMT, topicId, statusKey, sort);
//
//        // 读缓存
//        String cachedListJson = stringRedisTemplate.opsForValue().get(listKey);
//        if (cn.hutool.core.util.StrUtil.isNotBlank(cachedListJson)) {
//            java.util.List<PostListItemVO> rows = cn.hutool.json.JSONUtil
//                    .toList(cn.hutool.json.JSONUtil.parseArray(cachedListJson), PostListItemVO.class);
//            Long total = null;
//            String totalStr = stringRedisTemplate.opsForValue().get(totalKey);
//            if (cn.hutool.core.util.StrUtil.isNotBlank(totalStr)) {
//                try {
//                    total = Long.parseLong(totalStr);
//                } catch (Exception ignore) {
//                }
//            }
//            if (total == null)
//                total = (long) rows.size();
//            PageResult pageResult = new PageResult(total, rows);
//            return Result.success(pageResult);
//        }
//
//        // 未命中：尝试加锁，命中锁则同步恢复缓存；无论如何直接查询并返回数据
//        String lockKey = "lock:" + listKey;
//        org.redisson.api.RLock lock = redissonClient.getLock(lockKey);
//        boolean locked = false;
//        try {
//            locked = lock.tryLock(5, 10, java.util.concurrent.TimeUnit.SECONDS);
//        } catch (Exception ignore) {
//        }
//
//        final int offset = (page - 1) * size;
//        java.util.List<Post> posts = postsMapper.selectPageByTopic(topicId.longValue(), status, sort, offset, size);
//        // 批量查询该页所有帖子的附件，优先命中缓存，未命中一次性补齐并回写缓存
//        java.util.List<Long> postIdsBatch = posts.stream().map(Post::getId)
//                .collect(java.util.stream.Collectors.toList());
//        java.util.List<Attachment> allAttachments = attachmentService.getAttachmentsByBusinessBatch("post",
//                postIdsBatch);
//        java.util.Map<Long, java.util.List<Attachment>> attMap = (allAttachments == null)
//                ? new java.util.HashMap<>()
//                : allAttachments.stream()
//                        .filter(a -> a.getBusinessId() != null)
//                        .collect(java.util.stream.Collectors.groupingBy(Attachment::getBusinessId));
//        java.util.List<PostListItemVO> items = new java.util.ArrayList<>();
//        TopicVO topicVO = topicMapper.getTopicById(topicId);
//        String topicName = topicVO != null ? topicVO.getName() : null;
//        for (Post p : posts) {
//            UserProfile profile = userMapper.getProfile(p.getUserId());
//            String username = profile != null ? profile.getUsername() : null;
//            String avatar = profile != null ? profile.getAvatar() : null;
//            java.util.List<Attachment> attachments = attMap.getOrDefault(p.getId(), java.util.Collections.emptyList());
//            java.util.List<AttachmentLite> liteAttachments = new java.util.ArrayList<>();
//            for (Attachment a : attachments) {
//                liteAttachments.add(AttachmentLite.builder()
//                        .fileUrl(a.getFileUrl())
//                        .fileType(a.getFileType())
//                        .fileName(a.getFileName())
//                        .build());
//            }
//            PostListItemVO vo = PostListItemVO.builder()
//                    .id(p.getId())
//                    .title(p.getTitle())
//                    .content(p.getContent())
//                    .userId(p.getUserId())
//                    .authorUsername(username)
//                    .authorAvatar(avatar)
//                    .topicId(p.getTopicId())
//                    .topicName(topicName)
//                    .likeCount(p.getLikeCount())
//                    .commentCount(p.getCommentCount())
//                    .shareCount(p.getShareCount())
//                    .viewCount(p.getViewCount())
//                    .createdAt(p.getCreatedAt())
//                    .updatedAt(p.getUpdatedAt())
//                    .attachments(liteAttachments)
//                    .build();
//            items.add(vo);
//        }
//        long total = postsMapper.countByTopic(topicId.longValue(), status);
//        PageResult pageResult = new PageResult(total, items);
//
//        if (locked) {
//            try {
//                String json = cn.hutool.json.JSONUtil.toJsonStr(items);
//                stringRedisTemplate.opsForValue().set(listKey, json, TOPIC_LIST_TTL_SECONDS,
//                        java.util.concurrent.TimeUnit.SECONDS);
//                stringRedisTemplate.opsForValue().set(totalKey, String.valueOf(total), TOPIC_TOTAL_TTL_SECONDS,
//                        java.util.concurrent.TimeUnit.SECONDS);
//            } catch (Exception e) {
//                log.error("Restore topic:posts cache failed, key={}", listKey, e);
//            } finally {
//                try {
//                    lock.unlock();
//                } catch (Exception ignore) {
//                }
//            }
//        }
//        return Result.success(pageResult);
//    }

    /**
     * 从 OSS URL 中提取 fileId (objectKey)
     * OSS URL 格式：https://bucket.endpoint/objectKey
     * 
     * @param url OSS 文件 URL
     * @return fileId (objectKey)，如果无法提取则返回 null
     */
    private String extractFileIdFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        try {
            // 移除协议前缀
            String path = url;
            if (path.startsWith("https://") || path.startsWith("http://")) {
                int protocolEnd = path.indexOf("://") + 3;
                path = path.substring(protocolEnd);
            }

            // 找到第一个斜杠后的部分就是 objectKey
            int firstSlash = path.indexOf('/');
            if (firstSlash >= 0 && firstSlash < path.length() - 1) {
                return path.substring(firstSlash + 1);
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 fileId 中提取文件名
     * fileId 格式：image/2024/01/15/uuid.jpg
     * 
     * @param fileId 文件ID（objectKey）
     * @return 文件名，如果无法提取则返回 null
     */
    private String extractFileNameFromFileId(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return null;
        }

        try {
            int lastSlash = fileId.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < fileId.length() - 1) {
                return fileId.substring(lastSlash + 1);
            }
            return fileId;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 fileId 中提取文件类型
     * fileId 格式：image/2024/01/15/uuid.jpg 或 video/2024/01/15/uuid.mp4
     * 
     * @param fileId 文件ID（objectKey）
     * @return 文件类型（image/video），如果无法提取则返回 null
     */
    private String extractFileTypeFromFileId(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return null;
        }

        try {
            int firstSlash = fileId.indexOf('/');
            if (firstSlash > 0) {
                String type = fileId.substring(0, firstSlash);
                if ("image".equals(type) || "video".equals(type)) {
                    return type;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据文件名检测 MIME 类型
     * 
     * @param fileName 文件名
     * @return MIME 类型
     */
    private String detectMimeTypeFromFileName(String fileName) {
        if (fileName == null) {
            return "image/jpeg";
        }

        String lowerFileName = fileName.toLowerCase();

        if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFileName.endsWith(".png")) {
            return "image/png";
        } else if (lowerFileName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFileName.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerFileName.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerFileName.endsWith(".avi")) {
            return "video/avi";
        } else if (lowerFileName.endsWith(".mov")) {
            return "video/quicktime";
        }

        return "image/jpeg"; // 默认
    }
}
