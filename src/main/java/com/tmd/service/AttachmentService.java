package com.tmd.service;

import com.tmd.entity.dto.Attachment;
import com.tmd.entity.dto.FileUploadResponse;

import java.util.List;

public interface AttachmentService {
    /**
     * 保存附件记录
     */
    Attachment saveAttachment(FileUploadResponse fileResponse, String businessType, Long businessId, Long uploaderId);

    /**
     * 批量保存附件记录
     */
    List<Attachment> batchSaveAttachments(List<FileUploadResponse> fileResponses, String businessType, Long businessId,
            Long uploaderId);

    /**
     * 根据ID获取附件
     */
    Attachment getAttachmentById(Long id);

    /**
     * 根据业务类型和业务ID获取附件列表
     */
    List<Attachment> getAttachmentsByBusiness(String businessType, Long businessId);

    /**
     * 根据文件ID获取附件
     */
    Attachment getAttachmentByFileId(String fileId);

    /**
     * 根据上传者ID获取附件列表
     */
    List<Attachment> getAttachmentsByUploaderId(Long uploaderId);

    /**
     * 删除附件（同时删除OSS文件和数据库记录）
     */
    boolean deleteAttachment(Long id);

    /**
     * 根据业务类型和业务ID删除所有附件
     */
    boolean deleteAttachmentsByBusiness(String businessType, Long businessId);

    /**
     * 根据文件ID删除附件
     */
    boolean deleteAttachmentByFileId(String fileId);
}
