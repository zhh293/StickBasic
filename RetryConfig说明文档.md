# RetryConfig 配置类说明文档

## 1. 概述

RetryConfig 是一个企业级重试配置类，基于 Spring Retry 框架实现。该类提供了针对不同场景的重试模板配置，主要用于处理系统中可能出现的临时性故障，如网络抖动、连接超时等问题。

## 2. 设计理念与重要特性

### 2.1 Spring Retry 框架集成
本配置类充分利用了 Spring Retry 框架的强大功能，该框架提供了声明式和编程式两种重试方式。RetryConfig 采用编程式配置，具有更高的灵活性和可控性。

### 2.2 重试策略设计
配置类中定义了多种重试策略，根据不同业务场景选择合适的策略：

#### redisRetryTemplate
- **适用场景**：Redis 操作重试（网络抖动/连接超时场景）
- **核心特性**：
  - 精准异常控制：只对特定异常进行重试
  - 指数退避策略：避免重试风暴
  - 重试监听机制：完整记录重试过程

#### fixedRetryTemplate
- **适用场景**：简单重试需求场景
- **核心特性**：
  - 固定间隔重试：每次重试间隔相同
  - 简单直接：适用于轻量级重试需求

### 2.3 异常精确控制
通过 `SimpleRetryPolicy` 配置，可以精确控制哪些异常需要重试，哪些不需要：

```java
Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
retryableExceptions.put(RedisConnectionFailureException.class, true);  // 需要重试
retryableExceptions.put(IllegalArgumentException.class, false);         // 不重试
SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
```

这种设计避免了对所有异常都进行重试的粗暴做法，提高了系统的稳定性和效率。

### 2.4 指数退避策略
对于 redisRetryTemplate，采用了指数退避策略（Exponential Backoff）：

```java
ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
backOffPolicy.setInitialInterval(1000); // 初始间隔1秒
backOffPolicy.setMultiplier(2);         // 每次间隔翻倍（1s → 2s → 4s）
backOffPolicy.setMaxInterval(5000);     // 最大间隔5秒
```

该策略可以有效防止"重试风暴"，在网络不稳定时保护下游服务。

### 2.5 重试监听机制
通过注册 RetryListener，可以监控整个重试过程：

```java
retryTemplate.registerListener(new RetryListener() {
    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        // 重试开始时调用
        log.info("Redis操作重试开始，最大重试次数：{}", retryPolicy.getMaxAttempts());
        return true;
    }

    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        // 重试结束时调用
        if (throwable != null) {
            log.error("Redis操作重试失败，已达到最大重试次数", throwable);
        } else {
            log.info("Redis操作重试成功，重试次数：{}", context.getRetryCount());
        }
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        // 每次重试失败时调用
        log.warn("Redis操作重试失败，第{}次重试，异常：{}", context.getRetryCount(), throwable.getMessage());
    }
});
```

这种机制有助于问题排查和系统监控。

## 3. 核心组件说明

### 3.1 RetryTemplate
Spring Retry 的核心类，用于执行需要重试的操作。它将重试逻辑与业务逻辑分离，简化了重试机制的实现。

### 3.2 RetryPolicy（重试策略）
定义何时应该进行重试：
- SimpleRetryPolicy：基于尝试次数的简单策略
- TimeoutRetryPolicy：基于时间的策略
- CircuitBreakerRetryPolicy：断路器模式策略

### 3.3 BackOffPolicy（退避策略）
定义重试间隔时间：
- FixedBackOffPolicy：固定间隔策略
- ExponentialBackOffPolicy：指数退避策略
- UniformRandomBackOffPolicy：随机间隔策略

## 4. 使用示例

### 4.1 注入并使用 redisRetryTemplate
```java
@Autowired
@Qualifier("redisRetryTemplate")
private RetryTemplate redisRetryTemplate;

public void redisOperation() {
    redisRetryTemplate.execute(context -> {
        // 执行Redis操作
        stringRedisTemplate.opsForValue().get("key");
        return null;
    });
}
```

### 4.2 注入并使用 fixedRetryTemplate
```java
@Autowired
@Qualifier("fixedRetryTemplate")
private RetryTemplate fixedRetryTemplate;

public void simpleOperation() {
    fixedRetryTemplate.execute(context -> {
        // 执行简单操作
        return someMethod();
    });
}
```

## 5. 最佳实践与注意事项

### 5.1 幂等性考虑
使用重试机制时，必须确保被重试的操作是幂等的，即多次执行同一个操作不会产生副作用。

### 5.2 异常分类处理
合理区分可重试异常和不可重试异常：
- 可重试：网络异常、超时异常、临时性资源不足等
- 不可重试：业务逻辑错误、参数错误等

### 5.3 重试次数与间隔设置
根据具体业务场景合理设置重试次数和间隔时间：
- 网络操作：建议使用指数退避策略
- 本地操作：可使用固定间隔策略
- 实时性要求高的操作：减少重试次数

### 5.4 监控与日志
完善的日志记录有助于问题排查和系统优化，应记录：
- 重试开始和结束时间
- 每次重试的失败原因
- 最终成功或失败的状态

## 6. 总结

RetryConfig 类体现了以下几个重要设计思想：

1. **关注点分离**：将重试逻辑与业务逻辑完全分离
2. **策略模式**：通过不同策略组合应对不同场景
3. **可扩展性**：易于添加新的重试模板和策略
4. **可观测性**：完善的日志和监控机制
5. **精确控制**：对异常类型和重试行为的精细控制

这套重试机制大大增强了系统的容错能力和稳定性，是构建高可用系统的重要组成部分。