@Slf4j
@Component
public class XssProtectGatewayFilterFactory
        extends AbstractGatewayFilterFactory<XssProtectGatewayFilterFactory.Config> {

    private final XssProperties xssProperties;
    private final PathMatcher pathMatcher = new AntPathMatcher();

    public XssProtectGatewayFilterFactory(XssProperties xssProperties) {
        super(Config.class);
        this.xssProperties = xssProperties;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // 1. 检查是否启用XSS防护
            if (!xssProperties.isEnabled()) {
                return chain.filter(exchange);
            }

            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();
            String clientIp = getClientIp(request);

            // 2. 检查是否在排除路径中
            if (xssProperties.getExcludePaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
                log.debug("XSS防护：路径[{}]匹配排除规则，跳过检测", path);
                return chain.filter(exchange);
            }

            // 3. 处理请求参数（路径参数、查询参数、请求头、请求体）
            ServerHttpRequest modifiedRequest = request;

            // 3.1 处理路径参数（如/user/{id}中的id）
            if (config.isIncludePathParams()) {
                Map<String, String> pathParams = exchange
                        .getAttribute(ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (pathParams != null && !pathParams.isEmpty()) {
                    // 路径参数无法直接修改，需记录日志并在后续业务层校验（或通过重写路径实现）
                    for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                        String paramName = entry.getKey();
                        String paramValue = entry.getValue();
                        if (isExcludeParam(paramName) || paramValue == null)
                            continue;

                        if (XssUtils.isMalicious(paramValue)) {
                            log.warn("XSS检测：路径参数[{}={}]含恶意内容，客户端IP={}", paramName, paramValue, clientIp);
                        }
                    }
                }
            }

            // 3.2 处理查询参数（修改URI）
            if (config.isIncludeQueryParams()) {
                MultiValueMap<String, String> queryParams = request.getQueryParams();
                MultiValueMap<String, String> sanitizedQueryParams = new LinkedMultiValueMap<>();

                for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
                    String paramName = entry.getKey();
                    if (isExcludeParam(paramName)) {
                        sanitizedQueryParams.put(paramName, entry.getValue());
                        continue;
                    }

                    List<String> sanitizedValues = entry.getValue().stream()
                            .map(value -> {
                                String sanitized = XssUtils.sanitize(value, xssProperties.getMode());
                                if (!sanitized.equals(value) && XssUtils.isMalicious(value)) {
                                    log.warn("XSS净化：查询参数[{}]原值[{}]被净化为[{}]，客户端IP={}",
                                            paramName, value, sanitized, clientIp);
                                }
                                return sanitized;
                            })
                            .collect(Collectors.toList());
                    sanitizedQueryParams.put(paramName, sanitizedValues);
                }

                // 重新构建URI（替换查询参数）
                modifiedRequest = buildNewRequestWithQueryParams(request, sanitizedQueryParams);
            }

            // 3.3 处理请求头
            if (config.isIncludeHeaders()) {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(modifiedRequest.getHeaders());

                for (String headerName : xssProperties.getIncludeHeaders()) {
                    List<String> headerValues = headers.get(headerName);
                    if (headerValues == null)
                        continue;

                    List<String> sanitizedValues = headerValues.stream()
                            .map(value -> {
                                String sanitized = XssUtils.sanitize(value, xssProperties.getMode());
                                if (!sanitized.equals(value) && XssUtils.isMalicious(value)) {
                                    log.warn("XSS净化：请求头[{}]原值[{}]被净化为[{}]，客户端IP={}",
                                            headerName, value, sanitized, clientIp);
                                }
                                return sanitized;
                            })
                            .collect(Collectors.toList());
                    headers.set(headerName, sanitizedValues);
                }

                modifiedRequest = modifiedRequest.mutate().headers(httpHeaders -> httpHeaders.putAll(headers)).build();
            }

            // 3.4 处理请求体（JSON/表单）
            if (config.isIncludeBody() && canModifyBody(modifiedRequest)) {
                return modifyRequestBody(exchange, modifiedRequest, clientIp, chain);
            }

            // 4. 传递净化后的请求
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        };
    }

    /**
     * 修改请求体（支持JSON/表单）
     */
    private Mono<Void> modifyRequestBody(ServerWebExchange exchange, ServerHttpRequest request, String clientIp,
            GatewayFilterChain chain) {
        ServerRequest serverRequest = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
        MediaType contentType = request.getHeaders().getContentType();

        Mono<String> modifiedBody = serverRequest.bodyToMono(String.class)
                .flatMap(body -> {
                    // 限制最大长度，避免性能问题
                    if (body.length() > xssProperties.getMaxScanLength()) {
                        log.warn("XSS防护：请求体长度[{}]超过最大限制[{}]，跳过检测", body.length(), xssProperties.getMaxScanLength());
                        return Mono.just(body);
                    }

                    // 处理JSON类型
                    if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                        return sanitizeJsonBody(body, clientIp);
                    }

                    // 处理表单类型
                    if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
                        return sanitizeFormBody(body, clientIp);
                    }

                    // 其他类型（如XML可扩展）
                    return Mono.just(body);
                });

        // 用净化后的body替换原请求体
        BodyInserter<Mono<String>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody,
                String.class);
        CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, request.getHeaders());

        return bodyInserter.insert(outputMessage, new BodyInserterContext())
                .then(Mono.defer(() -> {
                    ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(request) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return outputMessage.getBody();
                        }

                        @Override
                        public HttpHeaders getHeaders() {
                            HttpHeaders headers = new HttpHeaders();
                            headers.putAll(super.getHeaders());
                            headers.setContentLength(
                                    outputMessage.getBody().map(DataBuffer::readableByteCount).reduce(0L, Long::sum));
                            return headers;
                        }
                    };
                    return chain.filter(exchange.mutate().request(decoratedRequest).build());
                }));
    }

    /**
     * 净化JSON请求体（递归处理所有字段值）
     */
    private Mono<String> sanitizeJsonBody(String jsonBody, String clientIp) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonBody);
            JsonNode sanitizedNode = sanitizeJsonNode(rootNode, clientIp, "");
            return Mono.just(objectMapper.writeValueAsString(sanitizedNode));
        } catch (Exception e) {
            log.error("XSS净化JSON失败", e);
            return Mono.just(jsonBody); // 解析失败时不修改，避免业务异常
        }
    }

    private JsonNode sanitizeJsonNode(JsonNode node, String clientIp, String parentPath) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                String currentPath = parentPath.isEmpty() ? fieldName : parentPath + "." + fieldName;

                if (isExcludeParam(fieldName)) {
                    continue; // 跳过排除的参数
                }

                JsonNode childNode = field.getValue();
                objectNode.set(fieldName, sanitizeJsonNode(childNode, clientIp, currentPath));
            }
            return objectNode;
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode element = arrayNode.get(i);
                arrayNode.set(i, sanitizeJsonNode(element, clientIp, parentPath + "[" + i + "]"));
            }
            return arrayNode;
        } else if (node.isTextual()) {
            String originalValue = node.asText();
            String sanitizedValue = XssUtils.sanitize(originalValue, xssProperties.getMode());
            if (!sanitizedValue.equals(originalValue) && XssUtils.isMalicious(originalValue)) {
                log.warn("XSS净化：JSON字段[{}]原值[{}]被净化为[{}]，客户端IP={}",
                        parentPath, originalValue, sanitizedValue, clientIp);
            }
            return new TextNode(sanitizedValue);
        } else {
            return node; // 非字符串类型不处理
        }
    }

    /**
     * 净化表单请求体
     */
    private Mono<String> sanitizeFormBody(String formBody, String clientIp) {
        try {
            MultiValueMap<String, String> formData = UriComponentsBuilder.newInstance()
                    .query(formBody)
                    .build()
                    .getQueryParams();

            MultiValueMap<String, String> sanitizedFormData = new LinkedMultiValueMap<>();
            for (Map.Entry<String, List<String>> entry : formData.entrySet()) {
                String paramName = entry.getKey();
                if (isExcludeParam(paramName)) {
                    sanitizedFormData.putAll(entry);
                    continue;
                }

                List<String> sanitizedValues = entry.getValue().stream()
                        .map(value -> {
                            String sanitized = XssUtils.sanitize(value, xssProperties.getMode());
                            if (!sanitized.equals(value) && XssUtils.isMalicious(value)) {
                                log.warn("XSS净化：表单参数[{}]原值[{}]被净化为[{}]，客户端IP={}",
                                        paramName, value, sanitized, clientIp);
                            }
                            return sanitized;
                        })
                        .collect(Collectors.toList());
                sanitizedFormData.put(paramName, sanitizedValues);
            }

            // 重新拼接表单数据
            return Mono.just(sanitizedFormData.entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream()
                            .map(value -> entry.getKey() + "="
                                    + URLEncoder.encode(value, StandardCharsets.UTF_8.name())))
                    .collect(Collectors.joining("&")));
        } catch (Exception e) {
            log.error("XSS净化表单失败", e);
            return Mono.just(formBody);
        }
    }

    /**
     * 构建包含净化后查询参数的新请求
     */
    private ServerHttpRequest buildNewRequestWithQueryParams(ServerHttpRequest request,
            MultiValueMap<String, String> sanitizedQueryParams) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(request.getURI());
        uriBuilder.replaceQueryParams(sanitizedQueryParams);
        return request.mutate().uri(uriBuilder.build().toUri()).build();
    }

    /**
     * 检查参数是否在排除列表中
     */
    private boolean isExcludeParam(String paramName) {
        return xssProperties.getExcludeParams().stream().anyMatch(exclude -> exclude.equalsIgnoreCase(paramName));
    }

    /**
     * 判断是否可以修改请求体（仅支持POST/PUT/PATCH，且内容类型可解析）
     */
    private boolean canModifyBody(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        if (method == null || !(method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT)
                || method.equals(HttpMethod.PATCH))) {
            return false;
        }

        MediaType contentType = request.getHeaders().getContentType();
        return contentType != null && (MediaType.APPLICATION_JSON.isCompatibleWith(contentType) ||
                MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType));
    }

    /**
     * 获取客户端真实IP（处理代理）
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    /**
     * 过滤器配置类（控制需要检测的维度）
     */
    @Data
    public static class Config {
        private boolean includePathParams = true; // 检测路径参数
        private boolean includeQueryParams = true; // 检测查询参数
        private boolean includeHeaders = true; // 检测请求头
        private boolean includeBody = true; // 检测请求体
    }
}

/*
 * 
 * app:
 * xss:
 * enabled: true
 * mode: ESCAPE # 转义模式
 * exclude-paths:
 * - /static/**
 * - /api/v1/rich-text/save # 富文本接口
 * exclude-params:
 * - content # 富文本内容字段
 * - html
 * include-headers:
 * - User-Agent
 * - Referer
 * - Cookie
 * max-scan-length: 1048576 # 1MB
 * 
 * 
 */
