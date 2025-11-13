package com.tmd.service;

import com.tmd.entity.dto.Result;
import com.tmd.entity.dto.TopicDTO;
import com.tmd.entity.dto.TopicVO;
import org.springframework.stereotype.Service;

@Service
public interface TopicService {
    Result getAllTopics(Integer page, Integer size, String status) throws InterruptedException;

    Result getTopicById(Integer topicId);

    /**
     * 通过话题ID获取话题，带Redis缓存（返回TopicVO，便于内部服务复用）
     */
    TopicVO getTopicCachedById(Integer topicId);

    Result createTopic(TopicDTO topic);

    Result followTopic(Integer topicId);

    Result getTopicFollowers(Integer topicId, Integer page, Integer size);

    Result getTopicPosts(Integer topicId,
                         Integer page,
                         Integer size,
                         String sort,
                         String status) throws InterruptedException;

    void incrementTopicPostCount(Long topicId, Integer delta);
}
