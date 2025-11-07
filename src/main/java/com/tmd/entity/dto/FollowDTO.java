package com.tmd.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FollowDTO {
    private Long followerId;      // 关注者ID
    private Long followingId;     // 被关注者ID
}