package com.tmd.entity.dto.call;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallSignalMessage {
    private CallAction action;
    private String callId;
    private Long fromUserId;
    private Long toUserId;
    private CallType callType;
    private Map<String, Object> payload;
    private Long timestamp;
    private String reason;
}
