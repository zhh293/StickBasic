package com.tmd.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通用附件实体类
 * 支持关联到不同的业务对象（帖子、邮件、用户等）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {
    private Long id;
    private String fileId; // OSS objectKey
    private String fileUrl; // 文件访问URL
    private String fileName; // 原始文件名
    private Long fileSize; // 文件大小（字节）
    private String fileType; // 文件类型：image, video, audio, document
    private String mimeType; // MIME类型，如 image/jpeg, video/mp4
    private String businessType; // 业务类型：post, mail, user, topic等
    private Long businessId; // 业务对象ID（如帖子ID、邮件ID等）
    private Long uploaderId; // 上传者用户ID
    private LocalDateTime uploadTime; // 上传时间
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间
}
