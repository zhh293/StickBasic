package com.eagle.gateway.server.filter.factory;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

import com.eagle.gateway.server.enums.ServerErrorCode;
import com.eagle.gateway.server.enums.ServerExchangeKey;
import com.eagle.gateway.server.exception.ServerException;
import com.eagle.gateway.server.prop.SqlInjectProperties;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Component
public class SqlInspectGatewayFilterFactory
        extends AbstractGatewayFilterFactory<SqlInspectGatewayFilterFactory.Config> {

    // 注入配置类（非静态，避免并发问题）
    private final SqlInjectProperties sqlInjectProperties;

    public SqlInspectGatewayFilterFactory(SqlInjectProperties sqlInjectProperties) {
        super(Config.class);
        this.sqlInjectProperties = sqlInjectProperties;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String clientIp = getClientIp(request); // 获取客户端IP（用于日志）

            String path = request.getPath().value();

            // 检查是否在排除路径中（Ant风格匹配）
            if (config.getExcludePaths().stream().anyMatch(pattern -> PathMatcher.match(pattern, path))) {
                return chain.filter(exchange); // 跳过检测
            }
            // 1. 检测路径参数
            if (config.isIncludePathParams()) {
                Map<String, String> pathParams = exchange
                        .getAttribute(ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (pathParams != null) {
                    for (String value : pathParams.values()) {
                        if (isInject(EncodingUtils.fullDecode(value), clientIp, "path-param")) {
                            throw new ServerException(ServerErrorCode.SQL_INJECT_ERROR);
                        }
                    }
                }
            }

            // 2. 检测Query参数（解码后）
            if (config.isIncludeQueryParams()) {
                MultiValueMap<String, String> queryParams = request.getQueryParams();
                for (List<String> values : queryParams.values()) {
                    for (String value : values) {
                        if (isInject(EncodingUtils.fullDecode(value), clientIp, "query-param")) {
                            throw new ServerException(ServerErrorCode.SQL_INJECT_ERROR);
                        }
                    }
                }
            }

            // 3. 检测请求头（配置需要检测的头，如User-Agent）
            if (config.isIncludeHeaders() && !CollectionUtils.isEmpty(config.getHeadersToCheck())) {
                HttpHeaders headers = request.getHeaders();
                for (String headerName : config.getHeadersToCheck()) {
                    List<String> values = headers.get(headerName);
                    if (values != null) {
                        for (String value : values) {
                            if (isInject(EncodingUtils.fullDecode(value), clientIp, "header:" + headerName)) {
                                throw new ServerException(ServerErrorCode.SQL_INJECT_ERROR);
                            }
                        }
                    }
                }
            }

            // 4. 检测请求体（根据Content-Type解析）
            if (config.isIncludeBody()) {
                String bodyData = exchange.getAttribute(ServerExchangeKey.requestBody.name());
                if (StringUtils.isNotEmpty(bodyData)) {
                    // 解析body（支持JSON/Form/XML等）
                    List<String> bodyValues = parseBody(bodyData, request.getHeaders().getContentType());
                    for (String value : bodyValues) {
                        if (isInject(EncodingUtils.fullDecode(value), clientIp, "body")) {
                            throw new ServerException(ServerErrorCode.SQL_INJECT_ERROR);
                        }
                    }
                }
            }

            return chain.filter(exchange);
        };
    }

    // 解析不同类型的body，提取所有字段值
    private List<String> parseBody(String bodyData, MediaType contentType) {
        List<String> values = new ArrayList<>();
        if (contentType == null)
            return values;

        if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            // 解析JSON（递归提取所有值）
            values.addAll(parseJson(bodyData));
        } else if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
            // 解析表单
            MultiValueMap<String, String> formData = UriComponentsBuilder.newInstance()
                    .query(bodyData)
                    .build()
                    .getQueryParams();
            formData.values().forEach(values::addAll);
        }
        // 可扩展XML、Multipart等类型
        return values;
    }

    // 递归解析JSON，提取所有值
    private List<String> parseJson(String json) {
        List<String> values = new ArrayList<>();
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            extractJsonValues(node, values);
        } catch (Exception e) {
            log.warn("解析JSON失败: {}", e.getMessage());
        }
        return values;
    }

    private void extractJsonValues(JsonNode node, List<String> values) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> extractJsonValues(field.getValue(), values));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(element -> extractJsonValues(element, values));
        } else if (node.isValueNode()) {
            values.add(node.asText());
        }
    }

    // 检测是否为注入内容（含日志记录）
    private boolean isInject(String content, String clientIp, String source) {
        if (StringUtils.isEmpty(content))
            return false;
        boolean matched = sqlInjectProperties.match(content);
        if (matched) {
            log.warn("检测到SQL注入尝试: 来源={}, 客户端IP={}, 内容={}", source, clientIp, content);
        }
        return matched;
    }

    // 获取客户端真实IP（处理代理场景）
    private String getClientIp(ServerHttpRequest request) {
        return request.getHeaders().getFirst("X-Forwarded-For") != null
                ? request.getHeaders().getFirst("X-Forwarded-For").split(",")[0].trim()
                : request.getRemoteAddress().getAddress().getHostAddress();
    }

    @Data
    public static class Config {
        private boolean includePathParams = true; // 是否检测路径参数
        private boolean includeQueryParams = true; // 是否检测Query参数
        private boolean includeHeaders = false; // 是否检测请求头
        private List<String> headersToCheck = Arrays.asList("User-Agent", "Referer"); // 需要检测的头
        private boolean includeBody = true; // 是否检测body
        private List<String> excludePaths = new ArrayList<>(); // 排除的路径（Ant风格）
        private List<String> excludeParams = new ArrayList<>(); // 排除的参数名
    }
}

/*
 * app:
 * sql-inject:
 * regex: >
 * (\b(select|insert|update|delete|drop|truncate|alter|exec|union|create|grant|
 * revoke|declare|fetch|load_file|into|from|where|group by|order
 * by|having|join|like|in|exists|between)\b)|
 * (['";\\/\(\)\[\]\{\}])|
 * (--|#|\/\*.*\*\/)
 * 
 */
