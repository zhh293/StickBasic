package com.eagle.gateway.server.filter.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;


public class RemoteAddrKeyResolver implements KeyResolver {

	@Override
	public Mono<String> resolve(ServerWebExchange exchange) {
		return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
	}

}
