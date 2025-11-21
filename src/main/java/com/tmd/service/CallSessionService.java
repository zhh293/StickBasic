package com.tmd.service;

import com.tmd.entity.dto.call.CallEndReason;
import com.tmd.entity.dto.call.CallSession;
import com.tmd.entity.dto.call.CallStatus;
import com.tmd.entity.dto.call.CallType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface CallSessionService {

    CallSession createSession(Long callerId, Long calleeId, CallType callType);

    Optional<CallSession> updateStatus(String callId, CallStatus status);

    Optional<CallSession> getSession(String callId);

    Optional<CallSession> endSession(String callId, CallEndReason reason);

    Optional<CallSession> heartbeat(String callId);

    List<CallSession> listSessionsByUser(Long userId);

    List<CallSession> findActiveSessions();

    void cleanupExpired(Duration ttl);
}
