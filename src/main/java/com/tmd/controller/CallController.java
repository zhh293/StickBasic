package com.tmd.controller;

import com.tmd.entity.dto.Result;
import com.tmd.entity.dto.call.CallEndReason;
import com.tmd.service.CallSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/call")
@RequiredArgsConstructor
@Slf4j
public class CallController {

    private final CallSessionService callSessionService;

    @GetMapping("/active/{userId}")
    public Result activeSessions(@PathVariable Long userId) {
        log.info("查询用户 {} 的实时通话会话", userId);
        return Result.success(callSessionService.listSessionsByUser(userId));
    }

    @PostMapping("/terminate/{callId}")
    public Result terminate(@PathVariable String callId,
            @RequestParam(defaultValue = "NORMAL") String reason) {
        CallEndReason endReason;
        try {
            endReason = CallEndReason.valueOf(reason.toUpperCase());
        } catch (IllegalArgumentException ex) {
            endReason = CallEndReason.UNKNOWN;
        }
        log.info("管理员终止通话 callId={} reason={}", callId, endReason);
        return callSessionService.endSession(callId, endReason)
                .map(Result::success)
                .orElseGet(() -> Result.error("通话不存在"));
    }

    @GetMapping("/metrics")
    public Result metrics() {
        Map<String, Object> data = new HashMap<>();
        data.put("activeSessions", callSessionService.findActiveSessions().size());
        return Result.success(data);
    }
}
