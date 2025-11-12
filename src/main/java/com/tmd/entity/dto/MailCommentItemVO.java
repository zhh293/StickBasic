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
public class MailCommentItemVO {
    private Long mailId;
    private String commentContent;
    private LocalDateTime createdAt;
    private String originalContent;
    private String originalStampType;
    private String originalSenderNickname;
}
