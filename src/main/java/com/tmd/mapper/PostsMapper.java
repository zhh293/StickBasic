package com.tmd.mapper;

import com.tmd.entity.dto.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PostsMapper {
    int insert(Post post);

    Post selectById(@Param("id") Long id);

    int deleteById(@Param("id") Long id);

    List<Post> selectPage(@Param("type") String type,
            @Param("status") String status,
            @Param("sort") String sort,
            @Param("offset") Integer offset,
            @Param("size") Integer size);

    long count(@Param("type") String type,
            @Param("status") String status);

    List<Post> selectPageByTopic(@Param("topicId") Long topicId,
            @Param("status") String status,
            @Param("sort") String sort,
            @Param("offset") Integer offset,
            @Param("size") Integer size);

    long countByTopic(@Param("topicId") Long topicId,
            @Param("status") String status);

    List<Post> selectLatestIds(@Param("type") String type,
            @Param("status") String status,
            @Param("limit") Integer limit);

    List<Post> selectByIds(@Param("ids") List<Long> ids);

    int incrShareCount(@Param("id") Long id, @Param("delta") Integer delta);

    @Update("update posts set view_count = IFNULL(view_count,0) + #{delta} where id = #{id}")
    int incrViewCount(@Param("id") Long id, @Param("delta") Integer delta);

    @Update("update posts set like_count = IFNULL(like_count,0) + #{delta} where id = #{id}")
    int incrLikeCount(@Param("id") Long id, @Param("delta") Integer delta);

    @Update("<script>" +
            "UPDATE posts SET like_count = CASE id " +
            "<foreach collection='list' item='item'>" +
            "WHEN #{item.id} THEN IFNULL(like_count, 0) + #{item.delta} " +
            "</foreach>" +
            "END " +
            "WHERE id IN " +
            "<foreach collection='list' item='item' open='(' separator=',' close=')'>" +
            "#{item.id}" +
            "</foreach>" +
            "</script>")
    int updateLikeCountBatch(@Param("list") List<java.util.Map<String, Object>> list);

    @Update("update posts set comment_count = IFNULL(comment_count,0) + #{delta} where id = #{id}")
    int incrCommentCount(@Param("id") Long id, @Param("delta") Integer delta);
}
