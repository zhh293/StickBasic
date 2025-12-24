package com.tmd.aspect;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Aspect
@Component
public class PerformanceAspect {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceAspect.class);

    private final MeterRegistry meterRegistry;
    private final Map<String, EndpointMetrics> metrics = new ConcurrentHashMap<>();

    public PerformanceAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 定义Controller层切点
     * 匹配所有Controller类中的方法
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerMethods() {
    }

    /**
     * 定义Service层切点
     * 匹配所有Service类中的方法
     */
    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceMethods() {
    }

    /**
     * 环绕通知 - 监控Controller层方法性能
     * 记录方法执行时间、异常信息等性能指标
     * 
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("controllerMethods()")
    @Timed(value = "controller.execution.time", description = "Controller method execution time")
    public Object monitorControllerExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String operation = className + "." + methodName;
        String key = "controller|" + operation;
        EndpointMetrics m = metrics.get(key);
        if (m == null) {
            EndpointMetrics nm = new EndpointMetrics(30000);
            EndpointMetrics prev = metrics.putIfAbsent(key, nm);
            m = prev == null ? nm : prev;
            registerGauges("controller", operation, m);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;
            sample.stop(meterRegistry.timer("controller.execution.time",
                    "method", operation, "status", "success"));
            m.update(executionTime);
            meterRegistry.counter("perf_calls_total", Tags.of(Tag.of("layer", "controller"), Tag.of("method", operation), Tag.of("outcome", "success"))).increment();

            // 记录慢查询（超过1秒）
            if (executionTime > 1000) {
                logger.warn("Slow controller method detected: {} took {}ms", operation, executionTime);
                meterRegistry.counter("controller.slow.request", "method", operation).increment();
            }

            logger.debug("Controller method {} executed successfully in {}ms", operation, executionTime);
            return result;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            sample.stop(meterRegistry.timer("controller.execution.time",
                    "method", operation, "status", "error"));
            m.update(executionTime);
            meterRegistry.counter("perf_calls_total", Tags.of(Tag.of("layer", "controller"), Tag.of("method", operation), Tag.of("outcome", "error"))).increment();

            logger.error("Controller method {} failed after {}ms with error: {}",
                    operation, executionTime, e.getMessage(), e);
            meterRegistry.counter("controller.error.count", "method", operation, "error", e.getClass().getSimpleName())
                    .increment();

            throw e;
        }
    }

    /**
     * 环绕通知 - 监控Service层方法性能
     * 记录数据库操作等耗时操作的性能指标
     * 
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("serviceMethods()")
    @Timed(value = "service.execution.time", description = "Service method execution time")
    public Object monitorServiceExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String operation = className + "." + methodName;
        String key = "service|" + operation;
        EndpointMetrics m = metrics.get(key);
        if (m == null) {
            EndpointMetrics nm = new EndpointMetrics(20000);
            EndpointMetrics prev = metrics.putIfAbsent(key, nm);
            m = prev == null ? nm : prev;
            registerGauges("service", operation, m);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;
            sample.stop(meterRegistry.timer("service.execution.time",
                    "method", operation, "status", "success"));
            m.update(executionTime);
            meterRegistry.counter("perf_calls_total", Tags.of(Tag.of("layer", "service"), Tag.of("method", operation), Tag.of("outcome", "success"))).increment();

            // 记录慢查询（超过500ms）
            if (executionTime > 500) {
                logger.warn("Slow service method detected: {} took {}ms", operation, executionTime);
                meterRegistry.counter("service.slow.query", "method", operation).increment();
            }

            logger.debug("Service method {} executed successfully in {}ms", operation, executionTime);
            return result;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            sample.stop(meterRegistry.timer("service.execution.time",
                    "method", operation, "status", "error"));
            m.update(executionTime);
            meterRegistry.counter("perf_calls_total", Tags.of(Tag.of("layer", "service"), Tag.of("method", operation), Tag.of("outcome", "error"))).increment();

            logger.error("Service method {} failed after {}ms with error: {}",
                    operation, executionTime, e.getMessage(), e);
            meterRegistry.counter("service.error.count", "method", operation, "error", e.getClass().getSimpleName())
                    .increment();

            throw e;
        }
    }

    @AfterReturning(pointcut = "execution(* org.springframework.data.redis.core.ValueOperations.get(..)) && args(key)", returning = "ret")
    public void redisGetMetric(Object key, Object ret) {
        if (key instanceof String) {
            String k = (String) key;
            if (k.startsWith("post:list:")) {
                meterRegistry.counter("posts_cache_get_total").increment();
                if (ret != null) {
                    meterRegistry.counter("posts_cache_hit_total").increment();
                } else {
                    meterRegistry.counter("posts_cache_miss_total").increment();
                }
            }
        }
    }

    @After("execution(* com.tmd.service.impl.PrivateChatServiceImpl.send(..))")
    public void chatSendMetric() {
        meterRegistry.counter("chat_send_total").increment();
    }

    @After("execution(* com.tmd.service.impl.PrivateChatServiceImpl.ack(..))")
    public void chatAckMetric() {
        meterRegistry.counter("chat_ack_total").increment();
    }

    @After("execution(* com.tmd.consumer.MessageConsumer.consume*(..)) && args(message, channel, amqpMessage)")
    public void mqConsumeMetric(Object message, Object channel, Message amqpMessage) {
        String q = amqpMessage.getMessageProperties() == null ? null
                : amqpMessage.getMessageProperties().getConsumerQueue();
        if (q != null) {
            meterRegistry.counter("mq_consume_total", "queue", q).increment();
            Boolean redelivered = amqpMessage.getMessageProperties().getRedelivered();
            if (redelivered != null && redelivered) {
                meterRegistry.counter("mq_redelivered_total", "queue", q).increment();
            }
        }
    }
    private void registerGauges(String layer, String operation, EndpointMetrics m) {
        if (m.registered.compareAndSet(false, true)) {
            meterRegistry.gauge("perf_ewma_ms", Tags.of(Tag.of("layer", layer), Tag.of("method", operation)), m, x -> x.ewma());
            meterRegistry.gauge("perf_p50_ms", Tags.of(Tag.of("layer", layer), Tag.of("method", operation)), m, x -> x.p50());
            meterRegistry.gauge("perf_p95_ms", Tags.of(Tag.of("layer", layer), Tag.of("method", operation)), m, x -> x.p95());
            meterRegistry.gauge("perf_p99_ms", Tags.of(Tag.of("layer", layer), Tag.of("method", operation)), m, x -> x.p99());
        }
    }
    static final class EndpointMetrics {
        final TimeDecayedEWMA ewma;
        final P2Quantile q50;
        final P2Quantile q95;
        final P2Quantile q99;
        final AtomicBoolean registered = new AtomicBoolean(false);
        EndpointMetrics(long tauMillis) {
            this.ewma = new TimeDecayedEWMA(tauMillis);
            this.q50 = new P2Quantile(0.5);
            this.q95 = new P2Quantile(0.95);
            this.q99 = new P2Quantile(0.99);
        }
        synchronized void update(double x) {
            ewma.update(x);
            q50.add(x);
            q95.add(x);
            q99.add(x);
        }
        double ewma() { return ewma.value(); }
        double p50() { return q50.value(); }
        double p95() { return q95.value(); }
        double p99() { return q99.value(); }
    }
    static final class TimeDecayedEWMA {
        private final long tauMillis;
        private double value = Double.NaN;
        private long lastTs = 0L;
        TimeDecayedEWMA(long tauMillis) { this.tauMillis = tauMillis; }
        synchronized void update(double x) {
            long now = System.currentTimeMillis();
            if (lastTs == 0L || Double.isNaN(value)) {
                value = x;
                lastTs = now;
                return;
            }
            long dt = Math.max(1L, now - lastTs);
            double alpha = 1.0 - Math.exp(-((double) dt) / ((double) tauMillis));
            value = alpha * x + (1.0 - alpha) * value;
            lastTs = now;
        }
        double value() { return Double.isNaN(value) ? 0.0 : value; }
    }
    static final class P2Quantile {
        private final double p;
        private boolean init = false;
        private int count = 0;
        private final double[] q = new double[5];
        private final int[] n = new int[5];
        private final double[] np = new double[5];
        private final double[] d = new double[5];
        P2Quantile(double p) { this.p = p; }
        synchronized void add(double x) {
            if (!init) {
                q[count] = x;
                count++;
                if (count == 5) {
                    java.util.Arrays.sort(q);
                    for (int i = 0; i < 5; i++) n[i] = i + 1;
                    np[0] = 1; np[1] = 1 + 2 * p; np[2] = 1 + 4 * p; np[3] = 3 + 2 * p; np[4] = 5;
                    d[0] = 0; d[1] = p / 2; d[2] = p; d[3] = (1 + p) / 2; d[4] = 1;
                    init = true;
                }
                return;
            }
            int k;
            if (x < q[0]) { q[0] = x; k = 0; }
            else if (x < q[1]) { k = 0; }
            else if (x < q[2]) { k = 1; }
            else if (x < q[3]) { k = 2; }
            else if (x <= q[4]) { k = 3; }
            else { q[4] = x; k = 3; }
            for (int i = k + 1; i < 5; i++) n[i]++;
            np[0] += d[0]; np[1] += d[1]; np[2] += d[2]; np[3] += d[3]; np[4] += d[4];
            adjust(1); adjust(2); adjust(3);
        }
        private void adjust(int i) {
            int ni = n[i];
            double npi = np[i];
            int di = (int) Math.signum(npi - ni);
            if (di == 0) return;
            int i1 = i - 1, i2 = i + 1;
            double qip = parabolic(i, di);
            if (qip > q[i1] && qip < q[i2]) q[i] = qip;
            else q[i] = linear(i, di);
            n[i] += di;
        }
        private double parabolic(int i, int d) {
            int i1 = i - 1, i2 = i + 1;
            double a = d / (double)(n[i2] - n[i1]);
            double b = (n[i] - n[i1] + d) * (q[i2] - q[i]) / (n[i2] - n[i]);
            double c = (n[i2] - n[i] - d) * (q[i] - q[i1]) / (n[i] - n[i1]);
            return q[i] + a * (b + c);
        }
        private double linear(int i, int d) {
            int i2 = i + d;
            return q[i] + d * (q[i2] - q[i]) / (n[i2] - n[i]);
        }
        double value() {
            if (!init) {
                if (count == 0) return 0.0;
                double[] c = java.util.Arrays.copyOf(q, count);
                java.util.Arrays.sort(c);
                int idx = (int) Math.floor(Math.max(0, Math.min(count - 1, p * (count - 1))));
                return c[idx];
            }
            return q[2];
        }
    }
}