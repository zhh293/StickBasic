package com.tmd.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostCreateDTO {
    private String title;
    private String content;
    // 帖子类型：story/daily_sign 等
    private String type;
    private Long topicId; // 可选
    private String publishLocation; // 可选
    private BigDecimal latitude; // 可选
    private BigDecimal longitude; // 可选
    // 状态：draft/published
    private String status;
    // 附件仅在 OSS 中，创建时不写入 attachment 表
    private List<AttachmentLite> attachments;
}