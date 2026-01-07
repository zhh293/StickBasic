package com.tmd.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.web.PortResolverImpl;

import java.time.LocalDateTime;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PostComment {
    private Long id;
    private Long postId;
    private Long commenterId;
    private String content;
    private Long parentId;
    private LocalDateTime createdAt;
    private Long rootId;
    private Integer likes;
    private Integer dislikes;
    private Integer replyCount;
}
