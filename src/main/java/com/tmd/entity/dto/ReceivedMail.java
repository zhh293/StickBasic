package com.tmd.entity.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReceivedMail {
    private Long id;
    private Long recipientId;
    private Long senderId;
    private String content;
    private String stampType;
    private String senderNickname;
    private Long originalMailId;
    private String status;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
