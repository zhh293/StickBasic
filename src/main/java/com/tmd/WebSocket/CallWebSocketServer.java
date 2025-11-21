package com.tmd.WebSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmd.entity.dto.call.CallAction;
import com.tmd.entity.dto.call.CallEndReason;
import com.tmd.entity.dto.call.CallSession;
import com.tmd.entity.dto.call.CallSignalMessage;
import com.tmd.entity.dto.call.CallStatus;
import com.tmd.entity.dto.call.CallType;
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

    private static CallSessionService callSessionService;

    @Autowired
    public void setCallSessionService(CallSessionService callSessionService) {
        CallWebSocketServer.callSessionService = callSessionService;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId) {
        CONNECTIONS.put(userId, new ClientConnection(session));
        log.info("呼叫WebSocket建立 userId={} session={}", userId, session.getId());
        send(session, heartbeatAck());
    }

    @OnMessage
    public void onMessage(String message, @PathParam("userId") Long userId) {
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

    private void handleAnswer(Long userId, CallSignalMessage message) {
        Optional<CallSession> optional = callSessionService.getSession(message.getCallId());
        if (optional.isEmpty()) {
            send(userId, buildError(message.getCallId(), "CALL_NOT_FOUND"));
            return;
        }
        CallSession session = optional.get();
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
                session.getBasicRemote().sendText(OBJECT_MAPPER.writeValueAsString(message));
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
