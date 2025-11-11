# Elasticsearch 配置与启动全流程（与当前项目兼容）

本指南涵盖从环境准备、安装、分词插件、索引 schema、Spring 应用连接、启动时初始化到验证与常见问题的完整流程，与你项目中的 `SearchController`/`SearchServiceImpl` 与 `PostsServiceImpl` 保持一致。

## 概览
- 索引名：`posts`
- 关键字段：`title`、`content`、`authorUsername`、`topicName`、`viewCount`、`createdAt`、`status` 等
- 分词：中文分词（IK），并提供同义词（可选）
- 前缀匹配：通过 `search_as_you_type` 自动生成 `._2gram/_3gram` 子字段
- 排序：`viewCount` 降序、`createdAt` 降序

## 环境准备
- 安装 Elasticsearch（建议与项目依赖版本一致，例如 7.17.x 或 8.x）
- 开发环境可关闭安全；生产环境请启用安全并配置用户名/密码
- 安装中文分词插件 `analysis-ik`（版本必须与 ES 完全匹配）

## 安装 Elasticsearch（Windows）
1. 下载并解压，例如：`C:\elasticsearch-7.17.15\`
2. 开发模式下可关闭安全（仅开发环境）：编辑 `config\elasticsearch.yml` 增加：
   - `network.host: 0.0.0.0`
   - `http.port: 9200`
   - `xpack.security.enabled: false`
3. 启动：`C:\elasticsearch-7.17.15\bin\elasticsearch.bat`
4. 健康检查：
   - `curl http://localhost:9200/`
   - 或 PowerShell：`Invoke-RestMethod http://localhost:9200/`

## 安装 IK 分词插件
- Windows 安装示例（替换为与你 ES 版本匹配的 IK 包）：
  - `C:\elasticsearch-7.17.15\bin\elasticsearch-plugin.bat install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v7.17.15/elasticsearch-analysis-ik-7.17.15.zip`
- 重启 ES 后验证分词：
  - `curl "http://localhost:9200/_analyze" -H "Content-Type: application/json" -d "{\"analyzer\":\"ik_smart\",\"text\":\"全文检索与微服务架构\"}"`

## 创建索引 schema（posts）
`SearchServiceImpl` 会查询以下字段，`PostsServiceImpl` 会写入对应字段。确保索引 `posts` 使用如下 settings/mappings（可以在 Kibana Dev Tools 或命令行执行）：

```
PUT posts
{
  "settings": {
    "analysis": {
      "filter": {
        "synonym_filter": {
          "type": "synonym_graph",
          "synonyms": [
            "微服务,微服务架构,msa,microservice",
            "分布式,分布式系统,distributed",
            "缓存,cache,redis",
            "搜索,检索,全文检索,es,elasticsearch"
          ]
        }
      },
      "analyzer": {
        "ik_synonym": {
          "type": "custom",
          "tokenizer": "ik_smart",
          "filter": [ "lowercase", "synonym_filter" ]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id":           { "type": "long" },
      "userId":       { "type": "long" },
      "topicId":      { "type": "long" },
      "title": {
        "type": "search_as_you_type",
        "analyzer": "ik_synonym",
        "search_analyzer": "ik_synonym"
      },
      "content": {
        "type": "text",
        "analyzer": "ik_synonym",
        "search_analyzer": "ik_synonym"
      },
      "authorUsername": {
        "type": "search_as_you_type",
        "analyzer": "ik_synonym",
        "search_analyzer": "ik_synonym"
      },
      "topicName": {
        "type": "search_as_you_type",
        "analyzer": "ik_synonym",
        "search_analyzer": "ik_synonym"
      },
      "likeCount":     { "type": "integer" },
      "commentCount":  { "type": "integer" },
      "shareCount":    { "type": "integer" },
      "viewCount":     { "type": "integer" },
      "createdAt":     { "type": "date", "format": "epoch_millis" },
      "updatedAt":     { "type": "date", "format": "epoch_millis" },
      "status":        { "type": "keyword" },
      "authorAvatar":  { "type": "keyword", "index": false }
    }
  }
}
```

