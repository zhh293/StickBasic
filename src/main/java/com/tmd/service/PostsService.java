package com.tmd.service;

import com.tmd.entity.dto.CommentCreateDTO;
import com.tmd.entity.dto.PostCreateDTO;
import com.tmd.entity.dto.Result;

public interface PostsService {
    Result createPost(Long userId, PostCreateDTO dto);
    Result getPosts(Integer page,
                    Integer size,
                    String type,
                    String status,
                    String sort) throws InterruptedException;

    Result getPostsScroll(Integer size,
                          String type,
                          String status,
                          String sort,
                          Long max,
                          Integer offset) throws InterruptedException;

    Result deletePost(Long userId, Long postId);

    Result createShareLink(Long userId, Long postId, String channel);

    Result openShareLink(String token);

    Result recordView(Long postId);

    Result toggleLike(Long postId);

    Result getUserLikes(Integer page, Integer size, String targetType);

    Result toggleFavorite(Long postId);

    Result getUserFavorites(Integer page, Integer size);

    Result createComment(Long userId, Long postId, CommentCreateDTO dto);
    Result createReplyComment(Long userId, Long postId, Long commentId, CommentCreateDTO dto);
    Result deleteComment(Long userId, Long commentId);
}
