package com.eagle.gateway.server.filter.factory;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.eagle.gateway.server.enums.SecurityHeaderKey;
import com.eagle.gateway.server.enums.ServerErrorCode;
import com.eagle.gateway.server.exception.ServerException;

@Slf4j
@Component
public class SignatureValidationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<SignatureValidationGatewayFilterFactory.Config> {

    public SignatureValidationGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String signature = request.getHeaders().getFirst(config.getSignatureHeader());
            String reqid = request.getHeaders().getFirst(SecurityHeaderKey.REQID.value());
            String reqtime = request.getHeaders().getFirst(SecurityHeaderKey.REQTIME.value());

            if (StringUtils.isAnyBlank(signature, reqid, reqtime)) {
                throw new ServerException(ServerErrorCode.ILLEGAL_SECURITY_HEADER);
            }

            String canonical = buildCanonicalString(request, reqid, reqtime, config.isIncludeQuery(), config.isIncludeBody());
            String expected = hmacSha256Base64(canonical, config.getSecret());

            if (!StringUtils.equals(expected, signature)) {
                throw new ServerException(ServerErrorCode.AUTHENTICATE_FAILED, "签名校验失败");
            }
            return chain.filter(exchange);
        };
    }

    private String buildCanonicalString(ServerHttpRequest request, String reqid, String reqtime,
                                        boolean includeQuery, boolean includeBody) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getMethodValue()).append('\n');
        sb.append(request.getURI().getPath()).append('\n');
        sb.append(reqid).append('\n').append(reqtime).append('\n');

        if (includeQuery) {
            MultiValueMap<String, String> q = request.getQueryParams();
            List<String> parts = new ArrayList<>();
            q.forEach((k, vals) -> {
                for (String v : vals) {
                    parts.add(k + "=" + v);
                }
            });
            Collections.sort(parts);
            sb.append(String.join("&", parts)).append('\n');
        }

        if (includeBody) {
            // 仅支持已经在解密过滤器中放入的明文 body
            Object bodyPlain = request.getHeaders().getFirst("X-Body-Plain");
            if (bodyPlain != null) {
                sb.append(bodyPlain.toString());
            }
        }
        return sb.toString();
    }

    private String hmacSha256Base64(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Data
    public static class Config {
        private String secret;
        private String signatureHeader = "x-ca-signature";
        private boolean includeQuery = true;
        private boolean includeBody = false; // 可与解密过滤器配合
    }
}