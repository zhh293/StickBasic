package com.tmd.controller;


import com.tmd.entity.dto.PostCreateDTO;
import com.tmd.entity.dto.Result;
import com.tmd.entity.dto.PageResult;
import com.tmd.entity.dto.PostListItemVO;

import com.tmd.tools.BaseContext;
import com.tmd.service.PostsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpHeaders;
import static com.tmd.constants.common.ERROR_CODE;

@RestController
@RequestMapping("/posts")
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
                           @RequestParam(required = false, defaultValue = "normal") String status,
                           @RequestParam(required = false, defaultValue = "latest") String sort) throws InterruptedException {
        log.info("用户正在获取帖子列表: page={}, size={}, type={}, status={}, sort={}",
                page, size, type, status, sort);
        return postsService.getPosts(page, size, type, status, sort);
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
                                 @RequestParam(required = false, defaultValue = "normal") String status,
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
                                                             @RequestParam String channel) {
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
}
