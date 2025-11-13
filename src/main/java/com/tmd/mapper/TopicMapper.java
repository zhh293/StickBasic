package com.tmd.mapper;

import com.tmd.entity.dto.Topic;
import com.tmd.entity.dto.TopicFollowVO;
import com.tmd.entity.dto.TopicVO;

import io.lettuce.core.dynamic.annotation.Param;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TopicMapper {
    @Select("select * from topic")
    List<Topic> getAllTopics();

    @Select("select c.id,c.name,c.description,c.cover_image,c.post_count,c.follower_count,c.status" +
            ",u.username from sticknew.topic c left join sticknew.user u on u.id = c.user_id where c.id=#{topicId}")
    TopicVO getTopicById(Integer topicId);

    void insert(Topic topicEntity);

    @Update("update topic set follower_count = #{followerCount} where id = #{topicId}")
    void updateFollowCount(TopicFollowVO topicFollowVO);

    @Delete("delete from topic where id = #{id}")
    void deleteById(Long id);

    @Update("update topic set post_count = IFNULL(post_count,0) + #{delta} where id = #{id}")
    void incrPostCount(@Param("id") Long id, @Param("delta") Integer delta);
}
