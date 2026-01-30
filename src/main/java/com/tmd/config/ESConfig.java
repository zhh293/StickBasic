package com.tmd.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ESConfig {
    // 读取环境变量SPRING_ELASTICSEARCH_HOST，默认localhost；端口同理
    @Value("${SPRING_ELASTICSEARCH_HOST:localhost}")
    private String esHost;

    @Value("${SPRING_ELASTICSEARCH_PORT:9200}")
    private int esPort;
    @Bean
    public RestHighLevelClient restHighLevelClient() {
        RestClientBuilder builder = RestClient.builder(new HttpHost(esHost, esPort, "http"));
        return new RestHighLevelClient(builder);
    }
}
