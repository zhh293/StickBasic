package com.tmd.mapper;

import com.github.pagehelper.Page;
import com.tmd.entity.dto.TopicFollow;
import com.tmd.entity.dto.TopicFollowVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TopicFollowMapper {
    @Delete("delete from topic_follow where topic_id = #{topicId}")
    void deleteByTopicId(Integer topicId);

    @Insert("insert into topic_follow (topic_id,user_id,created_at) values(#{topicId},#{userId},#{createdAt})")
    void insert(TopicFollow build);

    @Select("select c.created_at,c.user_id,u.username,u.avatar from sticknew.topic_follow c left join sticknew.user u on u.id = c.user_id " +
            "where c.topic_id =#{topicId}")
    Page<TopicFollowVO> getTopicFollowers(Integer topicId);
}
