package com.tmd.entity.dto.call;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CallSession {
    private String callId;
    private Long callerId;
    private Long calleeId;
    private CallType callType;
    private CallStatus status;
    private CallEndReason endReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastHeartbeatAt;

    public boolean isParticipant(Long userId) {
        if (userId == null) {
            return false;
        }
        return userId.equals(callerId) || userId.equals(calleeId);
    }
}
