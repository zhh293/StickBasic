package com.tmd.controller;

import com.tmd.entity.dto.AliOssUtil;
import com.tmd.entity.dto.FileUploadResponse;
import com.tmd.entity.dto.Result;
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
import java.util.Base64;
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

    // 最大文件大小：100MB
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

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

            // 3. 如果是图片类型，进行AI审核
            if ("image".equals(fileType)) {
                boolean moderationPassed = performImageModeration(file);
                if (!moderationPassed) {
                    return Result.error("图片内容审核未通过，包含违规内容");
                }
            }

            // 4. 生成唯一的 objectKey
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectKey = generateObjectKey(fileType, fileExtension);

            // 5. 上传文件到OSS（使用流式上传，支持大文件）
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

            FileUploadResponse fileInfo = aliOssUtil.getFileInfo(fileId);
            if (fileInfo == null) {
                return Result.error("文件不存在: " + fileId);
            }

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

            boolean deleted = aliOssUtil.delete(fileId);
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
