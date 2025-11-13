package com.tmd.metrics;


import com.tmd.entity.dto.PageResult;

public interface MetricsService {
    void inc(String name, String tags);
    void timer(String name, String tags, long durationMs);
    PageResult queryCounters(String name, Integer page, Integer size);
    PageResult queryTimers(String name, Integer page, Integer size);
    void initTable();
    void flushFromRedis(int limit);
}