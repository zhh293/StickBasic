package com.tmd.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostsIndexInitializer implements ApplicationRunner {

    private final RestHighLevelClient esClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            boolean exists = esClient.indices().exists(new GetIndexRequest("posts"), RequestOptions.DEFAULT);
            if (exists) {
                log.info("ES index 'posts' already exists. Skip initialization.");
                return;
            }

            // 优先创建: IK + 同义词 + search_as_you_type
            CreateIndexRequest req = new CreateIndexRequest("posts");
            String settings = """
                {"analysis":{"filter":{"synonym_filter":{"type":"synonym_graph","synonyms":[
                  "微服务,微服务架构,msa,microservice",
                  "分布式,分布式系统,distributed",
                  "缓存,cache,redis",
                  "搜索,检索,全文检索,es,elasticsearch"
                ]}},"analyzer":{"ik_synonym":{"type":"custom","tokenizer":"ik_smart","filter":["lowercase","synonym_filter"]}}}}
            """;
            String mappings = """
                {"properties":{
                  "id":{"type":"long"},
                  "userId":{"type":"long"},
                  "topicId":{"type":"long"},
                  "title":{"type":"search_as_you_type","analyzer":"ik_synonym","search_analyzer":"ik_synonym"},
                  "content":{"type":"text","analyzer":"ik_synonym","search_analyzer":"ik_synonym"},
                  "authorUsername":{"type":"search_as_you_type","analyzer":"ik_synonym","search_analyzer":"ik_synonym"},
                  "topicName":{"type":"search_as_you_type","analyzer":"ik_synonym","search_analyzer":"ik_synonym"},
                  "likeCount":{"type":"integer"},
                  "commentCount":{"type":"integer"},
                  "shareCount":{"type":"integer"},
                  "viewCount":{"type":"integer"},
                  "collectCount":{"type":"integer"},
                  "createdAt":{"type":"date","format":"epoch_millis"},
                  "updatedAt":{"type":"date","format":"epoch_millis"},
                  "status":{"type":"keyword"},
                  "authorAvatar":{"type":"keyword","index":false}
                }}
            """;
            req.settings(settings, XContentType.JSON);
            req.mapping(mappings, XContentType.JSON);
            esClient.indices().create(req, RequestOptions.DEFAULT);
            log.info("ES index 'posts' created with IK analyzer.");
        } catch (Exception e) {
            log.warn("Create 'posts' index with IK failed, try fallback to standard analyzer.", e);
            try {
                boolean exists = esClient.indices().exists(new GetIndexRequest("posts"), RequestOptions.DEFAULT);
                if (exists) return;
                CreateIndexRequest reqStd = new CreateIndexRequest("posts");
                String settingsStd = "{}"; // 使用默认分析器
                String mappingsStd = """
                    {"properties":{
                      "id":{"type":"long"},
                      "userId":{"type":"long"},
                      "topicId":{"type":"long"},
                      "title":{"type":"search_as_you_type"},
                      "content":{"type":"text"},
                      "authorUsername":{"type":"search_as_you_type"},
                      "topicName":{"type":"search_as_you_type"},
                      "likeCount":{"type":"integer"},
                      "commentCount":{"type":"integer"},
                      "collectCount":{"type":"integer"},
                      "shareCount":{"type":"integer"},
                      "viewCount":{"type":"integer"},
                      "createdAt":{"type":"date","format":"epoch_millis"},
                      "updatedAt":{"type":"date","format":"epoch_millis"},
                      "status":{"type":"keyword"},
                      "authorAvatar":{"type":"keyword","index":false}
                    }}
                """;
                reqStd.settings(settingsStd, XContentType.JSON);
                reqStd.mapping(mappingsStd, XContentType.JSON);
                esClient.indices().create(reqStd, RequestOptions.DEFAULT);
                log.info("ES index 'posts' created with standard analyzer.");
            } catch (Exception ex) {
                log.error("Fallback creation for 'posts' index failed.", ex);
            }
        }
    }
}