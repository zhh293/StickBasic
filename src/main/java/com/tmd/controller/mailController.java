package com.tmd.controller;


import com.tmd.entity.dto.MailDTO;
import com.tmd.entity.dto.MailPackage;
import com.tmd.entity.dto.Result;
import com.tmd.service.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController

@RequestMapping("/mails")
public class mailController {

    @Autowired
    private MailService mailService;
    @GetMapping("/{mailId}")
    public Result getMailById(@PathVariable Integer mailId) {
        return Result.success(mailService.getMailById(mailId));
    }


    //使用滚动分页查询
    @GetMapping
    public Result getAllMails(@RequestParam(defaultValue = "0") Integer scroll, @RequestParam(defaultValue = "10") Integer size,@RequestParam(defaultValue = "0") Long max

    ,@RequestParam(defaultValue = "all") String status) {
        if(max == 0){
            max = System.currentTimeMillis();
        }
        MailPackage mailPackage = MailPackage.builder()
                .scroll(scroll)
                .size(size)
                .max(max)
                .build();
        return Result.success(mailService.getAllMails(mailPackage));
    }



    @GetMapping("/self")
    public Result getSelfMails(@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "10") Integer size, @RequestParam(defaultValue = "sent") String status){
        return Result.success(mailService.getSelfMails(page,size,status));
    }

    @PostMapping
    public Result sendMail(@RequestBody MailDTO mailDTO){
        mailService.sendMail(mailDTO);
        return Result.success("发送成功哦");
    }

    //原邮件会单独放在一个区域里面，这个区域里面isFirst一定是true的。
    //然后还会有一个自己评论的邮件区域，能看到最原始的邮件的信息和自己评论的内容，这里面是不能评论的
    //然后还会有一个收件箱，能看到别人评论的东西和原始邮件的内容，这里面可以评论，并且isFirst一定是false的。
    //到时候应该是点开原始邮件之后，下面显示的是按时间排序后的双方的评论与回复等等，这个界面大概要调三个接口
    @PostMapping("/{mailId}/comments")
    public Result comment(@PathVariable Long mailId,@RequestBody MailDTO mailDTO,@RequestParam(defaultValue = "true") Boolean isFirst){
        return mailService.comment(mailId,mailDTO,isFirst);
    }

    @GetMapping("/received")
    public Result getReceivedMails(@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "10") Integer size, @RequestParam(defaultValue = "sent") String status){
        return Result.success(mailService.getReceivedMails(page,size,status));
    }

    @GetMapping("/comments/self")
    public Result getSelfCommentMails(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer size){
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 10 : Math.min(Math.max(size, 1), 50);
        return Result.success(mailService.getSelfCommentMails(p, s));
    }

    @GetMapping("/{mailId}/agent/insight")
    public Result agentInsight(@PathVariable Long mailId){
        return mailService.agentInsight(mailId);
    }

    @PostMapping("/{mailId}/agent/suggest")
    public Result agentSuggest(@PathVariable Long mailId,
                               @RequestParam(required = false) Integer count,
                               @RequestParam(required = false) String style){
        return mailService.agentSuggest(mailId, count, style);
    }
}
