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
    public Result comment(@PathVariable Integer mailId,@RequestBody MailDTO mailDTO){
        mailService.comment(mailId,mailDTO);
        return Result.success("评论成功哦");
    }

    @GetMapping("/received")
    public Result getReceivedMails(@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "10") Integer size, @RequestParam String status){
        return Result.success(mailService.getReceivedMails(page,size,status));
    }


}
