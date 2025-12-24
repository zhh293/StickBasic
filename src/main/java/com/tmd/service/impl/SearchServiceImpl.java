package com.tmd.service.impl;

import cn.hutool.core.util.StrUtil;
import com.tmd.entity.dto.*;
import com.tmd.mapper.AttachmentMapper;
import com.tmd.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final RestHighLevelClient esClient;
    private final AttachmentMapper attachmentMapper;

    private static final String INDEX_POSTS = "posts";

    @Override
    public Result search(String keyword, String type, Integer page, Integer size, String sort,
                         Long topicId, Long startTime, Long endTime) {
        if (StrUtil.isBlank(keyword)) {
            return Result.error("关键字不能为空");
        }
        if (page == null || page < 1)
            page = 1;
        if (size == null || size < 1)
            size = 10;

        try {
            SearchRequest request = new SearchRequest(INDEX_POSTS);
            SearchSourceBuilder source = new SearchSourceBuilder();

            // query: 根据 type 决定搜索字段并设置权重
            String t = StrUtil.isBlank(type) ? "all" : type;
            List<String> fields = new ArrayList<>();
            switch (t) {
                case "post":
                    // 帖子标题/内容，加入 search_as_you_type 子字段以支持前缀匹配
                    fields.add("title^3");
                    fields.add("title._2gram^2");
                    fields.add("title._3gram^2");
                    fields.add("content^2");
                    break;
                case "topic":
                    fields.add("topicName^2");
                    fields.add("topicName._2gram");
                    fields.add("topicName._3gram");
                    break;
                case "user":
                    fields.add("authorUsername^2");
                    fields.add("authorUsername._2gram");
                    fields.add("authorUsername._3gram");
                    break;
                default:
                    fields.add("title^3");
                    fields.add("title._2gram^2");
                    fields.add("title._3gram^2");
                    fields.add("content^2");
                    fields.add("topicName");
                    fields.add("topicName._2gram");
                    fields.add("topicName._3gram");
                    fields.add("authorUsername");
                    fields.add("authorUsername._2gram");
                    fields.add("authorUsername._3gram");
            }

            // 主查询 multi-match，设置中文相关性控制与模糊
            MultiMatchQueryBuilder main = QueryBuilders.multiMatchQuery(keyword);
            for (String f : fields) {
                String name = f.contains("^") ? f.substring(0, f.indexOf('^')) : f;
                float boost = 1.0f;
                if (f.contains("^")) {
                    try {
                        boost = Float.parseFloat(f.substring(f.indexOf('^') + 1));
                    } catch (Exception ignored) {
                    }
                }
                main.field(name, boost);
            }
            // operator/min_should_match 控制
            if ("user".equals(t) || "topic".equals(t)) {
                main.operator(Operator.AND); // 人名/话题多词需全部匹配，提升精确度
            } else {
                main.minimumShouldMatch("60%"); // 标题/内容默认提升相关性
            }
            // 模糊匹配：中文/名称小错别字容错
            if (keyword.length() >= 2 && keyword.length() <= 20) {
                main.fuzziness(Fuzziness.AUTO).prefixLength(1);
            }

            // 简易同义词扩展（可替换为索引级 synonym 分析器），同义词以较低 boost 作为 should
            List<String> synonyms = expandSynonyms(keyword);
            BoolQueryBuilder bool = QueryBuilders.boolQuery();
            bool.filter(QueryBuilders.termQuery("status", "published"));
            // 话题过滤
            if (topicId != null && topicId > 0) {
                bool.filter(QueryBuilders.termQuery("topicId", topicId));
            }
            // 时间范围过滤（epoch_millis）
            if ((startTime != null && startTime > 0) || (endTime != null && endTime > 0)) {
                var range = QueryBuilders.rangeQuery("createdAt");
                if (startTime != null && startTime > 0) range.gte(startTime);
                if (endTime != null && endTime > 0) range.lte(endTime);
                bool.filter(range);
            }
            // 主 should：原始关键词
            bool.should(main);
            // 同义词 should：扩大召回
            for (String syn : synonyms) {
                MultiMatchQueryBuilder synQ = QueryBuilders.multiMatchQuery(syn);
                for (String f : fields) {
                    String name = f.contains("^") ? f.substring(0, f.indexOf('^')) : f;
                    float boost = 0.7f; // 同义词权重略低于主查询
                    synQ.field(name, boost);
                }
                if ("user".equals(t) || "topic".equals(t)) {
                    synQ.operator(Operator.AND);
                } else {
                    synQ.minimumShouldMatch("60%");
                }
                if (syn.length() >= 2 && syn.length() <= 20) {
                    synQ.fuzziness(Fuzziness.AUTO).prefixLength(1);
                }
                bool.should(synQ);
            }
            // 至少命中一个 should（原始或同义词）
            bool.minimumShouldMatch(1);

            source.query(bool);

            // sort options: hot (default), latest, relevance
            String s = StrUtil.isBlank(sort) ? "hot" : sort;
            switch (s) {
                case "latest":
                    source.sort("createdAt", SortOrder.DESC);
                    source.sort("viewCount", SortOrder.DESC);
                    break;
                case "relevance":
                    // rely on _score, optionally add tiebreaker
                    source.sort("_score", SortOrder.DESC);
                    source.sort("createdAt", SortOrder.DESC);
                    break;
                case "hot":
                default:
                    source.sort("viewCount", SortOrder.DESC);
                    source.sort("createdAt", SortOrder.DESC);
                    break;
            }

            // pagination
            int from = (page - 1) * size;
            source.from(from).size(size);

            // highlight 仅针对本次查询涉及的字段
            HighlightBuilder highlight = new HighlightBuilder()
                    .preTags("<em>")
                    .postTags("</em>")
                    .requireFieldMatch(false); // 允许不同字段高亮
            for (String f : fields) {
                String name = f.contains("^") ? f.substring(0, f.indexOf('^')) : f;
                highlight.field(new HighlightBuilder.Field(name).highlighterType("unified"));
            }
            source.highlighter(highlight);

            request.source(source);
            SearchResponse resp = esClient.search(request, org.elasticsearch.client.RequestOptions.DEFAULT);

            // collect post ids for batch attachment query
            List<PostListItemVO> items = new ArrayList<>();
            List<Long> postIds = new ArrayList<>();
            for (SearchHit hit : resp.getHits().getHits()) {
                Map<String, Object> s1 = hit.getSourceAsMap();

                Long id = getLong(s1.get("id"));
                if (id != null) {
                    postIds.add(id);
                }
                Long userId = getLong(s1.get("userId"));
                Long hitTopicId = getLong(s1.get("topicId"));
                String title = getString(s1.get("title"));
                String content = getString(s1.get("content"));
                String authorUsername = getString(s1.get("authorUsername"));
                String authorAvatar = getString(s1.get("authorAvatar"));
                String topicName = getString(s1.get("topicName"));
                Integer likeCount = getInt(s1.get("likeCount"));
                Integer commentCount = getInt(s1.get("commentCount"));
                Integer shareCount = getInt(s1.get("shareCount"));
                Integer viewCount = getInt(s1.get("viewCount"));
                LocalDateTime createdAt = getDateTime(s1.get("createdAt"));
                LocalDateTime updatedAt = getDateTime(s1.get("updatedAt"));

                // apply highlights
                if (hit.getHighlightFields().get("title") != null) {
                    title = String.join("", toStrings(hit.getHighlightFields().get("title").fragments()));
                }
                if (hit.getHighlightFields().get("content") != null) {
                    content = String.join("", toStrings(hit.getHighlightFields().get("content").fragments()));
                }
                if (hit.getHighlightFields().get("authorUsername") != null) {
                    authorUsername = String.join("",
                            toStrings(hit.getHighlightFields().get("authorUsername").fragments()));
                }
                if (hit.getHighlightFields().get("topicName") != null) {
                    topicName = String.join("", toStrings(hit.getHighlightFields().get("topicName").fragments()));
                }

                // attachments will be filled after batch query
                List<AttachmentLite> liteAttachments = new ArrayList<>();

                PostListItemVO vo = PostListItemVO.builder()
                        .id(id)
                        .title(title)
                        .content(content)
                        .userId(userId)
                        .authorUsername(authorUsername)
                        .authorAvatar(authorAvatar)
                        .topicId(hitTopicId)
                        .topicName(topicName)
                        .likeCount(likeCount)
                        .commentCount(commentCount)
                        .shareCount(shareCount)
                        .viewCount(viewCount)
                        .createdAt(createdAt)
                        .updatedAt(updatedAt)
                        .attachments(liteAttachments)
                        .build();
                items.add(vo);
            }

            // batch query attachments by post ids and fill into items
            if (!postIds.isEmpty()) {
                List<Attachment> all = attachmentMapper.selectByBusinessIds("post", postIds);
                java.util.Map<Long, List<Attachment>> grouped = all.stream()
                        .collect(java.util.stream.Collectors.groupingBy(Attachment::getBusinessId));
                for (PostListItemVO vo : items) {
                    Long pid = vo.getId();
                    List<Attachment> attachments = grouped.get(pid);
                    if (attachments != null && !attachments.isEmpty()) {
                        List<AttachmentLite> lite = new ArrayList<>();
                        for (Attachment a : attachments) {
                            lite.add(AttachmentLite.builder()
                                    .fileUrl(a.getFileUrl())
                                    .fileType(a.getFileType())
                                    .fileName(a.getFileName())
                                    .build());
                        }
                        vo.setAttachments(lite);
                    }
                }
            }

            SearchPageResult result = SearchPageResult.builder()
                    .posts(items)
                    .total(resp.getHits().getTotalHits().value)
                    .currentPage(page)
                    .build();
            return Result.success(result);
        } catch (IOException e) {
            log.error("ES 搜索失败", e);
            return Result.error("搜索失败，请稍后重试");
        }
    }

    // 简易同义词扩展，可替换为读取配置或索引级 synonym 分析器
    private static List<String> expandSynonyms(String keyword) {
        List<String> list = new ArrayList<>();
        String k = keyword.trim().toLowerCase();
        switch (k) {
            case "微服务":
                list.add("微服务架构");
                list.add("msa");
                list.add("microservice");
                break;
            case "分布式":
                list.add("分布式系统");
                list.add("distributed");
                break;
            case "缓存":
                list.add("cache");
                list.add("redis");
                break;
            case "搜索":
                list.add("检索");
                list.add("全文检索");
                list.add("es");
                list.add("elasticsearch");
                break;
            default:
                break;
        }
        return list;
    }

    private static String[] toStrings(org.elasticsearch.common.text.Text[] texts) {
        String[] arr = new String[texts.length];
        for (int i = 0; i < texts.length; i++)
            arr[i] = texts[i].string();
        return arr;
    }

    private static String getString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long getLong(Object o) {
        if (o == null)
            return null;
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer getInt(Object o) {
        if (o == null)
            return null;
        try {
            return Integer.valueOf(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime getDateTime(Object o) {
        if (o == null)
            return null;
        try {
            long epoch = Long.parseLong(String.valueOf(o));
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epoch), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }
}

/*
 * 
 * 
 * 需要配置的字段
 * 
 * - title ：建议 search_as_you_type + 中文分词（支持前缀匹配）
 * - content ： text + 中文分词
 * - authorUsername ： search_as_you_type + 中文分词
 * - topicName ： search_as_you_type + 中文分词
 * 推荐映射（IK 版本）
 * 
 * - 前提：Elasticsearch 已安装 IK 分词插件（ ik_smart / ik_max_word ）
 * - 索引 posts 的 settings/mappings 示例：
 * ```
 * PUT posts
 * {
 *   "settings": {
 *     "analysis": {
 *       "filter": {
 *         "synonym_filter": {
 *           "type": "synonym_graph",
 *           "synonyms": [
 *             "微服务,微服务架构,msa,
 *             microservice",
 *             "分布式,分布式系统,
 *             distributed",
 *             "缓存,cache,redis",
 *             "搜索,检索,全文检索,es,
 *             elasticsearch"
 *           ]
 *         }
 *       },
 *       "analyzer": {
 *         "ik_synonym": {
 *           "type": "custom",
 *           "tokenizer": "ik_smart",
 *           "filter": ["lowercase", 
 *           "synonym_filter"]
 *         }
 *       }
 *     }
 *   },
 *   "mappings": {
 *     "properties": {
 *       "id": { "type": "long" },
 *       "userId": { "type": "long" },
 *       "topicId": { "type": "long" },
 *       "title": {
 *         "type": "search_as_you_type",
 *         "analyzer": "ik_synonym",
 *         "search_analyzer": 
 *         "ik_synonym"
 *       },
 *       "content": {
 *         "type": "text",
 *         "analyzer": "ik_synonym",
 *         "search_analyzer": 
 *         "ik_synonym"
 *       },
 *       "authorUsername": {
 *         "type": "search_as_you_type",
 *         "analyzer": "ik_synonym",
 *         "search_analyzer": 
 *         "ik_synonym"
 *       },
 *       "topicName": {
 *         "type": "search_as_you_type",
 *         "analyzer": "ik_synonym",
 *         "search_analyzer": 
 *         "ik_synonym"
 *       },
 *       "likeCount": { "type": 
 *       "integer" },
 *       "commentCount": { "type": 
 *       "integer" },
 *       "shareCount": { "type": 
 *       "integer" },
 *       "viewCount": { "type": 
 *       "integer" },
 *       "createdAt": { "type": "date", 
 *       "format": "epoch_millis" },
 *       "updatedAt": { "type": "date", 
 *       "format": "epoch_millis" },
 *       "status": { "type": "keyword" }
 *     }
 *   }
 * }
 * ```
 * - 作用：
 * - 中文分词：使用 IK，对中文进行合理切分。
 * - 同义词：通过 ik_synonym 将常见同义词在索引层扩展，与你代码里的查询同义词形成互补。
 * - 前缀匹配： search_as_you_type 自动生成 _2gram/_3gram 子字段，匹配短前缀。你的代码已查询这些子字段。
 * 
 * 
 * 
 */
