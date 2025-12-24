
package com.tmd.WebSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.tmd.entity.dto.call.*;
import com.tmd.service.CallSessionService;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ServerEndpoint("/ws/call/{userId}")
@Slf4j
public class CallWebSocketServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<Long, ClientConnection> CONNECTIONS = new ConcurrentHashMap<>();
    private static final long HEARTBEAT_TIMEOUT_MS = 30_000;

    private static final Map<Long, RateLimiter> RATE_LIMITERS = new ConcurrentHashMap<>();
    private static CallSessionService callSessionService;

    @Autowired
    public void setCallSessionService(CallSessionService callSessionService) {
        CallWebSocketServer.callSessionService = callSessionService;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId) {
        if (CONNECTIONS.containsKey(userId)) {
            // 关闭旧连接，保留新连接
            ClientConnection oldConn = CONNECTIONS.get(userId);
            try {
                oldConn.session().close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Duplicate connection"));
            } catch (Exception e) {
                log.warn("关闭重复连接失败 userId={}", userId, e);
            }
        }
        CONNECTIONS.put(userId, new ClientConnection(session));
        log.info("呼叫WebSocket建立 userId={} session={}", userId, session.getId());
        send(session, heartbeatAck());
    }

    @OnMessage
    public void onMessage(String message, @PathParam("userId") Long userId) {
        // 获取或创建限流器（10 QPS）
        RateLimiter limiter = RATE_LIMITERS.computeIfAbsent(userId, k -> RateLimiter.create(10));
        if (!limiter.tryAcquire()) {
            log.warn("用户信令频率超限 userId={}", userId);
            send(userId, buildError(null, "RATE_LIMIT_EXCEEDED"));
            return;
        }
        try {
            CallSignalMessage signal = OBJECT_MAPPER.readValue(message, CallSignalMessage.class);
            log.debug("收到信令 userId={} action={} callId={}", userId, signal.getAction(), signal.getCallId());
            route(userId, signal);
        } catch (Exception ex) {
            log.error("解析信令失败 userId={} raw={}", userId, message, ex);
            send(userId, buildError(null, "INVALID_PAYLOAD"));
        }
    }

    @OnClose
    public void onClose(@PathParam("userId") Long userId) {
        CONNECTIONS.remove(userId);
        RATE_LIMITERS.remove(userId); // 新增：清理用户限流器
        log.info("呼叫WebSocket关闭 userId={}", userId);
        log.info("呼叫WebSocket关闭 userId={}", userId);
    }

    @OnError
    public void onError(Session session, Throwable error, @PathParam("userId") Long userId) {
        log.error("呼叫WebSocket异常 userId={} session={}", userId, session != null ? session.getId() : "N/A", error);
        CONNECTIONS.remove(userId);
    }

    private void route(Long userId, CallSignalMessage message) {
        if (message.getAction() == null) {
            send(userId, buildError(message.getCallId(), "ACTION_REQUIRED"));
            return;
        }
        switch (message.getAction()) {
            case INITIATE -> handleInitiate(userId, message);
            case ANSWER -> handleAnswer(userId, message);
            case REJECT, CANCEL -> handleReject(userId, message);
            case END -> handleEnd(userId, message);
            case ICE, SDP_OFFER, SDP_ANSWER -> forwardSignal(userId, message);
            case HEARTBEAT -> handleHeartbeat(userId, message);
            default -> send(userId, buildError(message.getCallId(), "UNSUPPORTED_ACTION"));
        }
    }

    private void handleInitiate(Long callerId, CallSignalMessage message) {
        Long calleeId = message.getToUserId();
        if (calleeId == null) {
            send(callerId, buildError(null, "CALLEE_REQUIRED"));
            return;
        }
        if (!CONNECTIONS.containsKey(calleeId)) {
            send(callerId, buildError(null, "USER_OFFLINE"));
            return;
        }
        CallType callType = message.getCallType() != null ? message.getCallType() : CallType.VOICE;
        CallSession session = callSessionService.createSession(callerId, calleeId, callType);
        callSessionService.updateStatus(session.getCallId(), CallStatus.RINGING);

        CallSignalMessage ringing = new CallSignalMessage();
        ringing.setAction(CallAction.RINGING);
        ringing.setCallId(session.getCallId());
        ringing.setCallType(callType);
        ringing.setFromUserId(callerId);
        ringing.setToUserId(calleeId);
        ringing.setPayload(message.getPayload());
        ringing.setTimestamp(Instant.now().toEpochMilli());
        send(calleeId, ringing);

        CallSignalMessage ack = new CallSignalMessage();
        ack.setAction(CallAction.INITIATE);
        ack.setCallId(session.getCallId());
        ack.setCallType(callType);
        ack.setToUserId(calleeId);
        ack.setTimestamp(Instant.now().toEpochMilli());
        send(callerId, ack);
    }



