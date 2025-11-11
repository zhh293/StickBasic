package com.tmd.service;

import com.tmd.entity.dto.Result;

public interface PostsService {
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
}