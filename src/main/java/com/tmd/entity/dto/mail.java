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
public class mail {
    private Long id;
    private Long senderId;
    private String stampType;
    private String stampContent;
    private String senderNickname;
    private String recipientEmail;
    private String content;
    private mailStatus status;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