//    到这个方法的时候实际上是被叫已经按下了接通键了吧，这时候被叫正在建立WEBRTC,同时主叫在这里处理完anwser相关的逻辑，然后就可以开始交流了
/*    核心目的：让主叫方知道 “可以开始建立 WebRTC 连接了”
    被叫按下接通键后，客户端会先发起 WebRTC 连接的初始化（比如收集本地 ICE 候选、生成 SDP Answer），但同时需要通过服务端告诉主叫：“我已经接了，你赶紧准备连接”—— 这就是服务端转发 ANSWER 信令的意义。
    主叫方收到服务端转发的 ANSWER 信令后，就会触发本地 WebRTC 的后续流程（比如接收被叫的 SDP Answer、交换 ICE 候选），最终建立起端到端的媒体流连接（语音 / 视频数据直接在双方客户端之间传输，不经过服务端）。*/
    private void handleAnswer(Long userId, CallSignalMessage message) {
        Optional<CallSession> optional = callSessionService.getSession(message.getCallId());
        if (optional.isEmpty()) {
            send(userId, buildError(message.getCallId(), "CALL_NOT_FOUND"));
            return;
        }
        CallSession session = optional.get();
        // 新增：仅允许 RINGING 状态的呼叫被接听
        if (session.getStatus() != CallStatus.RINGING) {
            send(userId, buildError(message.getCallId(), "INVALID_CALL_STATUS"));
            return;
        }
        Long target = session.getCallerId().equals(userId) ? session.getCalleeId() : session.getCallerId();
        callSessionService.updateStatus(session.getCallId(), CallStatus.ACTIVE);
        message.setTimestamp(Instant.now().toEpochMilli());
        send(target, message);
    }

    private void handleReject(Long userId, CallSignalMessage message) {
        Optional<CallSession> optional = callSessionService.getSession(message.getCallId());
        if (optional.isEmpty()) {
            send(userId, buildError(message.getCallId(), "CALL_NOT_FOUND"));
            return;
        }
        CallSession session = optional.get();
        Long target = session.getCallerId().equals(userId) ? session.getCalleeId() : session.getCallerId();
        callSessionService.endSession(session.getCallId(), CallEndReason.REJECTED);
        message.setTimestamp(Instant.now().toEpochMilli());
        send(target, message);
    }

    private void handleEnd(Long userId, CallSignalMessage message) {
        Optional<CallSession> optional = callSessionService.endSession(message.getCallId(), CallEndReason.NORMAL);
        if (optional.isEmpty()) {
            send(userId, buildError(message.getCallId(), "CALL_NOT_FOUND"));
            return;
        }
        CallSession session = optional.get();
        Long target = session.getCallerId().equals(userId) ? session.getCalleeId() : session.getCallerId();
        message.setTimestamp(Instant.now().toEpochMilli());
        send(target, message);
    }

    private void forwardSignal(Long userId, CallSignalMessage message) {
        Optional<CallSession> optional = callSessionService.getSession(message.getCallId());
        if (optional.isEmpty()) {
            send(userId, buildError(message.getCallId(), "CALL_NOT_FOUND"));
            return;
        }
        CallSession session = optional.get();
        Long target = session.getCallerId().equals(userId) ? session.getCalleeId() : session.getCallerId();
        message.setTimestamp(Instant.now().toEpochMilli());
        send(target, message);
    }

    private void handleHeartbeat(Long userId, CallSignalMessage message) {
        if (message.getCallId() != null) {
            callSessionService.heartbeat(message.getCallId());
        }
        ClientConnection connection = CONNECTIONS.get(userId);
        if (connection != null) {
            connection.touch();
        }
        send(userId, heartbeatAck());
    }

    private CallSignalMessage buildError(String callId, String reason) {
        CallSignalMessage error = new CallSignalMessage();
        error.setAction(CallAction.ERROR);
        error.setCallId(callId);
        error.setReason(reason);
        error.setTimestamp(Instant.now().toEpochMilli());
        return error;
    }

    private CallSignalMessage heartbeatAck() {
        CallSignalMessage ack = new CallSignalMessage();
        ack.setAction(CallAction.HEARTBEAT);
        ack.setTimestamp(Instant.now().toEpochMilli());
        return ack;
    }

    private void send(Long userId, CallSignalMessage message) {
        ClientConnection connection = CONNECTIONS.get(userId);
        if (connection == null) {
            return;
        }
        send(connection.session(), message);
    }

    private void send(Session session, CallSignalMessage message) {
        try {
            if (session != null && session.isOpen()) {
                session.getAsyncRemote().sendText(OBJECT_MAPPER.writeValueAsString(message));
            }
        } catch (IOException e) {
            log.error("发送信令失败 session={} action={}", session != null ? session.getId() : "N/A", message.getAction(), e);
        }
    }

    static void cleanupStaleConnections() {
        CONNECTIONS.entrySet().removeIf(entry -> {
            ClientConnection connection = entry.getValue();
            if (connection.isExpired()) {
                Session session = connection.session();
                if (session != null && session.isOpen()) {
                    try {
                        session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Heartbeat timeout"));
                    } catch (IOException e) {
                        log.warn("关闭过期连接失败 userId={}", entry.getKey(), e);
                    }
                }
                return true;
            }
            return false;
        });
    }

    private record ClientConnection(Session session, AtomicLong lastHeartbeat) {
        ClientConnection(Session session) {
            this(session, new AtomicLong(System.currentTimeMillis()));
        }

        void touch() {
            lastHeartbeat.set(System.currentTimeMillis());
        }

        boolean isExpired() {
            return System.currentTimeMillis() - lastHeartbeat.get() > HEARTBEAT_TIMEOUT_MS;
        }
    }
}
