package com.tmd.controller.CommonController分片版本;

import com.alibaba.fastjson.JSONObject;
import com.tmd.config.RedisCache;
import com.tmd.entity.dto.AliOssUtil;
import com.tmd.entity.dto.Attachment;
import com.tmd.entity.dto.FileUploadResponse;
import com.tmd.entity.dto.Result;
import com.tmd.mapper.AttachmentMapper;
import com.tmd.tools.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 分片上传控制器
 */
@RestController
@RequestMapping("/upload/slice")
@Slf4j
public class CommonControllerUploadBySlice {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private AttachmentMapper attachmentMapper;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // 临时文件存储目录
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + File.separator + "tmd_slice_upload";
    // Redis Key 前缀
    private static final String TASK_KEY_PREFIX = "slice:task:";
    // 任务过期时间（小时）
    private static final long TASK_EXPIRE_HOURS = 24;

    // 缓存 Key 前缀 (参考 AttachmentServiceImpl)
    private static final String CACHE_KEY_PREFIX_ID = "attachment:id:";
    private static final String CACHE_KEY_PREFIX_FILE_ID = "attachment:fileId:";
    private static final String CACHE_KEY_PREFIX_BUSINESS = "attachment:business:";
    private static final String CACHE_KEY_PREFIX_UPLOADER = "attachment:uploader:";
    private static final int CACHE_EXPIRE_DAYS = 7;

