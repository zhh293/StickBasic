package com.tmd.metrics.impl;


import com.tmd.entity.dto.PageResult;
import com.tmd.mapper.MetricsMapper;
import com.tmd.metrics.MetricEvent;
import com.tmd.metrics.MetricsService;
import com.tmd.tools.RedisIdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetricsServiceImpl implements MetricsService {

    private final StringRedisTemplate stringRedisTemplate;
    private final MetricsMapper metricsMapper;
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    public void inc(String name, String tags) {
        try {
            stringRedisTemplate.opsForHash().increment("metrics:counters:" + name, tags == null ? "-" : tags, 1);
        } catch (Exception ignore) {}
    }

    @Override
    public void timer(String name, String tags, long durationMs) {
        try {
            String key = "metrics:timers:" + name + ":" + (tags == null ? "-" : tags);
            stringRedisTemplate.opsForList().leftPush(key, Long.toString(durationMs));
            stringRedisTemplate.opsForList().trim(key, 0, 999);
            MetricEvent e = MetricEvent.builder()
                    .id(redisIdWorker.nextId("metric"))
                    .name(name)
                    .tags(tags)
                    .value(null)
                    .durationMs(durationMs)
                    .createdAt(LocalDateTime.now())
                    .build();
            stringRedisTemplate.opsForList().leftPush("metrics:events", com.alibaba.fastjson2.JSON.toJSONString(e));
            stringRedisTemplate.opsForList().trim("metrics:events", 0, 4999);
        } catch (Exception ignore) {}
    }

    @Override
    public PageResult queryCounters(String name, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 10 : Math.min(Math.max(size, 1), 50);
        java.util.Map<Object, Object> map = stringRedisTemplate.opsForHash().entries("metrics:counters:" + name);
        List<java.util.Map<String, Object>> rows = new ArrayList<>();
        if (map != null && !map.isEmpty()) {
            List<java.util.Map.Entry<Object, Object>> all = new ArrayList<>(map.entrySet());
            int start = (p - 1) * s;
            int end = Math.min(start + s, all.size());
            for (int i = start; i < end; i++) {
                java.util.Map.Entry<Object, Object> e = all.get(i);
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("tags", e.getKey());
                row.put("count", Long.valueOf(e.getValue().toString()));
                rows.add(row);
            }
            return PageResult.builder().total((long) all.size()).rows(rows).build();
        }
        return PageResult.builder().total(0L).rows(rows).build();
    }

    @Override
    public PageResult queryTimers(String name, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 10 : Math.min(Math.max(size, 1), 50);
        List<java.util.Map<String, Object>> rows = new ArrayList<>();
        java.util.Set<String> keys = stringRedisTemplate.keys("metrics:timers:" + name + ":*");
        if (keys != null) {
            List<String> listKeys = new ArrayList<>(keys);
            int start = (p - 1) * s;
            int end = Math.min(start + s, listKeys.size());
            for (int i = start; i < end; i++) {
                String key = listKeys.get(i);
                List<String> vals = stringRedisTemplate.opsForList().range(key, 0, 999);
                java.util.List<Long> nums = new ArrayList<>();
                if (vals != null) {
                    for (String v : vals) nums.add(Long.parseLong(v));
                }
                java.util.Collections.sort(nums);
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("key", key);
                row.put("count", nums.size());
                row.put("p50", nums.isEmpty() ? 0 : nums.get(nums.size() / 2));
                row.put("p95", nums.isEmpty() ? 0 : nums.get((int) Math.floor(nums.size() * 0.95) - 1 < 0 ? 0 : (int) Math.floor(nums.size() * 0.95) - 1));
                row.put("p99", nums.isEmpty() ? 0 : nums.get((int) Math.floor(nums.size() * 0.99) - 1 < 0 ? 0 : (int) Math.floor(nums.size() * 0.99) - 1));
                rows.add(row);
            }
            return PageResult.builder().total((long) listKeys.size()).rows(rows).build();
        }
        return PageResult.builder().total(0L).rows(rows).build();
    }

    @Override
    public void initTable() {
        try {
            metricsMapper.createTableIfNotExists();
        } catch (Exception e) {
            log.warn("metrics table init failed", e);
        }
    }

    @Override
    public void flushFromRedis(int limit) {
        int max = Math.min(Math.max(limit, 1), 1000);
        List<String> batch = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            String s = stringRedisTemplate.opsForList().rightPop("metrics:events");
            if (s == null) break;
            batch.add(s);
        }
        for (String s : batch) {
            try {
                MetricEvent e = com.alibaba.fastjson2.JSON.parseObject(s, MetricEvent.class);
                metricsMapper.insert(e);
            } catch (Exception ignore) {}
        }
    }
}