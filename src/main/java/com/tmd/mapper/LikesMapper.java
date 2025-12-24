package com.tmd.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface LikesMapper {
    @Insert("insert into likes(user_id, target_type, target_id, created_at) values(#{userId}, #{targetType}, #{targetId}, now())")
    int insert(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("targetId") Long targetId);

    @Delete("delete from likes where user_id = #{userId} and target_type = #{targetType} and target_id = #{targetId}")
    int delete(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("targetId") Long targetId);

    @Select("select count(1) from likes where user_id = #{userId} and target_type = #{targetType} and target_id = #{targetId}")
    int exists(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("targetId") Long targetId);

    @Select("select id as likeId, user_id as userId, target_type as targetType, target_id as targetId, created_at as createdAt from likes where user_id = #{userId} and target_type = #{targetType} order by created_at desc limit #{offset}, #{size}")
    List<Map<String, Object>> selectByUser(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("offset") Integer offset, @Param("size") Integer size);
}
