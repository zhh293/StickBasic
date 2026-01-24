package com.tmd.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCommentVO {
    private Long commentId;
    private String content;
    private Integer likeCount;
    private Integer replyCount;
    private String status;
    private UserInfo author;
    private List<ReplyVO> replies;
    private Boolean isLiked;
    private String createdAt;
    private String updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyVO {
        private Long commentId;
        private String content;
        private Integer likeCount;
        private UserInfo replyToUser;
        private UserInfo author;
        private String createdAt;
        private Boolean isLiked;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long userId;
        private String username;
        private String avatar;
    }
}
