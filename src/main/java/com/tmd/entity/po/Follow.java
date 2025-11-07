package com.tmd.entity.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Follow {
    private Long id;
    private Long followerId;      // 关注者ID
    private Long followingId;     // 被关注者ID
    private LocalDateTime createdAt;
}