package com.eagle.gateway.server.filter.factory;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.eagle.gateway.server.domain.LoginUser;
import com.eagle.gateway.server.domain.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import com.eagle.gateway.server.enums.ServerErrorCode;
import com.eagle.gateway.server.enums.ServerExchangeKey;
import com.eagle.gateway.server.exception.ServerException;

import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.Config> {

	@Autowired
	private RedisCache redisCache;

	// session有状态验证
	/*
	 * @Override
	 * public GatewayFilter apply(Config config) {
	 * return (exchange, chain) -> {
	 * log.debug("========进入认证过滤器========");
	 * 
	 * // 过滤白名单
	 * Object isWhitelistUrlFlag =
	 * exchange.getAttribute(ServerExchangeKey.is_whitelist_url.name());
	 * if (null != isWhitelistUrlFlag &&
	 * Boolean.valueOf(isWhitelistUrlFlag.toString()))
	 * return chain.filter(exchange);
	 * 
	 * WebSession webSession = exchange.getSession().block();
	 * if (null == webSession.getAttribute(ServerExchangeKey.gw_user.name()))
	 * throw new ServerException(ServerErrorCode.AUTHENTICATE_FAILED);
	 * 
	 * try {
	 * exchange.getAttributes().put(ServerExchangeKey.appid.name(),
	 * webSession.getAttribute(ServerExchangeKey.appid.name()));
	 * exchange.getAttributes().put(ServerExchangeKey.gw_session.name(),
	 * webSession.getAttribute(ServerExchangeKey.gw_user.name()));
	 * return chain.filter(exchange);
	 * } catch (Exception e) {
	 * log.error(e.getMessage(), e);
	 * throw new ServerException(ServerErrorCode.AUTHENTICATE_FAILED);
	 * }
	 * };
	 * }
	 */
	// token无状态认证
	// AuthGatewayFilterFactory.java
	@Override
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {
			try {
				// 1. 白名单过滤（逻辑不变）
            	Object isWhitelistUrlFlag = exchange.getAttribute(ServerExchangeKey.is_whitelist_url.name());
            	if (null != isWhitelistUrlFlag && Boolean.parseBoolean(isWhitelistUrlFlag.toString())) {
                	return chain.filter(exchange);
            	}
				// 从请求头获取token
				String token = extractToken(exchange.getRequest());
				if (StrUtil.isBlank(token)) {
					throw new ServerException(ServerErrorCode.AUTHENTICATE_FAILED);
				}

				// 验证token有效性（可选：解析JWT直接验证，或查询Redis）
				if (!validateToken(token)) {
					throw new ServerException(ServerErrorCode.AUTHENTICATE_FAILED);
				}
				// 检查token是否过期
				if (redisCache.getExpire("login:" + token) < 0) {
					throw new ServerException(ServerErrorCode.TOKEN_EXPIRED);
				}
				// 从Redis获取用户信息
				Object cacheObject = redisCache.getCacheObject("login:" + token);
				LoginUser loginUser = JSONUtil.toBean(JSONUtil.toJsonStr(cacheObject), LoginUser.class);
				if (loginUser == null || loginUser.getUser() == null) {
					throw new ServerException(ServerErrorCode.AUTHENTICATE_FAILED);
				}

				// 将用户信息放入请求头传递给下游服务
				String userId = loginUser.getUser().getId() != null ? String.valueOf(loginUser.getUser().getId()) : "";
				String userName = loginUser.getUser().getUserName();
				String userInfo = JSONObject.toJSONString(loginUser);
				exchange = exchange.mutate()
						.request(exchange.getRequest().mutate()
								.header("X-User-Id", userId)
								.header("X-User-Name", userName != null ? userName : "")
								.header("X-User-Info", Base64.getEncoder().encodeToString(userInfo.getBytes()))
								.build())
						.build();

				// 刷新过期时间
				redisCache.setCacheObject("login:" + token, loginUser, 30, TimeUnit.MINUTES);

				return chain.filter(exchange);
			} catch (Exception e) {
				throw new ServerException(ServerErrorCode.AUTHENTICATE_FAILED);
			}
		};
	}

	private boolean validateToken(String token) {
		if (redisCache.hasKey("login:" + token)) {
			return true;
		}
		return false;
	}

	private String extractToken(ServerHttpRequest request) {
		String token = request.getHeaders().getFirst("Authorization");
		if (token != null) {
			token = token.trim();
			if (token.toLowerCase().startsWith("bearer ")) {
				return token.substring(7).trim();
			}
			return token;
		} else {
			return null;
		}
	}

	public AuthGatewayFilterFactory() {
		super(Config.class);
	}

	public static class Config {

	}

}
