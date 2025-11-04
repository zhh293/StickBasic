package com.tmd.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private String fileId; // objectKey
    private String fileUrl; // 文件访问URL
    private String fileName; // 文件名
    private Long fileSize; // 文件大小（字节）
    private String fileType; // 文件类型：image, video
    private String uploadTime; // 上传时间，ISO 8601格式，如：2024-01-15T10:30:00Z
}
