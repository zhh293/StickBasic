package com.tmd.controller;


import com.tmd.entity.dto.PageResult;
import com.tmd.entity.dto.Result;
import com.tmd.metrics.MetricsService;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/metrics")
public class MetricsController {

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private MeterRegistry meterRegistry;

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

    @GetMapping("/perf")
    public Result perf(@RequestParam(required = false) String layer,
                       @RequestParam(required = false) String method) {
        Map<String, Map<String, Object>> agg = new LinkedHashMap<>();
        for (Meter m : meterRegistry.getMeters()) {
            String name = m.getId().getName();
            if (!name.startsWith("perf_")) continue;
            String l = null;
            String me = null;
            for (Tag t : m.getId().getTags()) {
                if ("layer".equals(t.getKey())) l = t.getValue();
                if ("method".equals(t.getKey())) me = t.getValue();
            }
            if (layer != null && (l == null || !layer.equals(l))) continue;
            if (method != null && (me == null || !method.equals(me))) continue;
            String key = (l == null ? "" : l) + "|" + (me == null ? "" : me);
            Map<String, Object> row = agg.get(key);
            if (row == null) {
                row = new LinkedHashMap<>();
                row.put("layer", l);
                row.put("method", me);
                row.put("ewmaMs", null);
                row.put("p50Ms", null);
                row.put("p95Ms", null);
                row.put("p99Ms", null);
                row.put("callsSuccess", 0L);
                row.put("callsError", 0L);
                agg.put(key, row);
            }
            double v = 0.0;
            for (Measurement mm : m.measure()) {
                v = mm.getValue();
            }
            if ("perf_ewma_ms".equals(name)) row.put("ewmaMs", v);
            else if ("perf_p50_ms".equals(name)) row.put("p50Ms", v);
            else if ("perf_p95_ms".equals(name)) row.put("p95Ms", v);
            else if ("perf_p99_ms".equals(name)) row.put("p99Ms", v);
        }
        for (Meter m : meterRegistry.getMeters()) {
            if (!"perf_calls_total".equals(m.getId().getName())) continue;
            String l = null, me = null, oc = null;
            for (Tag t : m.getId().getTags()) {
                if ("layer".equals(t.getKey())) l = t.getValue();
                else if ("method".equals(t.getKey())) me = t.getValue();
                else if ("outcome".equals(t.getKey())) oc = t.getValue();
            }
            if (layer != null && (l == null || !layer.equals(l))) continue;
            if (method != null && (me == null || !method.equals(me))) continue;
            String key = (l == null ? "" : l) + "|" + (me == null ? "" : me);
            Map<String, Object> row = agg.get(key);
            if (row == null) {
                row = new LinkedHashMap<>();
                row.put("layer", l);
                row.put("method", me);
                row.put("ewmaMs", null);
                row.put("p50Ms", null);
                row.put("p95Ms", null);
                row.put("p99Ms", null);
                row.put("callsSuccess", 0L);
                row.put("callsError", 0L);
                agg.put(key, row);
            }
            double v = 0.0;
            for (Measurement mm : m.measure()) {
                v = mm.getValue();
            }
            if ("success".equals(oc)) row.put("callsSuccess", (long) v);
            else if ("error".equals(oc)) row.put("callsError", (long) v);
        }
        List<Map<String, Object>> rows = new ArrayList<>(agg.values());
        return Result.success(rows);
    }
}





// 已加接口

// -新增只读端点：GET/metrics/perf，返回每个方法的实时性能指标聚合-代码位置：src/main/java/com/tmd/controller/MetricsController.java:52-123-依赖数据来源：

// MeterRegistry 中由切面注册的指标-切面采集位置：src/main/java/com/tmd/aspect/PerformanceAspect.java:59-109（Controller）与 119-169（Service）返回内容

// -layer：
// controller 或 service-method：
// Class.method 唯一标识-ewmaMs：
// 时间衰减 EWMA 延迟-p50Ms、p95Ms、p99Ms：P²在线近似分位数-callsSuccess、callsError：成功/异常调用总计
// 过滤参数

// -支持按标签筛选：-layer：
// 如 controller
// 或 service-method：如 UserController.login-示例：/metrics/perf?layer=controller&method=UserController.login
// 效果说明

// -高保真延迟画像：
// EWMA 对突发与稳态都敏感，能快速反映端到端延迟变化；p95/p99 揭示尾延迟问题，避免均值掩盖。-无侵入采集：
// 通过 AOP 环绕增量计算，不改变业务返回与控制流。-实时检索：
// 端点聚合了 perf_ewma_ms、perf_p50_ms、perf_p95_ms、
// perf_p99_ms 与
// perf_calls_total 指标，
// 直出 JSON，运维与调参更直接。-安全性：未改动安全配置；
// 按现有 SecurityConfig 该端点需认证访问，
// 如需开放可在 SecurityConfig
// 的 authorizeHttpRequests 中添加白名单。现有指标对接

// -
// 切面注册的 Micrometer 指标-EWMA：perf_ewma_ms{layer,method}-分位数：perf_p50_ms{layer,method}、perf_p95_ms{layer,method}、perf_p99_ms{layer,method}-计数：perf_calls_total{layer,method,outcome}-
// Actuator 直接查看（可选）：/actuator/metrics/perf_p95_ms?tag=layer:service&tag=method:
// PostsServiceImpl.findById 使用示例

// -
// 查询某个 Controller 方法的全量性能画像：-请求：GET/metrics/perf?layer=controller&method=UserController.login-响应示例：-[{ "layer":"controller","method":"UserController.login","ewmaMs":23.7,"p50Ms":18.0,"p95Ms":65.0,"p99Ms":120.0,"callsSuccess":345,"callsError":3 }]-
// 查询所有 Service 方法：-请求：GET/metrics/perf?layer=
// service