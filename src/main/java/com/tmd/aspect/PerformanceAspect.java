package com.tmd.aspect;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Aspect
@Component
public class PerformanceAspect {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceAspect.class);
    
    private final MeterRegistry meterRegistry;
    
    public PerformanceAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 定义Controller层切点
     * 匹配所有Controller类中的方法
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerMethods() {}

    /**
     * 定义Service层切点
     * 匹配所有Service类中的方法
     */
    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceMethods() {}

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
        
        Timer.Sample sample = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            sample.stop(meterRegistry.timer("controller.execution.time", 
                "method", operation, "status", "success"));
            
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
            
            logger.error("Controller method {} failed after {}ms with error: {}", 
                operation, executionTime, e.getMessage(), e);
            meterRegistry.counter("controller.error.count", "method", operation, "error", e.getClass().getSimpleName()).increment();
            
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
        
        Timer.Sample sample = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            sample.stop(meterRegistry.timer("service.execution.time", 
                "method", operation, "status", "success"));
            
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
            
            logger.error("Service method {} failed after {}ms with error: {}", 
                operation, executionTime, e.getMessage(), e);
            meterRegistry.counter("service.error.count", "method", operation, "error", e.getClass().getSimpleName()).increment();
            
            throw e;
        }
    }
}