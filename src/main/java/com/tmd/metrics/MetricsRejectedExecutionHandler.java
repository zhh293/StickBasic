package com.tmd.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public class MetricsRejectedExecutionHandler implements RejectedExecutionHandler {
    private final MeterRegistry meterRegistry;
    private final RejectedExecutionHandler delegate = new ThreadPoolExecutor.AbortPolicy();

    public MetricsRejectedExecutionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        meterRegistry.counter("threadpool_rejected_total").increment();
        delegate.rejectedExecution(r, executor);
    }
}