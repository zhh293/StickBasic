package com.tmd.mapper;

import com.tmd.entity.po.Follow;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FollowMapper {
    
    @Insert("INSERT INTO follow (follower_id, following_id, created_at) VALUES (#{followerId}, #{followingId}, NOW())")
    int insert(Follow follow);
    
    @Delete("DELETE FROM follow WHERE follower_id = #{followerId} AND following_id = #{followingId}")
    int delete(@Param("followerId") Long followerId, @Param("followingId") Long followingId);
    
    @Select("SELECT COUNT(*) FROM follow WHERE follower_id = #{followerId} AND following_id = #{followingId}")
    int exists(@Param("followerId") Long followerId, @Param("followingId") Long followingId);
    
    @Select("SELECT following_id FROM follow WHERE follower_id = #{followerId}")
    List<Long> findFollowingIds(@Param("followerId") Long followerId);
    
    @Select("SELECT follower_id FROM follow WHERE following_id = #{followingId}")
    List<Long> findFollowerIds(@Param("followingId") Long followingId);
    
    @Select("SELECT COUNT(*) FROM follow WHERE follower_id = #{followerId}")
    int countFollowing(@Param("followerId") Long followerId);
    
    @Select("SELECT COUNT(*) FROM follow WHERE following_id = #{followingId}")
    int countFollowers(@Param("followingId") Long followingId);
}