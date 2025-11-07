package com.tmd.service.impl;

import com.tmd.entity.dto.Attachment;
import com.tmd.entity.dto.FileUploadResponse;
import com.tmd.entity.dto.AliOssUtil;
import com.tmd.mapper.AttachmentMapper;
import com.tmd.service.AttachmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    @Autowired
    private AttachmentMapper attachmentMapper;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Override
    public Attachment saveAttachment(FileUploadResponse fileResponse, String businessType, Long businessId,
            Long uploaderId) {
        LocalDateTime now = LocalDateTime.now();
        Attachment attachment = Attachment.builder()
                .fileId(fileResponse.getFileId())
                .fileUrl(fileResponse.getFileUrl())
                .fileName(fileResponse.getFileName())
                .fileSize(fileResponse.getFileSize())
                .fileType(fileResponse.getFileType())
                .mimeType(detectMimeType(fileResponse.getFileName(), fileResponse.getFileType()))
                .businessType(businessType)
                .businessId(businessId)
                .uploaderId(uploaderId)
                .uploadTime(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        attachmentMapper.insert(attachment);
        log.info("附件保存成功: id={}, fileId={}, businessType={}, businessId={}",
                attachment.getId(), fileResponse.getFileId(), businessType, businessId);
        return attachment;
    }

    @Override
    public List<Attachment> batchSaveAttachments(List<FileUploadResponse> fileResponses, String businessType,
            Long businessId, Long uploaderId) {
        if (fileResponses == null || fileResponses.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDateTime now = LocalDateTime.now();
        List<Attachment> attachments = fileResponses.stream()
                .map(fileResponse -> Attachment.builder()
                        .fileId(fileResponse.getFileId())
                        .fileUrl(fileResponse.getFileUrl())
                        .fileName(fileResponse.getFileName())
                        .fileSize(fileResponse.getFileSize())
                        .fileType(fileResponse.getFileType())
                        .mimeType(detectMimeType(fileResponse.getFileName(), fileResponse.getFileType()))
                        .businessType(businessType)
                        .businessId(businessId)
                        .uploaderId(uploaderId)
                        .uploadTime(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .collect(Collectors.toList());

        attachmentMapper.batchInsert(attachments);
        log.info("批量保存附件成功: count={}, businessType={}, businessId={}",
                attachments.size(), businessType, businessId);
        return attachments;
    }

    @Override
    public Attachment getAttachmentById(Long id) {
        return attachmentMapper.selectById(id);
    }

    @Override
    public List<Attachment> getAttachmentsByBusiness(String businessType, Long businessId) {
        return attachmentMapper.selectByBusiness(businessType, businessId);
    }

    @Override
    public Attachment getAttachmentByFileId(String fileId) {
        return attachmentMapper.selectByFileId(fileId);
    }

    @Override
    public List<Attachment> getAttachmentsByUploaderId(Long uploaderId) {
        return attachmentMapper.selectByUploaderId(uploaderId);
    }

    @Override
    public boolean deleteAttachment(Long id) {
        try {
            Attachment attachment = attachmentMapper.selectById(id);
            if (attachment == null) {
                log.warn("附件不存在: id={}", id);
                return false;
            }

            // 删除OSS文件
            boolean deleted = aliOssUtil.delete(attachment.getFileId());
            if (!deleted) {
                log.warn("OSS文件删除失败: fileId={}", attachment.getFileId());
            }

            // 删除数据库记录
            attachmentMapper.deleteById(id);
            log.info("附件删除成功: id={}, fileId={}", id, attachment.getFileId());
            return true;
        } catch (Exception e) {
            log.error("删除附件失败: id={}", id, e);
            return false;
        }
    }

    @Override
    public boolean deleteAttachmentsByBusiness(String businessType, Long businessId) {
        try {
            List<Attachment> attachments = attachmentMapper.selectByBusiness(businessType, businessId);

            // 删除所有OSS文件
            for (Attachment attachment : attachments) {
                try {
                    aliOssUtil.delete(attachment.getFileId());
                } catch (Exception e) {
                    log.warn("删除OSS文件失败: fileId={}", attachment.getFileId(), e);
                }
            }

            // 删除数据库记录
            attachmentMapper.deleteByBusiness(businessType, businessId);
            log.info("批量删除附件成功: businessType={}, businessId={}, count={}",
                    businessType, businessId, attachments.size());
            return true;
        } catch (Exception e) {
            log.error("批量删除附件失败: businessType={}, businessId={}", businessType, businessId, e);
            return false;
        }
    }

    @Override
    public boolean deleteAttachmentByFileId(String fileId) {
        try {
            Attachment attachment = attachmentMapper.selectByFileId(fileId);
            if (attachment == null) {
                log.warn("附件不存在: fileId={}", fileId);
                return false;
            }

            // 删除OSS文件
            boolean deleted = aliOssUtil.delete(fileId);
            if (!deleted) {
                log.warn("OSS文件删除失败: fileId={}", fileId);
            }

            // 删除数据库记录
            attachmentMapper.deleteByFileId(fileId);
            log.info("附件删除成功: fileId={}", fileId);
            return true;
        } catch (Exception e) {
            log.error("删除附件失败: fileId={}", fileId, e);
            return false;
        }
    }

    /**
     * 根据文件名和文件类型检测MIME类型
     */
    private String detectMimeType(String fileName, String fileType) {
        if (fileName == null) {
            return "application/octet-stream";
        }

        String lowerFileName = fileName.toLowerCase();

        // 根据文件类型判断
        if ("image".equals(fileType)) {
            if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
                return "image/jpeg";
            } else if (lowerFileName.endsWith(".png")) {
                return "image/png";
            } else if (lowerFileName.endsWith(".gif")) {
                return "image/gif";
            } else if (lowerFileName.endsWith(".webp")) {
                return "image/webp";
            }
            return "image/*";
        } else if ("video".equals(fileType)) {
            if (lowerFileName.endsWith(".mp4")) {
                return "video/mp4";
            } else if (lowerFileName.endsWith(".avi")) {
                return "video/avi";
            } else if (lowerFileName.endsWith(".mov")) {
                return "video/quicktime";
            } else if (lowerFileName.endsWith(".wmv")) {
                return "video/x-ms-wmv";
            }
            return "video/*";
        }

        // 根据扩展名判断
        if (lowerFileName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx")) {
            return "application/msword";
        } else if (lowerFileName.endsWith(".xls") || lowerFileName.endsWith(".xlsx")) {
            return "application/vnd.ms-excel";
        } else if (lowerFileName.endsWith(".zip")) {
            return "application/zip";
        } else if (lowerFileName.endsWith(".txt")) {
            return "text/plain";
        }

        return "application/octet-stream";
    }
}