说明：
- `search_as_you_type` 自动生成 `title._2gram/_3gram` 等子字段，支持前缀匹配；与 `SearchServiceImpl` 的 multi-match 字段集匹配。
- 时间戳以毫秒写入（`epoch_millis`），与搜索时的排序兼容。

## Spring 应用配置
在 `application.properties`（或 `application.yml`）中加入：

```
spring.elasticsearch.uris=http://localhost:9200
# 若开启安全：
# spring.elasticsearch.username=elastic
# spring.elasticsearch.password=your_password
```

示例 ES 客户端 Bean（与项目现状一致）：

```java
@Configuration
public class EsConfig {
  @Value("${spring.elasticsearch.uris}")
  private String esUri;

  @Bean
  public RestHighLevelClient restHighLevelClient() {
    return new RestHighLevelClient(RestClient.builder(HttpHost.create(esUri)));
  }
}
```

## 启动时索引初始化（推荐）
为保证环境一致性，应用启动时自动检查 `posts` 是否存在，不存在则创建上述 settings/mappings。

```java
@Component
@RequiredArgsConstructor
public class PostsIndexInitializer implements ApplicationRunner {
  private final RestHighLevelClient esClient;

  @Override
  public void run(ApplicationArguments args) throws Exception {
    boolean exists = esClient.indices().exists(new GetIndexRequest("posts"), RequestOptions.DEFAULT);
    if (!exists) {
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
        {"properties":{"id":{"type":"long"},"userId":{"type":"long"},"topicId":{"type":"long"},
        "title":{"type":"search_as_you_type","analyzer":"ik_synonym","search_analyzer":"ik_synonym"},
        "content":{"type":"text","analyzer":"ik_synonym","search_analyzer":"ik_synonym"},
        "authorUsername":{"type":"search_as_you_type","analyzer":"ik_synonym","search_analyzer":"ik_synonym"},
        "topicName":{"type":"search_as_you_type","analyzer":"ik_synonym","search_analyzer":"ik_synonym"},
        "likeCount":{"type":"integer"},"commentCount":{"type":"integer"},"shareCount":{"type":"integer"},
        "viewCount":{"type":"integer"},"createdAt":{"type":"date","format":"epoch_millis"},
        "updatedAt":{"type":"date","format":"epoch_millis"},"status":{"type":"keyword"},
        "authorAvatar":{"type":"keyword","index":false}}}
      """;
      req.settings(settings, XContentType.JSON);
      req.mapping(mappings, XContentType.JSON);
      esClient.indices().create(req, RequestOptions.DEFAULT);
    }
  }
}
```

## 验证流程
1. 启动 ES 并确认健康：`curl http://localhost:9200/`
2. 检查索引是否存在：`curl http://localhost:9200/posts`
3. 启动应用，创建帖子（`POST /posts`），后台日志出现 ES 索引写入成功信息。
4. 搜索接口验证：`GET /search?keyword=微服务&type=all&page=1&size=10`
   - 返回中能看到高亮 `<em>…</em>` 片段
   - 排序符合 `viewCount` 降序 + `createdAt` 降序

## 常见问题
- `search_as_you_type` 子字段不存在：索引 mappings 若未正确设置，需要删除并重建索引。
- IK 插件版本不匹配：插件必须与 ES 主版本一致，否则启动时报插件加载错误。
- 开启安全：在应用配置中设置 `spring.elasticsearch.username/password` 或在客户端上加 Basic Auth。
- 同义词更新：若使用 `synonyms_path` 文件管理同义词，修改后需重载或关闭/开启索引；内联同义词需重建索引以生效。

## 运维建议
- 将 `posts` 索引初始化逻辑保留在代码中，避免环境差异导致搜索体验波动。
- 对于大规模数据，建议使用 `index templates` 管理 settings/mappings，并使用 `rollover`。
- 在生产环境开启 `xpack.security`，并使用专用只读账号供应用查询，以降低风险。

---
如需我把 `PostsIndexInitializer` 直接加入你的项目（`src/main/java/com/tmd/config`）并完善依赖导入，请告知，我可以帮你补充代码文件。该文档已与当前服务实现保持一致，无需改动现有查询逻辑。