    // 分片大小限制（1KB ~ 5MB）
    private static final long MIN_CHUNK_SIZE = 1024; // 1KB
    private static final long MAX_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * 1. 初始化分片上传任务
     *
     * @param fileMd5     文件MD5
     * @param fileName    文件名
     * @param totalChunks 总分片数（可选）
     * @param fileType    文件类型（image/video等）
     * @return 任务ID
     */
    @PostMapping("/init")
    public Result initTask(@RequestParam("fileMd5") String fileMd5,
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "totalChunks", required = false) Integer totalChunks,
            @RequestParam(value = "fileType", defaultValue = "other") String fileType) {

        // 校验MD5和文件名非空、合法
        if (fileMd5 == null || fileMd5.trim().isEmpty() || fileMd5.length() != 32) {
            return Result.error("文件MD5不合法，必须为32位字符串");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            return Result.error("文件名不能为空");
        }
        // 生成唯一任务ID
        String taskId = UUID.randomUUID().toString().replace("-", "");
        String key = TASK_KEY_PREFIX + taskId;

        // 存储任务信息到 Redis Hash
        Map<String, Object> initData = new HashMap<>();
        initData.put("fileMd5", fileMd5);
        initData.put("fileName", fileName);
        initData.put("fileType", fileType);
        if (totalChunks != null) {
            initData.put("totalChunks", totalChunks);
        }
        initData.put("createdAt", System.currentTimeMillis());

        redisTemplate.opsForHash().putAll(key, initData);
        redisTemplate.expire(key, TASK_EXPIRE_HOURS, TimeUnit.HOURS);

        // 创建本地临时目录
        File taskDir = new File(TEMP_DIR, taskId);
        if (!taskDir.exists()) {
            boolean mkdirs = taskDir.mkdirs();
            if (!mkdirs) {
                return Result.error("创建临时目录失败");
            }
            // 设置目录可读写，避免权限不足
            taskDir.setWritable(true);
            taskDir.setReadable(true);
        }
        return Result.success(taskId);
    }

    /**
     * 2. 上传分片
     *
     * @param taskId     任务ID
     * @param chunkIndex 分片索引（从0开始或从1开始均可，需保持一致）
     * @param file       分片文件
     * @return 上传结果
     */
    @PostMapping("/chunk")
    public Result uploadChunk(@RequestParam("taskId") String taskId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("file") MultipartFile file) {
        // 校验分片索引非负
        if (chunkIndex == null || chunkIndex < 0) {
            return Result.error("分片索引不合法，必须为非负整数");
        }
        // 校验分片文件大小（1KB ~ 5MB）
        long chunkSize = file.getSize();
        if (chunkSize < MIN_CHUNK_SIZE || chunkSize > MAX_CHUNK_SIZE) {
            return Result.error("分片大小不合法，必须在1KB~5MB之间");
        }

        String key = TASK_KEY_PREFIX + taskId;
        if (!redisTemplate.hasKey(key)) {
            return Result.error("任务不存在或已过期");
        }

        try {
            File taskDir = new File(TEMP_DIR, taskId);
            if (!taskDir.exists()) {
                taskDir.mkdirs();
                // 设置目录可读写，避免权限不足
                taskDir.setWritable(true);
                taskDir.setReadable(true);
            }

            // 保存分片到本地
            File chunkFile = new File(taskDir, String.valueOf(chunkIndex));
            file.transferTo(chunkFile);

            // 记录分片路径到 Redis Hash
            redisTemplate.opsForHash().put(key, "chunk_" + chunkIndex, chunkFile.getAbsolutePath());

            // 刷新过期时间
            redisTemplate.expire(key, TASK_EXPIRE_HOURS, TimeUnit.HOURS);

            return Result.success("分片 " + chunkIndex + " 上传成功");
        } catch (IOException e) {
            log.error("分片上传失败: taskId={}, index={}", taskId, chunkIndex, e);
            return Result.error("分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 断点续传：查询已上传的分片索引
     * 
     * @param taskId 任务ID
     * @return 已上传的分片索引列表
     */
    @GetMapping("/query")
    public Result queryUploadedChunks(@RequestParam("taskId") String taskId) {
        String key = TASK_KEY_PREFIX + taskId;
        if (!redisTemplate.hasKey(key)) {
            return Result.error("任务不存在或已过期");
        }
        // 收集已上传的分片索引
        List<Integer> uploadedChunks = new ArrayList<>();
        Map<Object, Object> taskData = redisTemplate.opsForHash().entries(key);
        for (Object k : taskData.keySet()) {
            String kStr = (String) k;
            if (kStr.startsWith("chunk_")) {
                try {
                    uploadedChunks.add(Integer.parseInt(kStr.substring(6)));
                } catch (NumberFormatException e) {
                    log.warn("忽略非数字分片索引键: {}", kStr);
                }
            }
        }
        Collections.sort(uploadedChunks);
        return Result.success(uploadedChunks);
    }

    /**
     * 3. 合并分片并上传到云端
     *
     * @param taskId       任务ID
     * @param businessType 业务类型
     * @param businessId   业务ID
     * @param uploaderId   上传者ID
     * @return 附件信息
     */
    @PostMapping("/merge")
    public Result mergeChunks(@RequestParam("taskId") String taskId,
            @RequestParam(value = "businessType", required = false) String businessType,
            @RequestParam(value = "businessId", required = false) Long businessId,
            @RequestParam(value = "uploaderId", required = false) Long uploaderId) {
        String key = TASK_KEY_PREFIX + taskId;
        if (!redisTemplate.hasKey(key)) {
            return Result.error("任务不存在或已过期");
        }

        // 获取任务信息
        Map<Object, Object> taskData = redisTemplate.opsForHash().entries(key);
        String fileName = (String) taskData.get("fileName");
        String originalMd5 = (String) taskData.get("fileMd5");
        String fileType = (String) taskData.get("fileType");

        // 收集所有分片索引
        List<Integer> chunkIndices = new ArrayList<>();
        for (Object k : taskData.keySet()) {
            String kStr = (String) k;
            if (kStr.startsWith("chunk_")) {
                try {
                    chunkIndices.add(Integer.parseInt(kStr.substring(6)));
                } catch (NumberFormatException e) {
                    // 忽略非数字后缀的键
                    log.warn("忽略非数字分片索引键: {}", kStr);
                }
            }
        }
        Collections.sort(chunkIndices);

        if (chunkIndices.isEmpty()) {
            return Result.error("未找到任何分片数据");
        }

        // 检查分片连续性 (假设从0或1开始，这里简单判断是否连续)
        // 更严谨的做法是前端传递 totalChunks，后端校验 chunkIndices.size() == totalChunks
        if (taskData.containsKey("totalChunks")) {
            int totalChunks = (Integer) taskData.get("totalChunks");
            if (chunkIndices.size() != totalChunks) {
                return Result.error("分片数量不完整，预期 " + totalChunks + "，实际 " + chunkIndices.size());
            }
        }

        File taskDir = new File(TEMP_DIR, taskId);
        File mergedFile = new File(taskDir, fileName);

        // 执行合并
        try (FileOutputStream fos = new FileOutputStream(mergedFile)) {
            for (Integer index : chunkIndices) {
                String chunkPath = (String) taskData.get("chunk_" + index);
                File chunkFile = new File(chunkPath);
                if (!chunkFile.exists()) {
                    return Result.error("分片文件丢失: " + index);
                }
                Files.copy(chunkFile.toPath(), fos);
            }
        } catch (IOException e) {
            log.error("合并文件失败", e);
            return Result.error("合并文件失败");
        }

        // 校验 MD5
        try (FileInputStream fis = new FileInputStream(mergedFile)) {
            String mergedMd5 = getMD5(fis);
            if (!mergedMd5.equalsIgnoreCase(originalMd5)) {
                // 校验失败，删除合并文件
                mergedFile.delete();
                return Result.error("文件MD5校验失败，可能上传过程中数据损坏");
            }
        } catch (Exception e) {
            log.error("MD5校验异常", e);
            return Result.error("MD5校验异常");
        }

        // 上传到 OSS
        String fileUrl;
        String objectKey;
        try (FileInputStream fis = new FileInputStream(mergedFile)) {
            String ext = "";
            if (fileName.contains(".")) {
                ext = fileName.substring(fileName.lastIndexOf("."));
            }
            objectKey = generateObjectKey(fileType, ext);
            fileUrl = aliOssUtil.upload(fis, objectKey);
        } catch (Exception e) {
            log.error("上传OSS失败", e);
            return Result.error("上传OSS失败");
        }

        // 构造响应对象
        long fileSize = mergedFile.length();
        String uploadTime = LocalDateTime.now().atOffset(ZoneOffset.UTC).toString();
        FileUploadResponse response = FileUploadResponse.builder()
                .fileId(objectKey)
                .fileUrl(fileUrl)
                .fileName(fileName)
                .fileSize(fileSize)
                .fileType(fileType)
                .uploadTime(uploadTime)
                .build();

        // 借鉴 AttachmentServiceImpl 的缓存与存储逻辑
        Attachment attachment = saveAttachment(response, businessType, businessId, uploaderId);

        // 清理资源
        try {
            // 删除 Redis 任务键
            redisTemplate.delete(key);
            // 删除本地临时目录及文件
            deleteDirectory(taskDir);
        } catch (Exception e) {
            log.warn("资源清理失败: taskId={}", taskId, e);
            // 重试机制
            int retryCount = 3;
            for (int i = 0; i < retryCount; i++) {
                try {
                    deleteDirectory(taskDir);
                    redisTemplate.delete(key);
                    break;
                } catch (Exception e) {
                    log.warn("重试资源清理失败: taskId={}, 重试次数={}", taskId, i + 1, e);
                }
            }
        }

        return Result.success(attachment);
    }

    // ================= 私有辅助方法 =================

    /**
     * 计算流的MD5
     */
    private String getMD5(InputStream is) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
            md.update(buffer, 0, read);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDirectory(f);
                }
            }
        }
        file.delete();
    }

    /**
     * 生成 ObjectKey (参考 CommonController)
     */
    private String generateObjectKey(String fileType, String fileExtension) {
        // 格式：type/year/month/day/uuid.ext
        LocalDateTime now = LocalDateTime.now();
        String datePath = DateTimeFormatter.ofPattern("yyyy/MM/dd").format(now);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format("%s/%s/%s%s", fileType, datePath, uuid, fileExtension);
    }

    /**
     * 检测 MIME 类型 (参考 AttachmentServiceImpl 逻辑，简化版)
     */
    private String detectMimeType(String fileName, String fileType) {
        if (fileName == null || !fileName.contains(".")) {
            // 无后缀名，按文件类型返回通用MIME
            switch (fileType) {
                case "image":
                    return "image/unknown";
                case "video":
                    return "video/unknown";
                case "audio":
                    return "audio/unknown";
                default:
                    return "application/octet-stream";
            }
        }
        // 取文件后缀并转小写
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerName.endsWith(".png")) {
            return "image/png";
        }
        if (lowerName.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerName.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lowerName.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (lowerName.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lowerName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerName.endsWith(".txt")) {
            return "text/plain";
        }
        // 匹配不到后缀，按文件类型兜底
        switch (fileType) {
            case "image":
                return "image/unknown";
            case "video":
                return "video/unknown";
            case "audio":
                return "audio/unknown";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * 保存附件逻辑 (借鉴 AttachmentServiceImpl)
     */
    private Attachment saveAttachment(FileUploadResponse fileResponse, String businessType, Long businessId,
            Long uploaderId) {
        LocalDateTime now = LocalDateTime.now();
        // 使用 RedisIdWorker 生成分布式ID
        Long id = redisIdWorker.nextId("attachment");

        Attachment attachment = Attachment.builder()
                .id(id)
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

        // 1. 先写入 Redis 缓存（立即返回，提升响应速度）
        String cacheKeyById = CACHE_KEY_PREFIX_ID + id;
        String cacheKeyByFileId = CACHE_KEY_PREFIX_FILE_ID + fileResponse.getFileId();
        redisCache.setCacheObject(cacheKeyById, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
        redisCache.setCacheObject(cacheKeyByFileId, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
        log.info("附件已写入Redis缓存: id={}, fileId={}", id, fileResponse.getFileId());

        // 2. 异步写入 MySQL（带事务保证）
        CompletableFuture.runAsync(() -> {
            try {
                transactionTemplate.execute(status -> {
                    try {
                        saveAttachmentToDatabase(attachment);
                        log.info("附件异步写入MySQL成功: id={}, fileId={}", attachment.getId(), fileResponse.getFileId());
                        return null;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        throw new RuntimeException("附件异步写入MySQL失败: fileId=" + fileResponse.getFileId(), e);
                    }
                });
            } catch (Exception e) {
                log.error("附件异步写入MySQL失败: fileId={}", fileResponse.getFileId(), e);
            }
        }, threadPoolExecutor);

        return attachment;
    }

    /**
     * 同步保存到数据库并清除关联缓存
     */
    private void saveAttachmentToDatabase(Attachment attachment) {
        attachmentMapper.insert(attachment);
        // 清除相关列表缓存
        invalidateBusinessCache(attachment.getBusinessType(), attachment.getBusinessId());
        invalidateUploaderCache(attachment.getUploaderId());
    }

    /**
     * 清除业务列表缓存
     */
    private void invalidateBusinessCache(String businessType, Long businessId) {
        if (businessType != null && businessId != null) {
            String cacheKey = CACHE_KEY_PREFIX_BUSINESS + businessType + ":" + businessId;
            redisCache.deleteObject(cacheKey);
        }
    }

    /**
     * 清除上传者相关缓存
     */
    private void invalidateUploaderCache(Long uploaderId) {
        if (uploaderId != null) {
            String cacheKey = CACHE_KEY_PREFIX_UPLOADER + uploaderId;
            redisCache.deleteObject(cacheKey);
        }
    }
}
