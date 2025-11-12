package com.eagle.gateway.server.filter;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestTraceAndSecurityHeadersGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String reqId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        if (reqId == null || reqId.isEmpty()) {
            reqId = UUID.randomUUID().toString();
        }
        exchange.getResponse().getHeaders().set("X-Request-Id", reqId);

        // 安全响应头
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set("X-Content-Type-Options", "nosniff");
        response.getHeaders().set("X-Frame-Options", "DENY");
        response.getHeaders().set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        // 可选：CSP 最佳实践需结合实际静态资源策略
        // response.getHeaders().set("Content-Security-Policy", "default-src 'self'");

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 在大多数过滤器之前执行
        return Ordered.HIGHEST_PRECEDENCE;
    }
}