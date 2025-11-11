package com.tmd.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostListItemVO {
    private Long id;
    private String title;
    private String content;
    private Long userId;
    private String authorUsername;
    private String authorAvatar;
    private Long topicId;
    private String topicName;
    private Integer likeCount;
    private Integer commentCount;
    private Integer shareCount;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AttachmentLite> attachments;
}