package com.tmd.service;

import java.util.List;

public interface FollowService {

    /**
     * 关注用户
     * 
     * @param followerId  关注者ID
     * @param followingId 被关注者ID
     * @return 是否成功
     */
    boolean followUser(Long followerId, Long followingId);

    /**
     * 取消关注用户
     * 
     * @param followerId  关注者ID
     * @param followingId 被关注者ID
     * @return 是否成功
     */
    boolean unfollowUser(Long followerId, Long followingId);

    /**
     * 检查是否已关注
     * 
     * @param followerId  关注者ID
     * @param followingId 被关注者ID
     * @return 是否已关注
     */
    boolean isFollowing(Long followerId, Long followingId);

    /**
     * 获取用户的关注列表（从Redis缓存）
     * 
     * @param userId 用户ID
     * @return 关注的用户ID列表
     */
    List<Long> getFollowingIds(Long userId);

    /**
     * 获取用户的粉丝列表
     * 
     * @param userId 用户ID
     * @return 粉丝的用户ID列表
     */
    List<Long> getFollowerIds(Long userId);

    /**
     * 获取关注数量
     * 
     * @param userId 用户ID
     * @return 关注数量
     */
    int getFollowingCount(Long userId);

    /**
     * 获取粉丝数量
     * 
     * @param userId 用户ID
     * @return 粉丝数量
     */
    int getFollowerCount(Long userId);
}