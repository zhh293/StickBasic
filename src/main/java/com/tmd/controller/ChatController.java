package com.tmd.controller;

import com.tmd.entity.dto.Result;
import com.tmd.service.PrivateChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
@Slf4j
@RequiredArgsConstructor
public class ChatController {

    private final PrivateChatService privateChatService;

    @PostMapping("/send")
    public Result send(@RequestParam Long toUserId,
                       @RequestParam String content) {
        log.info("发送私聊消息 toUserId={} contentLen={}", toUserId, content == null ? 0 : content.length());
        return privateChatService.send(toUserId, content);
    }

    @GetMapping("/offline")
    public Result pullOffline(@RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "10") Integer size) {
        log.info("拉取离线消息 page={} size={}", page, size);
        return privateChatService.pullOffline(page, size);
    }

    @GetMapping("/history/{otherUserId}")
    public Result history(@PathVariable Long otherUserId,
                          @RequestParam(defaultValue = "10") Integer size,
                          @RequestParam(required = false) Long max,
                          @RequestParam(defaultValue = "0") Integer offset) {
        log.info("查询会话历史 otherUserId={} size={} max={} offset={}", otherUserId, size, max, offset);
        return privateChatService.history(otherUserId, size, max, offset);
    }

    @PostMapping("/read/{messageId}")
    public Result markRead(@PathVariable Long messageId) {
        log.info("标记已读 messageId={}", messageId);
        return privateChatService.markRead(messageId);
    }

    @PostMapping("/recall/{messageId}")
    public Result recall(@PathVariable Long messageId) {
        log.info("撤回消息 messageId={}", messageId);
        return privateChatService.recall(messageId);
    }

    @PostMapping("/ack/{messageId}")
    public Result ack(@PathVariable Long messageId) {
        log.info("收到消息ACK messageId={}", messageId);
        return privateChatService.ack(messageId);
    }
}