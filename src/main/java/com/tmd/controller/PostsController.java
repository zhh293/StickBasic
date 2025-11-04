package com.tmd.controller;


import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts")
@Slf4j
public class PostsController {
    //帖子由于比较多，所以基本上每一个都需要redis来参与了
    //这个帖子列表我打算用滚动分页查询，因为帖子算是一个更新频率比较高的东西了，不滚动的话可能会出现问题

    @Autowired
    private RestHighLevelClient restHighLevelClient;
    @GetMapping
    public String getPosts(){
        log.info("用户正在获取帖子列表");
        return "帖子列表";
    }
}
