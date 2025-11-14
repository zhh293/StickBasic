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
public class ChatMessageVO {
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private String content;
    private ChatStatus status;
    private LocalDateTime createdAt;
}