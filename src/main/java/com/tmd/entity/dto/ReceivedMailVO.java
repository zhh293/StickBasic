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
public class ReceivedMailVO {
     private Long receivedMailId;
     private String senderNickname;
     private String content;
     private String stampType;
     private String reviewContent;
     private LocalDateTime createdAt;
}
