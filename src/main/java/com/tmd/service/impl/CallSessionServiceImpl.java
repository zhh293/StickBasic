package com.tmd.service.impl;

import com.tmd.entity.dto.call.CallEndReason;
import com.tmd.entity.dto.call.CallSession;
import com.tmd.entity.dto.call.CallStatus;
import com.tmd.entity.dto.call.CallType;
import com.tmd.service.CallSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class CallSessionServiceImpl implements CallSessionService {

    private final ConcurrentMap<String, CallSession> sessions = new ConcurrentHashMap<>();

    @Value("${call.session.ttl-seconds:120}")
    private long sessionTtlSeconds;

    @Override
    public CallSession createSession(Long callerId, Long calleeId, CallType callType) {
        String callId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        CallSession session = CallSession.builder()
                .callId(callId)
                .callerId(callerId)
                .calleeId(calleeId)
                .callType(callType)
                .status(CallStatus.CREATED)
                .createdAt(now)
                .updatedAt(now)
                .lastHeartbeatAt(now)
                .build();
        sessions.put(callId, session);
        log.info("创建通话会话 callId={} caller={} callee={} type={}", callId, callerId, calleeId, callType);
        return session;
    }

    @Override
    public Optional<CallSession> updateStatus(String callId, CallStatus status) {
        return Optional.ofNullable(sessions.computeIfPresent(callId, (id, existing) -> {
            existing.setStatus(status);
            existing.setUpdatedAt(Instant.now());
            return existing;
        }));
    }

    @Override
    public Optional<CallSession> getSession(String callId) {
        return Optional.ofNullable(sessions.get(callId));
    }

    @Override
    public Optional<CallSession> endSession(String callId, CallEndReason reason) {
        return Optional.ofNullable(sessions.computeIfPresent(callId, (id, existing) -> {
            existing.setStatus(CallStatus.ENDED);
            existing.setEndReason(reason);
            existing.setUpdatedAt(Instant.now());
            return existing;
        }));
    }

    @Override
    public Optional<CallSession> heartbeat(String callId) {
        return Optional.ofNullable(sessions.computeIfPresent(callId, (id, existing) -> {
            existing.setLastHeartbeatAt(Instant.now());
            return existing;
        }));
    }

    @Override
    public List<CallSession> listSessionsByUser(Long userId) {
        List<CallSession> result = new ArrayList<>();
        for (CallSession session : sessions.values()) {
            if (session.isParticipant(userId)) {
                result.add(session);
            }
        }
        return result;
    }

    @Override
    public List<CallSession> findActiveSessions() {
        List<CallSession> result = new ArrayList<>();
        for (CallSession session : sessions.values()) {
            if (session.getStatus() == CallStatus.ACTIVE || session.getStatus() == CallStatus.RINGING) {
                result.add(session);
            }
        }
        return result;
    }

    @Override
    public void cleanupExpired(Duration ttl) {
        Instant expiredThreshold = Instant.now().minus(ttl);
        sessions.values().removeIf(session -> session.getLastHeartbeatAt() != null
                && session.getLastHeartbeatAt().isBefore(expiredThreshold));
    }

    @Scheduled(fixedDelayString = "${call.session.cleanup-interval-ms:60000}")
    public void scheduledCleanup() {
        cleanupExpired(Duration.ofSeconds(sessionTtlSeconds));
    }
}
