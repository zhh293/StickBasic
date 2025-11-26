package com.tmd.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface FavoriteMapper {
    @Insert("insert into favorite(user_id, post_id, created_at) values(#{userId}, #{postId}, now())")
    int insert(@Param("userId") Long userId, @Param("postId") Long postId);

    @Delete("delete from favorite where user_id = #{userId} and post_id = #{postId}")
    int delete(@Param("userId") Long userId, @Param("postId") Long postId);

    @Select("select count(1) from favorite where user_id = #{userId} and post_id = #{postId}")
    int exists(@Param("userId") Long userId, @Param("postId") Long postId);

    @Select("select id, user_id as userId, post_id as postId, created_at as createdAt from favorite where user_id = #{userId} order by created_at desc limit #{offset}, #{size}")
    List<Map<String, Object>> selectByUser(@Param("userId") Long userId, @Param("offset") Integer offset, @Param("size") Integer size);
}
