package com.tmd.controller;

import com.tmd.entity.dto.AliOssUtil;
import com.tmd.entity.dto.Attachment;
import com.tmd.entity.dto.FileUploadResponse;
import com.tmd.entity.dto.Result;
import com.tmd.service.AttachmentService;
import com.tmd.tools.BaseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 通用文件上传和删除接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/common")
public class CommonController {

    @Autowired
    @Qualifier("moderationClient")
    private ChatClient moderationClient;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AttachmentService attachmentService;

    // 最大文件大小：100MB
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;
    // 图片审核文件大小阈值：超过10MB的图片跳过审核（避免内存占用过大）
    private static final long IMAGE_MODERATION_SIZE_THRESHOLD = 10 * 1024 * 1024;

    /**
     * 文件上传接口
     * 
     * @param file     上传的文件
     * @param fileType 文件类型：image（图像）、video（视频/音频）
     * @return 文件上传结果
     */
    @PostMapping("/upload")
    public Result uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") String fileType) {

        try {
            // 1. 参数校验
            if (file == null || file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            if (fileType == null || (!fileType.equals("image") && !fileType.equals("video"))) {
                return Result.error("文件类型必须为 image 或 video");
            }

            // 2. 文件大小校验
            long fileSize = file.getSize();
            if (fileSize > MAX_FILE_SIZE) {
                return Result.error("文件大小不能超过 100MB");
            }

            // 3. 如果是图片类型，进行AI审核（仅对小文件进行审核，大文件跳过以避免内存占用）
            if ("image".equals(fileType) && fileSize <= IMAGE_MODERATION_SIZE_THRESHOLD) {
                boolean moderationPassed = performImageModeration(file);
                if (!moderationPassed) {
                    return Result.error("图片内容审核未通过，包含违规内容");
                }
            } else if ("image".equals(fileType) && fileSize > IMAGE_MODERATION_SIZE_THRESHOLD) {
                log.warn("图片文件过大（{}MB），跳过AI审核以避免内存占用", fileSize / 1024 / 1024);
            }

            // 4. 生成唯一的 objectKey
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectKey = generateObjectKey(fileType, fileExtension);

            // 5. 使用流式上传到OSS（避免大文件占用过多内存）
            String fileUrl;
            try (InputStream inputStream = file.getInputStream()) {
                fileUrl = aliOssUtil.upload(inputStream, objectKey);
            }

            // 6. 如果是图片，将图片信息存储到向量数据库（可选，用于后续检索）
            if ("image".equals(fileType)) {
                storeImageToVectorStore(file, objectKey, fileUrl);
            }

            // 7. 构建响应（包含上传时间）
            String uploadTime = LocalDateTime.now().atOffset(ZoneOffset.UTC).toString();
            FileUploadResponse response = FileUploadResponse.builder()
                    .fileId(objectKey)
                    .fileUrl(fileUrl)
                    .fileName(originalFilename != null ? originalFilename : "unknown")
                    .fileSize(fileSize)
                    .fileType(fileType)
                    .uploadTime(uploadTime)
                    .build();

            log.info("文件上传成功: objectKey={}, fileType={}, fileSize={}", objectKey, fileType, fileSize);
            return Result.success(response);

        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件信息接口
     * 
     * @param fileId objectKey（文件ID）
     * @return 文件信息
     */
    @GetMapping("/files/{fileId}")
    public Result getFileInfo(@PathVariable("fileId") String fileId) {
        try {
            if (fileId == null || fileId.trim().isEmpty()) {
                return Result.error("文件ID不能为空");
            }

            // 从附件表查询文件信息
            Attachment attachment = attachmentService.getAttachmentByFileId(fileId);
            if (attachment == null) {
                return Result.error("文件不存在: " + fileId);
            }

            FileUploadResponse fileInfo = FileUploadResponse.builder()
                    .fileId(attachment.getFileId())
                    .fileUrl(attachment.getFileUrl())
                    .fileName(attachment.getFileName())
                    .fileSize(attachment.getFileSize())
                    .fileType(attachment.getFileType())
                    .uploadTime(attachment.getUploadTime().toString())
                    .build();

            log.info("获取文件信息成功: fileId={}", fileId);
            return Result.success(fileInfo);
        } catch (Exception e) {
            log.error("获取文件信息失败: fileId={}", fileId, e);
            return Result.error("获取文件信息失败: " + e.getMessage());
        }
    }

    /**
     * 文件删除接口
     * 
     * @param fileId objectKey
     * @return 删除结果
     */
    @DeleteMapping("/delete/{fileId}")
    public Result deleteFile(@PathVariable("fileId") String fileId) {
        try {
            if (fileId == null || fileId.trim().isEmpty()) {
                return Result.error("文件ID不能为空");
            }

            // 通过附件服务删除（会同时删除OSS文件和数据库记录）
            boolean deleted = attachmentService.deleteAttachmentByFileId(fileId);
            if (deleted) {
                log.info("文件删除成功: fileId={}", fileId);
                return Result.success("文件删除成功");
            } else {
                return Result.error("文件删除失败，可能文件不存在");
            }
        } catch (Exception e) {
            log.error("文件删除失败: fileId={}", fileId, e);
            return Result.error("文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 对图片进行AI审核
     * 
     * @param file 图片文件
     * @return true表示审核通过，false表示审核不通过
     */
    private boolean performImageModeration(MultipartFile file) {
        try {
            // 将图片转换为Base64编码
            byte[] imageBytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 构建审核提示，包含图片Base64数据
            String prompt = "请审核以下图片内容（Base64编码）。\n图片数据：" + base64Image;

            // 调用AI进行审核
            String response = moderationClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 解析返回结果
            String resultStr = response.trim().replaceAll("\\s+", "");
            int result = -1;

            // 尝试从响应中提取0或1
            if (resultStr.matches(".*\\b0\\b.*") && !resultStr.matches(".*\\b10\\b.*")
                    && !resultStr.matches(".*\\b01\\b.*")) {
                result = 0;
            } else if (resultStr.matches(".*\\b1\\b.*")) {
                result = 1;
            } else {
                // 如果无法匹配，尝试直接解析第一个字符
                try {
                    char firstChar = resultStr.charAt(0);
                    if (firstChar == '0' || firstChar == '1') {
                        result = Character.getNumericValue(firstChar);
                    } else {
                        // 查找字符串中的第一个0或1
                        int idx0 = resultStr.indexOf('0');
                        int idx1 = resultStr.indexOf('1');
                        if (idx0 != -1 && (idx1 == -1 || idx0 < idx1)) {
                            result = 0;
                        } else if (idx1 != -1) {
                            result = 1;
                        } else {
                            // 如果还是无法解析，默认返回0（拒绝）
                            result = 0;
                        }
                    }
                } catch (Exception e) {
                    // 如果还是无法解析，默认返回0（拒绝）
                    result = 0;
                }
            }

            log.info("图片审核结果: result={}, response={}", result, response);
            return result == 1;

        } catch (Exception e) {
            log.error("图片审核异常", e);
            // 审核异常时，为了安全起见，拒绝上传
            return false;
        }
    }

    /**
     * 将图片信息存储到向量数据库
     * 
     * @param file      图片文件
     * @param objectKey objectKey
     * @param fileUrl   文件URL
     */
    private void storeImageToVectorStore(MultipartFile file, String objectKey, String fileUrl) {
        try {
            // 创建文档，包含图片元数据信息
            String content = String.format("图片文件: objectKey=%s, fileName=%s, fileUrl=%s, uploadTime=%s",
                    objectKey,
                    file.getOriginalFilename(),
                    fileUrl,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            Document document = new Document(content);
            document.getMetadata().put("objectKey", objectKey);
            document.getMetadata().put("fileUrl", fileUrl);
            document.getMetadata().put("fileName", file.getOriginalFilename());
            document.getMetadata().put("fileType", "image");
            document.getMetadata().put("fileSize", String.valueOf(file.getSize()));

            // 存储到向量数据库
            vectorStore.add(java.util.List.of(document));
            log.info("图片信息已存储到向量数据库: objectKey={}", objectKey);
        } catch (Exception e) {
            log.error("存储图片信息到向量数据库失败: objectKey={}", objectKey, e);
            // 不影响主流程，只记录日志
        }
    }

    /**
     * 批量上传文件接口
     * 
     * @param files    上传的文件列表
     * @param fileType 文件类型：image（图像）、video（视频/音频）
     * @return 文件上传结果列表
     */
    @PostMapping("/upload/batch")
    public Result batchUploadFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("fileType") String fileType) {
        try {
            if (files == null || files.length == 0) {
                return Result.error("文件列表不能为空");
            }

            if (fileType == null || (!fileType.equals("image") && !fileType.equals("video"))) {
                return Result.error("文件类型必须为 image 或 video");
            }

            List<FileUploadResponse> responses = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                try {
                    if (file == null || file.isEmpty()) {
                        errors.add("第 " + (i + 1) + " 个文件为空");
                        continue;
                    }

                    // 文件大小校验
                    long fileSize = file.getSize();
                    if (fileSize > MAX_FILE_SIZE) {
                        errors.add("第 " + (i + 1) + " 个文件大小超过 100MB");
                        continue;
                    }

                    // 如果是图片类型，进行AI审核（仅对小文件进行审核）
                    if ("image".equals(fileType) && fileSize <= IMAGE_MODERATION_SIZE_THRESHOLD) {
                        boolean moderationPassed = performImageModeration(file);
                        if (!moderationPassed) {
                            errors.add("第 " + (i + 1) + " 个图片审核未通过");
                            continue;
                        }
                    } else if ("image".equals(fileType) && fileSize > IMAGE_MODERATION_SIZE_THRESHOLD) {
                        log.warn("第 {} 个图片文件过大（{}MB），跳过AI审核", i + 1, fileSize / 1024 / 1024);
                    }

                    // 生成唯一的 objectKey
                    String originalFilename = file.getOriginalFilename();
                    String fileExtension = "";
                    if (originalFilename != null && originalFilename.contains(".")) {
                        fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
                    }
                    String objectKey = generateObjectKey(fileType, fileExtension);

                    // 使用流式上传到OSS（避免大文件占用过多内存）
                    String fileUrl;
                    try (InputStream inputStream = file.getInputStream()) {
                        fileUrl = aliOssUtil.upload(inputStream, objectKey);
                    }

                    // 如果是图片，将图片信息存储到向量数据库
                    if ("image".equals(fileType)) {
                        storeImageToVectorStore(file, objectKey, fileUrl);
                    }

                    // 构建响应
                    String uploadTime = LocalDateTime.now().atOffset(ZoneOffset.UTC).toString();
                    FileUploadResponse response = FileUploadResponse.builder()
                            .fileId(objectKey)
                            .fileUrl(fileUrl)
                            .fileName(originalFilename != null ? originalFilename : "unknown")
                            .fileSize(fileSize)
                            .fileType(fileType)
                            .uploadTime(uploadTime)
                            .build();

                    responses.add(response);
                } catch (Exception e) {
                    log.error("批量上传文件失败: 第 {} 个文件", i + 1, e);
                    errors.add("第 " + (i + 1) + " 个文件上传失败: " + e.getMessage());
                }
            }

            if (responses.isEmpty() && !errors.isEmpty()) {
                return Result.error("所有文件上传失败: " + String.join("; ", errors));
            }

            log.info("批量上传文件完成: 成功={}, 失败={}", responses.size(), errors.size());
            return Result.success(responses);
        } catch (Exception e) {
            log.error("批量上传文件失败", e);
            return Result.error("批量上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件并关联到业务对象
     * 
     * @param file         上传的文件
     * @param fileType     文件类型：image（图像）、video（视频/音频）
     * @param businessType 业务类型：post, mail, user, topic等
     * @param businessId   业务对象ID（如帖子ID、邮件ID等）
     * @return 附件信息
     */
    @PostMapping("/upload/attach")
    public Result uploadAndAttach(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") String fileType,
            @RequestParam("businessType") String businessType,
            @RequestParam("businessId") Long businessId) {
        try {
            // 1. 先上传文件
            Result uploadResult = uploadFile(file, fileType);
            if (uploadResult.getCode() != 200) {
                return uploadResult;
            }

            FileUploadResponse fileResponse = (FileUploadResponse) uploadResult.getData();
            Long uploaderId = BaseContext.get();

            // 2. 保存附件记录
            Attachment attachment = attachmentService.saveAttachment(
                    fileResponse, businessType, businessId, uploaderId);

            log.info("文件上传并关联成功: attachmentId={}, businessType={}, businessId={}",
                    attachment.getId(), businessType, businessId);
            return Result.success(attachment);
        } catch (Exception e) {
            log.error("上传并关联文件失败", e);
            return Result.error("上传并关联文件失败: " + e.getMessage());
        }
    }

    /**
     * 批量上传文件并关联到业务对象
     * 
     * @param files        上传的文件列表
     * @param fileType     文件类型：image（图像）、video（视频/音频）
     * @param businessType 业务类型：post, mail, user, topic等
     * @param businessId   业务对象ID（如帖子ID、邮件ID等）
     * @return 附件信息列表
     */
    @PostMapping("/upload/attach/batch")
    public Result batchUploadAndAttach(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("fileType") String fileType,
            @RequestParam("businessType") String businessType,
            @RequestParam("businessId") Long businessId) {
        try {
            // 1. 先批量上传文件
            Result uploadResult = batchUploadFiles(files, fileType);
            if (uploadResult.getCode() != 200) {
                return uploadResult;
            }

            @SuppressWarnings("unchecked")
            List<FileUploadResponse> fileResponses = (List<FileUploadResponse>) uploadResult.getData();
            Long uploaderId = BaseContext.get();

            // 2. 批量保存附件记录
            List<Attachment> attachments = attachmentService.batchSaveAttachments(
                    fileResponses, businessType, businessId, uploaderId);

            log.info("批量上传并关联成功: count={}, businessType={}, businessId={}",
                    attachments.size(), businessType, businessId);
            return Result.success(attachments);
        } catch (Exception e) {
            log.error("批量上传并关联文件失败", e);
            return Result.error("批量上传并关联文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取业务对象的附件列表
     * 
     * @param businessType 业务类型：post, mail, user, topic等
     * @param businessId   业务对象ID
     * @return 附件列表
     */
    @GetMapping("/attachments")
    public Result getAttachments(
            @RequestParam("businessType") String businessType,
            @RequestParam("businessId") Long businessId) {
        try {
            if (businessType == null || businessType.trim().isEmpty()) {
                return Result.error("业务类型不能为空");
            }
            if (businessId == null) {
                return Result.error("业务对象ID不能为空");
            }

            List<Attachment> attachments = attachmentService.getAttachmentsByBusiness(businessType, businessId);
            log.info("获取附件列表成功: businessType={}, businessId={}, count={}",
                    businessType, businessId, attachments.size());
            return Result.success(attachments);
        } catch (Exception e) {
            log.error("获取附件列表失败: businessType={}, businessId={}", businessType, businessId, e);
            return Result.error("获取附件列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据附件ID获取附件信息
     * 
     * @param attachmentId 附件ID
     * @return 附件信息
     */
    @GetMapping("/attachments/{attachmentId}")
    public Result getAttachmentById(@PathVariable("attachmentId") Long attachmentId) {
        try {
            if (attachmentId == null) {
                return Result.error("附件ID不能为空");
            }

            Attachment attachment = attachmentService.getAttachmentById(attachmentId);
            if (attachment == null) {
                return Result.error("附件不存在: " + attachmentId);
            }

            log.info("获取附件信息成功: attachmentId={}", attachmentId);
            return Result.success(attachment);
        } catch (Exception e) {
            log.error("获取附件信息失败: attachmentId={}", attachmentId, e);
            return Result.error("获取附件信息失败: " + e.getMessage());
        }
    }

    /**
     * 删除附件（同时删除OSS文件和数据库记录）
     * 
     * @param attachmentId 附件ID
     * @return 删除结果
     */
    @DeleteMapping("/attachments/{attachmentId}")
    public Result deleteAttachment(@PathVariable("attachmentId") Long attachmentId) {
        try {
            if (attachmentId == null) {
                return Result.error("附件ID不能为空");
            }

            boolean deleted = attachmentService.deleteAttachment(attachmentId);
            if (deleted) {
                log.info("附件删除成功: attachmentId={}", attachmentId);
                return Result.success("附件删除成功");
            } else {
                return Result.error("附件删除失败，可能附件不存在");
            }
        } catch (Exception e) {
            log.error("附件删除失败: attachmentId={}", attachmentId, e);
            return Result.error("附件删除失败: " + e.getMessage());
        }
    }

    /**
     * 删除业务对象的所有附件
     * 
     * @param businessType 业务类型：post, mail, user, topic等
     * @param businessId   业务对象ID
     * @return 删除结果
     */
    @DeleteMapping("/attachments")
    public Result deleteAttachmentsByBusiness(
            @RequestParam("businessType") String businessType,
            @RequestParam("businessId") Long businessId) {
        try {
            if (businessType == null || businessType.trim().isEmpty()) {
                return Result.error("业务类型不能为空");
            }
            if (businessId == null) {
                return Result.error("业务对象ID不能为空");
            }

            boolean deleted = attachmentService.deleteAttachmentsByBusiness(businessType, businessId);
            if (deleted) {
                log.info("批量删除附件成功: businessType={}, businessId={}", businessType, businessId);
                return Result.success("附件删除成功");
            } else {
                return Result.error("附件删除失败");
            }
        } catch (Exception e) {
            log.error("批量删除附件失败: businessType={}, businessId={}", businessType, businessId, e);
            return Result.error("附件删除失败: " + e.getMessage());
        }
    }

    /**
     * 生成唯一的 objectKey
     * 
     * @param fileType      文件类型
     * @param fileExtension 文件扩展名
     * @return objectKey
     */
    private String generateObjectKey(String fileType, String fileExtension) {
        // 使用日期时间 + UUID 生成唯一文件名
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format("%s/%s/%s%s", fileType, dateDir, uuid, fileExtension);
    }
}
