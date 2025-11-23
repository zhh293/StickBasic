package com.tmd.controller;


import com.tmd.entity.dto.MailDTO;
import com.tmd.entity.dto.MailPackage;
import com.tmd.entity.dto.Result;
import com.tmd.entity.dto.mail;
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
    public Result getAllMails(@RequestParam(defaultValue = "0") Integer scroll, @RequestParam(defaultValue = "10") Integer size,@RequestParam Long max

    ,@RequestParam String status) {
        MailPackage mailPackage = MailPackage.builder()
                .scroll(scroll)
                .size(size)
                .max(max)
                .build();
        return Result.success(mailService.getAllMails(mailPackage));
    }



    @GetMapping("/self")
    public Result getSelfMails(@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "10") Integer size, @RequestParam String status){
        return Result.success(mailService.getSelfMails(page,size,status));
    }

    @PostMapping
    public Result sendMail(@RequestBody MailDTO mailDTO){
        mailService.sendMail(mailDTO);
        return Result.success("发送成功哦");
    }

    @PostMapping("/{mailId}/comments")
    public Result comment(@PathVariable Long mailId,@RequestBody MailDTO mailDTO,@RequestParam(defaultValue = "true") Boolean isFirst){
        return mailService.comment(mailId,mailDTO,isFirst);
    }

    @GetMapping("/received")
    public Result getReceivedMails(@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "10") Integer size, @RequestParam String status){
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
