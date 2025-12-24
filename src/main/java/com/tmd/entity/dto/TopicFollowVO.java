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
public class TopicFollowVO {
    private Boolean isFollowed;
    private Integer followerCount;
    private LocalDateTime createdAt;
    private Long topicId;
    private Long userId;
    private String username;
    private String avatar;
    private Integer postCount;
}
