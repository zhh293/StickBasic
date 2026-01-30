package com.tmd.controller;


import com.tmd.entity.dto.CommentCreateDTO;
import com.tmd.entity.dto.PostCreateDTO;
import com.tmd.entity.dto.PostUpdateDTO;
import com.tmd.entity.dto.Result;
import com.tmd.service.PostsService;
import com.tmd.tools.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import static com.tmd.constants.common.ERROR_CODE;

@RestController
@RequestMapping("/api/posts")
@Slf4j
public class PostsController {
    // 帖子列表需配合Redis、Redisson与ES进行高性能查询与索引恢复

    @Autowired
    private PostsService postsService;

    //初期我们先用这个接口，后续再根据需求调整
    @GetMapping
    public Result getPosts(@RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "10") Integer size,
                           @RequestParam(required = false) String type,
                           @RequestParam(required = false, defaultValue = "published") String status,
                           @RequestParam(required = false, defaultValue = "latest") String sort) throws InterruptedException {
        log.info("用户正在获取帖子列表: page={}, size={}, type={}, status={}, sort={}",
                page, size, type, status, sort);
        return postsService.getPosts(page, size, type, status, sort);
    }

    @GetMapping("/{userId}")
    public Result getPostsByUser(@PathVariable Long userId,
                                 @RequestParam(defaultValue = "1") Integer page,
                                 @RequestParam(defaultValue = "10") Integer size,
                                 @RequestParam(required = false) String type,
                                 @RequestParam(required = false, defaultValue = "published") String status,
                                 @RequestParam(required = false, defaultValue = "latest") String sort) throws InterruptedException {
        log.info("用户 {} 正在获取用户 {} 的帖子列表: page={}, size={}, type={}, status={}, sort={}",
                userId, userId, page,
                size, type, status, sort);
        return postsService.getPostsByUser(userId, page, size, type, status, sort);
    }

    @PostMapping
    public Result createPost(@RequestBody PostCreateDTO dto) {
        Long userId = BaseContext.get();
        if (userId == null || userId == ERROR_CODE) {
            return Result.error("验证失败,非法访问");
        }
        log.info("用户 {} 正在创建帖子", userId);
        return postsService.createPost(userId, dto);
    }

    @GetMapping("/scroll")
    public Result getPostsScroll(@RequestParam(defaultValue = "10") Integer size,
                                 @RequestParam(required = false) String type,
                                 @RequestParam(required = false, defaultValue = "published") String status,
                                 @RequestParam(required = false, defaultValue = "latest") String sort,
                                 @RequestParam(required = false) Long max,
                                 @RequestParam(defaultValue = "0") Integer offset) throws InterruptedException {
        log.info("用户正在滚动获取帖子列表: size={}, type={}, status={}, sort={}, max={}, offset={}",
                size, type, status, sort, max, offset);
        return postsService.getPostsScroll(size, type, status, sort, max, offset);
    }

    @DeleteMapping("/{postId}")
    public Result deletePost(@PathVariable Long postId) {
        Long userId = BaseContext.get();
        if (userId == null || userId == ERROR_CODE) {
            return Result.error("验证失败,非法访问");
        }
        log.info("用户 {} 正在删除帖子 {}", userId, postId);
        return postsService.deletePost(userId, postId);
    }

    @PostMapping("/{postId}/share")
    public Result createShare(@PathVariable Long postId,
                                                             @RequestParam(required = false) String channel) {
        Long userId = BaseContext.get();
        if (userId == null || userId == ERROR_CODE) {
            return Result.error("验证失败,非法访问");
        }
        return postsService.createShareLink(userId, postId, channel);
    }

    @PostMapping("/{postId}/view")
    public Result recordView(@PathVariable Long postId,
                             @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Long userId = BaseContext.get();
        if (postId == null || postId <= 0) {
            return Result.error("参数错误");
        }
        return postsService.recordView(postId);
    }

    @PostMapping("/{postId}/like")
    public Result toggleLike(@PathVariable Long postId,
                             @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Long userId = BaseContext.get();
        if (postId == null || postId <= 0) {
            return Result.error("参数错误");
        }
        return postsService.toggleLike(postId);
    }
    @PutMapping
    public Result updatePosts(@RequestBody PostUpdateDTO postUpdateDTO){
        Long userId = BaseContext.get();
        if (userId == null || userId == ERROR_CODE) {
            return Result.error("验证失败,非法访问");
        }
        return postsService.updatePosts(postUpdateDTO);
    }

    @GetMapping("/{postId}/comments")
    public Result getPostComments(@PathVariable Long postId,
                                  @RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "10") Integer size,
                                  @RequestParam(defaultValue = "latest") String sortBy) {
        log.info("Fetching comments for post {}: page={}, size={}, sortBy={}", postId, page, size, sortBy);
        return postsService.getPostComments(postId, page, size, sortBy);
    }

    @GetMapping("/likes")
    public Result getUserLikes(@RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "20") Integer size,
                               @RequestParam(defaultValue = "post") String targetType) {
        Long userId = BaseContext.get();
        if (userId == null || userId <= 0) {
            return Result.error("未登录");
        }
        return postsService.getUserLikes(page, size, targetType);
    }

    @PostMapping("/{postId}/favorite")
    public Result toggleFavorite(@PathVariable Long postId,
                                 @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Long userId = BaseContext.get();
        if (postId == null || postId <= 0) {
            return Result.error("参数错误");
        }
        return postsService.toggleFavorite(postId);
    }

    @GetMapping("/favorites")
    public Result getUserFavorites(@RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer size) {
        Long userId = BaseContext.get();
        if (userId == null || userId <= 0) {
            return Result.error("未登录");
        }
        return postsService.getUserFavorites(page, size);
    }

    @GetMapping("/share/{token}")
    public Result openShare(@PathVariable String token) {
        return postsService.openShareLink(token);
    }
    //帖子评论（根评论）
    @PostMapping("/{postId}/comments")
    public Result createComment(@PathVariable Long postId,
                                @RequestBody CommentCreateDTO dto) {
        Long userId = BaseContext.get();
        if (userId == null || userId == ERROR_CODE) {
            return Result.error("验证失败,非法访问");
        }
        log.info("用户 {} 正在创建帖子 {} 的评论", userId, postId);
        return postsService.createComment(userId, postId, dto);
    }
    //帖子评论（子评论）
    @PostMapping("/{postId}/comment/{commentId}")
    public Result createReplyComment(@PathVariable Long postId,
                                     @PathVariable Long commentId,
                                     @RequestBody CommentCreateDTO dto) {
        Long userId = BaseContext.get();
        if (userId == null || userId == ERROR_CODE) {
            return Result.error("验证失败,非法访问");
        }
        log.info("用户 {} 正在回复帖子 {} 的评论 {}", userId, postId, commentId);
        return postsService.createReplyComment(userId, postId, commentId, dto);
    }

    @DeleteMapping("/comment/{commentId}")
    public Result deleteComment(@PathVariable Long commentId) {
        Long userId = BaseContext.get();
        if (userId == null || userId == ERROR_CODE) {
            return Result.error("验证失败,非法访问");
        }
        log.info("用户 {} 正在删除评论 {}", userId, commentId);
        return postsService.deleteComment(userId, commentId);
    }
//    @GetMapping("/{postId}")
//    public Result getPost(@PathVariable Long postId) {
//        log.info("Fetching post {}", postId);
//        return postsService.getPost(postId);
//    }

    //这个里面也要考虑使用缓存了，看看怎么样能实现一个稳定高效的评论系统。。
}
