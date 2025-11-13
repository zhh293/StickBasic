package com.tmd.metrics;


import com.tmd.metrics.impl.MetricsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class MetricsAspect {

    private final MetricsServiceImpl metricsService;

    @Around("execution(public * com.tmd.controller..*(..))")
    public Object controller(ProceedingJoinPoint pjp) throws Throwable {
        String name = pjp.getSignature().getDeclaringTypeName() + "." + pjp.getSignature().getName();
        long t = System.nanoTime();
        metricsService.inc("http.calls", name);
        Object ret = pjp.proceed();
        long d = (System.nanoTime() - t) / 1_000_000;
        metricsService.timer("http.latency", name, d);
        return ret;
    }

    @Around("execution(public * com.tmd.service.impl.MailServiceImpl.*(..))")
    public Object mail(ProceedingJoinPoint pjp) throws Throwable {
        String name = pjp.getSignature().getName();
        long t = System.nanoTime();
        metricsService.inc("mail.calls", name);
        Object ret = pjp.proceed();
        long d = (System.nanoTime() - t) / 1_000_000;
        metricsService.timer("mail.latency", name, d);
        return ret;
    }
}