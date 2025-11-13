package com.tmd.controller;


import com.tmd.entity.dto.PageResult;
import com.tmd.entity.dto.Result;
import com.tmd.metrics.MetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/metrics")
public class MetricsController {

    @Autowired
    private MetricsService metricsService;

    @GetMapping("/counters")
    public Result counters(@RequestParam String name,
                           @RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "10") Integer size) {
        PageResult pr = metricsService.queryCounters(name, page, size);
        return Result.success(pr);
    }

    @GetMapping("/timers")
    public Result timers(@RequestParam String name,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "10") Integer size) {
        PageResult pr = metricsService.queryTimers(name, page, size);
        return Result.success(pr);
    }

    @PostMapping("/init")
    public Result init() {
        metricsService.initTable();
        return Result.success();
    }

    @PostMapping("/flush")
    public Result flush(@RequestParam(defaultValue = "200") Integer limit) {
        metricsService.flushFromRedis(limit);
        return Result.success();
    }
}