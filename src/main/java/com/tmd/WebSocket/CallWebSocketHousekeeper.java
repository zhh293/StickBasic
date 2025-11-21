package com.tmd.WebSocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CallWebSocketHousekeeper {

    @Scheduled(fixedDelayString = "${call.connection.cleanup-interval-ms:30000}")
    public void cleanup() {
        CallWebSocketServer.cleanupStaleConnections();
        log.debug("执行通话WebSocket心跳清理");
    }
}
