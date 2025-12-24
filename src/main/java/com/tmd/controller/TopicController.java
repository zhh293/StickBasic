package com.tmd.controller;


import com.tmd.entity.dto.Result;
import com.tmd.entity.dto.TopicDTO;
import com.tmd.service.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping ("/topics")
public class TopicController {

    @Autowired
    private TopicService topicService;

    @GetMapping
    public Result getAllTopics(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer size,@RequestParam(defaultValue = "approved") String status) throws InterruptedException {
        return topicService.getAllTopics(page, size,status);
    }

    @GetMapping("/{topicId}")
    public Result getTopicById(@PathVariable Integer topicId){
        // return topicService.getTopicById(topicId);
        return Result.success(topicService.getTopicCachedById(topicId));
    }

    @PostMapping
    public Result createTopic(@RequestBody TopicDTO topic){
        //话题一经上传，无法被用户修改了。所以不需要单独建一个表存文件在oss存储的时候的id了
        return topicService.createTopic(topic);
    }

    @PostMapping("/{topicId}/follow")
    public Result followTopic(@PathVariable Integer topicId){
        return topicService.followTopic(topicId);
    }
    @GetMapping("/{topicId}/followers")
    public Result getTopicFollowers(@PathVariable Integer topicId,@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer size){
        return topicService.getTopicFollowers(topicId,page, size);
    }

    @GetMapping("/{topicId}/posts")
    public Result getTopicPosts(@PathVariable Integer topicId,
                                @RequestParam(defaultValue = "1") Integer page,
                                @RequestParam(defaultValue = "10") Integer size,
                                @RequestParam(required = false, defaultValue = "latest") String sort,
                                @RequestParam(required = false) String status) throws InterruptedException {
        return topicService.getTopicPosts(topicId, page, size, sort, status);
    }
}
