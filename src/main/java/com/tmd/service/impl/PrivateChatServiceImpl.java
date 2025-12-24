package com.tmd.service.impl;

import cn.hutool.json.JSONUtil;
import com.tmd.WebSocket.WebSocketServer;
import com.tmd.config.ThreadPoolConfig;
import com.tmd.entity.dto.*;
import com.tmd.mapper.ChatMessageMapper;
import com.tmd.service.PrivateChatService;
import com.tmd.tools.BaseContext;
import com.tmd.tools.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PrivateChatServiceImpl implements PrivateChatService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private ThreadPoolConfig threadPoolConfig;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private RedisIdWorker redisIdWorker;

    private static final String OFFLINE_KEY_FMT = "chat:offline:%s";
    private static final String CONV_KEY_FMT = "chat:conv:%s:%s";
    private static final String MSG_KEY_FMT = "chat:msg:%s";
    private static final String ACK_KEY_FMT = "chat:ack:%s";

    private String convKey(Long a, Long b) {
        long x = Math.min(a, b);
        long y = Math.max(a, b);
        return String.format(CONV_KEY_FMT, x, y);
    }

    @Override
    public Result send(Long toUserId, String content) {
        Long fromUserId = BaseContext.get();
        if (fromUserId == null || toUserId == null) {
            return Result.error("参数错误");
        }
        if (content == null || content.trim().isEmpty()) {
            return Result.error("内容不能为空");
        }
        long id = redisIdWorker.nextId("chat");
        ChatMessage message = ChatMessage.builder()
                .id(id)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .content(content)
                .status(ChatStatus.sent)
                .createdAt(LocalDateTime.now())
                .build();

        String convKey = convKey(fromUserId, toUserId);
        try {
            long now = System.currentTimeMillis();
            stringRedisTemplate.opsForZSet().add(convKey, JSONUtil.toJsonStr(message), now);
            stringRedisTemplate.opsForValue().set(String.format(MSG_KEY_FMT, id), JSONUtil.toJsonStr(message));
            // 会话缓存按7天窗口滚动清理，并设置TTL（7天）
            long sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000;
            try {
                stringRedisTemplate.opsForZSet().removeRangeByScore(convKey, 0, sevenDaysAgo);
                stringRedisTemplate.expire(convKey, java.time.Duration.ofDays(7));
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.warn("写入会话ZSet失败 key={} id={}", convKey, id, e);
        }

        boolean delivered = false;
        try {
            if (webSocketServer.Open(String.valueOf(toUserId))) {
                webSocketServer.sendToUser(String.valueOf(toUserId), JSONUtil.toJsonStr(message));
                delivered = true;
                message.setStatus(ChatStatus.delivered);
                message.setDeliveredAt(LocalDateTime.now());
                stringRedisTemplate.opsForValue().set(String.format(MSG_KEY_FMT, id), JSONUtil.toJsonStr(message));
                threadPoolConfig.threadPoolExecutor().execute(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {}
                    String ack = stringRedisTemplate.opsForValue().get(String.format(ACK_KEY_FMT, id));
                    boolean stillOpen = webSocketServer.Open(String.valueOf(toUserId));
                    if (ack == null || ack.isEmpty()) {
                        message.setStatus(ChatStatus.sent);
                        message.setDeliveredAt(null);
                        stringRedisTemplate.opsForValue().set(String.format(MSG_KEY_FMT, id), JSONUtil.toJsonStr(message));
                        String offlineKey2 = String.format(OFFLINE_KEY_FMT, toUserId);
                        stringRedisTemplate.opsForZSet().add(offlineKey2, JSONUtil.toJsonStr(message), System.currentTimeMillis());
                    }
                });
            } else {
                String offlineKey = String.format(OFFLINE_KEY_FMT, toUserId);
                stringRedisTemplate.opsForZSet().add(offlineKey, JSONUtil.toJsonStr(message), System.currentTimeMillis());
            }
        } catch (Exception e) {
            log.error("推送消息失败 toUserId={} id={}", toUserId, id, e);
            String offlineKey = String.format(OFFLINE_KEY_FMT, toUserId);
            stringRedisTemplate.opsForZSet().add(offlineKey, JSONUtil.toJsonStr(message), System.currentTimeMillis());
        }

        ChatMessageVO vo = ChatMessageVO.builder()
                .id(message.getId())
                .fromUserId(message.getFromUserId())
                .toUserId(message.getToUserId())
                .content(message.getContent())
                .status(message.getStatus())
                .createdAt(message.getCreatedAt())
                .build();

        threadPoolConfig.threadPoolExecutor().execute(() -> {
            try {
                chatMessageMapper.insert(message);
            } catch (Exception ignored) {
            }
        });

        return Result.success(vo);
    }

    @Override
    public Result pullOffline(Integer page, Integer size) {
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 10;
        Long uid = BaseContext.get();
        if (uid == null) return Result.error("未登录");
        String key = String.format(OFFLINE_KEY_FMT, uid);
        Set<String> set = stringRedisTemplate.opsForZSet()
                .rangeByScore(key, 0, System.currentTimeMillis(), (long) (page - 1) * size, size);
        if (set == null || set.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<ChatMessageVO> list = set.stream()
                .map(s -> JSONUtil.toBean(s, ChatMessage.class))
                .map(m -> {
                    String mk = String.format(MSG_KEY_FMT, m.getId());
                    String latest = stringRedisTemplate.opsForValue().get(mk);
                    ChatMessage src = latest != null ? JSONUtil.toBean(latest, ChatMessage.class) : m;
                    return src;
                })
                .map(m -> ChatMessageVO.builder()
                        .id(m.getId())
                        .fromUserId(m.getFromUserId())
                        .toUserId(m.getToUserId())
                        .content(m.getContent())
                        .status(m.getStatus())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        try {
            stringRedisTemplate.opsForZSet().remove(key, set.toArray());
        } catch (Exception e) {
            log.warn("清理离线消息失败 key={}", key, e);
        }
        return Result.success(list);
    }

    @Override
    public Result history(Long otherUserId, Integer size, Long max, Integer offset) {
        if (otherUserId == null) return Result.error("参数错误");
        if (size == null || size < 1) size = 10;
        if (offset == null || offset < 0) offset = 0;
        Long uid = BaseContext.get();
        if (uid == null) return Result.error("未登录");

        String key = convKey(uid, otherUserId);
        long maxScore = max == null ? System.currentTimeMillis() : max;
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, (double) maxScore, offset, size);
        if (tuples == null || tuples.isEmpty()) {
            return Result.success(ScrollResult.builder().data(new ArrayList<>()).max(max).scroll(offset).build());
        }
        List<ChatMessageVO> items = new ArrayList<>(tuples.size());
        long nextMax = max == null ? System.currentTimeMillis() : max;
        int os = offset;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            String s = t.getValue();
            ChatMessage m0 = JSONUtil.toBean(s, ChatMessage.class);
            String mk = String.format(MSG_KEY_FMT, m0.getId());
            String latest = stringRedisTemplate.opsForValue().get(mk);
            ChatMessage m = latest != null ? JSONUtil.toBean(latest, ChatMessage.class) : m0;
            items.add(ChatMessageVO.builder()
                    .id(m.getId())
                    .fromUserId(m.getFromUserId())
                    .toUserId(m.getToUserId())
                    .content(m.getContent())
                    .status(m.getStatus())
                    .createdAt(m.getCreatedAt())
                    .build());
            Double score = t.getScore();
            long time = score == null ? nextMax : score.longValue();
            if (time < nextMax) {
                nextMax = time;
                os = offset + items.size();
            }
        }
        ScrollResult res = ScrollResult.builder().data(items).max(nextMax).scroll(os).build();
        return Result.success(res);
    }

    @Override
    public Result markRead(Long messageId) {
        if (messageId == null) return Result.error("参数错误");
        Long uid = BaseContext.get();
        if (uid == null) return Result.error("未登录");
        ChatMessage m = null;
        try {
            m = chatMessageMapper.selectById(messageId);
        } catch (Exception ignored) {}
        if (m == null) return Result.error("消息不存在");
        if (!uid.equals(m.getToUserId())) return Result.error("无权限");
        LocalDateTime now = LocalDateTime.now();
        m.setStatus(ChatStatus.read);
        m.setReadAt(now);
        stringRedisTemplate.opsForValue().set(String.format(MSG_KEY_FMT, m.getId()), JSONUtil.toJsonStr(m));
        try { stringRedisTemplate.expire(String.format(MSG_KEY_FMT, m.getId()), java.time.Duration.ofHours(24)); } catch (Exception ignored) {}
        threadPoolConfig.threadPoolExecutor().execute(() -> {
            try { chatMessageMapper.markRead(messageId, now); } catch (Exception ignored) {}
        });
        try {
            Map<String, Object> receipt = new HashMap<>();
            receipt.put("type", "chat_receipt");
            receipt.put("receiptType", "read");
            receipt.put("messageId", m.getId());
            receipt.put("fromUserId", m.getFromUserId());
            receipt.put("toUserId", m.getToUserId());
            webSocketServer.sendToUser(String.valueOf(m.getFromUserId()), JSONUtil.toJsonStr(receipt));
        } catch (Exception ignored) {}
        return Result.success("ok");
    }

    @Override
    public Result recall(Long messageId) {
        if (messageId == null) return Result.error("参数错误");
        Long uid = BaseContext.get();
        if (uid == null) return Result.error("未登录");
        ChatMessage m = null;
        try {
            m = chatMessageMapper.selectById(messageId);
        } catch (Exception ignored) {}
        if (m == null) return Result.error("消息不存在");
        if (!uid.equals(m.getFromUserId())) return Result.error("无权限");
        LocalDateTime now = LocalDateTime.now();
        m.setStatus(ChatStatus.recalled);
        m.setRecalledAt(now);
        stringRedisTemplate.opsForValue().set(String.format(MSG_KEY_FMT, m.getId()), JSONUtil.toJsonStr(m));
        try { stringRedisTemplate.expire(String.format(MSG_KEY_FMT, m.getId()), java.time.Duration.ofDays(7)); } catch (Exception ignored) {}
        threadPoolConfig.threadPoolExecutor().execute(() -> {
            try { chatMessageMapper.markRecalled(messageId, now); } catch (Exception ignored) {}
        });
        try {
            Map<String, Object> receipt = new HashMap<>();
            receipt.put("type", "chat_receipt");
            receipt.put("receiptType", "recall");
            receipt.put("messageId", m.getId());
            receipt.put("fromUserId", m.getFromUserId());
            receipt.put("toUserId", m.getToUserId());
            webSocketServer.sendToUser(String.valueOf(m.getToUserId()), JSONUtil.toJsonStr(receipt));
        } catch (Exception ignored) {}
        return Result.success("ok");
    }

    @Override
    public Result ack(Long messageId) {
        if (messageId == null) return Result.error("参数错误");
        try {
            stringRedisTemplate.opsForValue().set(String.format(ACK_KEY_FMT, messageId), "1", java.time.Duration.ofMinutes(5));
            return Result.success("ok");
        } catch (Exception e) {
            return Result.error("ack失败");
        }
    }
}