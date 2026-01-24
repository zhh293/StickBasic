package com.tmd.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.tmd.config.RedisCache;
import com.tmd.entity.dto.AliOssUtil;
import com.tmd.entity.dto.Attachment;
import com.tmd.entity.dto.FileUploadResponse;
import com.tmd.mapper.AttachmentMapper;
import com.tmd.service.AttachmentService;
import com.tmd.tools.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    @Autowired
    private AttachmentMapper attachmentMapper;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RedisIdWorker redisIdWorker;

    // Redis Key 前缀
    private static final String CACHE_KEY_PREFIX_ID = "attachment:id:";
    private static final String CACHE_KEY_PREFIX_FILE_ID = "attachment:fileId:";
    private static final String CACHE_KEY_PREFIX_BUSINESS = "attachment:business:";
    private static final String CACHE_KEY_PREFIX_UPLOADER = "attachment:uploader:";

    // 缓存过期时间：7天
    private static final int CACHE_EXPIRE_DAYS = 7;

    @Override
    public Attachment saveAttachment(FileUploadResponse fileResponse, String businessType, Long businessId,
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

        // 先写入 Redis 缓存（立即返回，提升响应速度）
        String cacheKeyById = CACHE_KEY_PREFIX_ID + id;
        String cacheKeyByFileId = CACHE_KEY_PREFIX_FILE_ID + fileResponse.getFileId();
        redisCache.setCacheObject(cacheKeyById, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
        redisCache.setCacheObject(cacheKeyByFileId, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
        log.info("附件已写入Redis缓存: id={}, fileId={}", id, fileResponse.getFileId());

        // 异步写入 MySQL（带事务保证）
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
                // 如果异步写入失败，可以考虑重试机制或记录到失败队列
            }
        }, threadPoolExecutor);

        // 返回带ID的附件对象（ID已通过RedisIdWorker生成）
        return attachment;
    }

    /**
     * 同步保存附件到数据库（不带事务注解，由调用方通过TransactionTemplate管理事务）
     */
    public void saveAttachmentToDatabase(Attachment attachment) {
        attachmentMapper.insert(attachment);
        // 清除相关列表缓存（缓存已在写入时更新）
        invalidateBusinessCache(attachment.getBusinessType(), attachment.getBusinessId());
        invalidateUploaderCache(attachment.getUploaderId());
    }

    @Override
    public List<Attachment> batchSaveAttachments(List<FileUploadResponse> fileResponses, String businessType,
            Long businessId, Long uploaderId) {
        if (fileResponses == null || fileResponses.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDateTime now = LocalDateTime.now();
        List<Attachment> attachments = fileResponses.stream()
                .map(fileResponse -> {
                    // 使用 RedisIdWorker 生成分布式ID
                    Long id = redisIdWorker.nextId("attachment");
                    return Attachment.builder()
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
                })
                .collect(Collectors.toList());

        // 先批量写入 Redis 缓存（立即返回，提升响应速度）
        for (Attachment attachment : attachments) {
            String cacheKeyById = CACHE_KEY_PREFIX_ID + attachment.getId();
            String cacheKeyByFileId = CACHE_KEY_PREFIX_FILE_ID + attachment.getFileId();
            redisCache.setCacheObject(cacheKeyById, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            redisCache.setCacheObject(cacheKeyByFileId, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
        }
        log.info("批量附件已写入Redis缓存: count={}", attachments.size());

        // 异步批量写入 MySQL（带事务保证）
        CompletableFuture.runAsync(() -> {
            try {
                transactionTemplate.execute(status -> {
                    try {
                        batchSaveAttachmentsToDatabase(attachments);
                        log.info("批量附件异步写入MySQL成功: count={}, businessType={}, businessId={}",
                                attachments.size(), businessType, businessId);
                        return null;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        throw new RuntimeException("批量附件异步写入MySQL失败: businessType=" + businessType
                                + ", businessId=" + businessId, e);
                    }
                });
            } catch (Exception e) {
                log.error("批量附件异步写入MySQL失败: businessType={}, businessId={}", businessType, businessId, e);
            }
        }, threadPoolExecutor);

        return attachments;
    }

    /**
     * 同步批量保存附件到数据库（不带事务注解，由调用方通过TransactionTemplate管理事务）
     */
    public void batchSaveAttachmentsToDatabase(List<Attachment> attachments) {
        attachmentMapper.batchInsert(attachments);
        // 清除相关列表缓存（缓存已在写入时更新）
        if (!attachments.isEmpty()) {
            Attachment first = attachments.get(0);
            invalidateBusinessCache(first.getBusinessType(), first.getBusinessId());
            invalidateUploaderCache(first.getUploaderId());
        }
    }

    @Override
    public Attachment getAttachmentById(Long id) {
        if (id == null) {
            return null;
        }

        // 先查 Redis 缓存
        String cacheKey = CACHE_KEY_PREFIX_ID + id;
        Object cacheObject = redisCache.getCacheObject(cacheKey);
        Attachment attachment = JSONObject.parseObject(cacheObject.toString(), Attachment.class);
        if (attachment != null) {
            log.debug("从Redis缓存获取附件: id={}", id);
            return attachment;
        }

        // 缓存未命中，查 MySQL
        attachment = attachmentMapper.selectById(id);
        if (attachment != null) {
            // 写入缓存
            redisCache.setCacheObject(cacheKey, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            // 同时缓存 fileId 索引
            String cacheKeyByFileId = CACHE_KEY_PREFIX_FILE_ID + attachment.getFileId();
            redisCache.setCacheObject(cacheKeyByFileId, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            log.debug("从MySQL获取附件并写入缓存: id={}", id);
        }
        return attachment;
    }

    @Override
    public List<Attachment> getAttachmentsByBusiness(String businessType, Long businessId) {
        if (businessType == null || businessId == null) {
            return new ArrayList<>();
        }

        // 先查 Redis 缓存
        String cacheKey = CACHE_KEY_PREFIX_BUSINESS + businessType + ":" + businessId;
        List<Object> cacheList = redisCache.getCacheList(cacheKey);
        List<Attachment> attachments = cacheList.stream()
                .map(json -> JSONObject.parseObject(json.toString(), Attachment.class))
                .toList();
        if (attachments != null && !attachments.isEmpty()) {
            log.debug("从Redis缓存获取业务附件列表: businessType={}, businessId={}", businessType, businessId);
            return attachments;
        }

        log.error("getAttachmentsByBusiness方法从Redis缓存未命中，开始查询业务附件列表: businessType={}, businessId={}", businessType, businessId);
        // 缓存未命中，查 MySQL
        attachments = attachmentMapper.selectByBusiness(businessType, businessId);
        if (attachments != null && !attachments.isEmpty()) {
            // 写入缓存
            redisCache.setCacheList(cacheKey, attachments);
            redisCache.expire(cacheKey, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            // 同时缓存每个附件对象
            for (Attachment attachment : attachments) {
                if (attachment.getId() != null) {
                    String cacheKeyById = CACHE_KEY_PREFIX_ID + attachment.getId();
                    redisCache.setCacheObject(cacheKeyById, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
                }
                String cacheKeyByFileId = CACHE_KEY_PREFIX_FILE_ID + attachment.getFileId();
                redisCache.setCacheObject(cacheKeyByFileId, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            }
            log.info("从MySQL获取业务附件列表并写入缓存: businessType={}, businessId={}, count={}",
                    businessType, businessId, attachments.size());
        }
        return attachments != null ? attachments : new ArrayList<>();
    }

    @Override
    public List<Attachment> getAttachmentsByBusinessBatch(String businessType, List<Long> businessIds) {
        if (businessType == null || businessIds == null || businessIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 去重并过滤空ID
        java.util.List<Long> ids = businessIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        log.error("第一部开始查询");
        // 先尝试从缓存读取各 businessId 的附件列表
        java.util.List<Attachment> combined = new java.util.ArrayList<>();
        java.util.List<Long> missed = new java.util.ArrayList<>();
        for (Long bid : ids) {
            String cacheKey = CACHE_KEY_PREFIX_BUSINESS + businessType + ":" + bid;
            List<Object> cacheList = redisCache.getCacheList(cacheKey);
            //里面都是JSONOBJECT对象，需要转化为Attachment对象
            List<Attachment> cached = cacheList.stream()
                    .map(json -> JSONObject.parseObject(json.toString(), Attachment.class))
                    .toList();
            // 注意：即便缓存是空列表，也视为命中，避免穿透
            if (cached != null && !cached.isEmpty()) {
                combined.addAll(cached);
            } else {
                missed.add(bid);
            }
        }

        log.error("getAttachmentsByBusinessBatch方法从Redis缓存未命中，开始查询业务附件列表: businessType={}, businessIds={}", businessType, businessIds);
        // 对缓存未命中的 businessId，一次性查库并回填缓存
        if (!missed.isEmpty()) {
            java.util.List<Attachment> fetched = attachmentMapper.selectByBusinessIds(businessType, missed);
            log.error("从MySQL获取业务附件列表: businessType={}, businessIds={}",
                    businessType, missed);
            if (fetched != null && !fetched.isEmpty()) {
                java.util.Map<Long, java.util.List<Attachment>> grouped = fetched.stream()
                        .filter(a -> a.getBusinessId() != null)
                        .collect(java.util.stream.Collectors.groupingBy(Attachment::getBusinessId));
                log.error("归类完毕之后帖子与图片关系为{}",grouped);
                for (Long bid : missed) {
                    log.error("开始为业务ID{}的附件列表写入缓存",bid);
                    java.util.List<Attachment> group = grouped.getOrDefault(bid, new java.util.ArrayList<>());
                    String cacheKey = CACHE_KEY_PREFIX_BUSINESS + businessType + ":" + bid;
                    try {
                        redisCache.setCacheList(cacheKey, group);
                        // 缓存 TTL（与单条方法保持一致）
                        redisCache.expire(cacheKey, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
                    } catch (Exception ignore) {}
                    combined.addAll(group);
                }
            } else {
                // 防止穿透：为所有 missed 写入空列表缓存（较短过期）
                log.error("getAttachmentsByBusinessBatch方法从MySQL未命中，写入空列表缓存: businessType={}, businessIds={}",
                        businessType, missed);
                for (Long bid : missed) {
                    String cacheKey = CACHE_KEY_PREFIX_BUSINESS + businessType + ":" + bid;
                    try {
                        redisCache.setCacheList(cacheKey, new java.util.ArrayList<>());
                        redisCache.expire(cacheKey, 1, TimeUnit.HOURS);
                    } catch (Exception ignore) {}
                }
            }
        }
        log.error("getAttachmentsByBusinessBatch方法最后要返回了:");

        return combined;
    }

    @Override
    public Attachment getAttachmentByFileId(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return null;
        }

        // 先查 Redis 缓存
        String cacheKey = CACHE_KEY_PREFIX_FILE_ID + fileId;
        Object cacheObject = redisCache.getCacheObject(cacheKey);
        Attachment attachment = JSONObject.parseObject(cacheObject.toString(), Attachment.class);
        if (attachment != null) {
            log.debug("从Redis缓存获取附件: fileId={}", fileId);
            return attachment;
        }

        // 缓存未命中，查 MySQL
        attachment = attachmentMapper.selectByFileId(fileId);
        if (attachment != null) {
            // 写入缓存
            redisCache.setCacheObject(cacheKey, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            // 同时缓存 id 索引
            if (attachment.getId() != null) {
                String cacheKeyById = CACHE_KEY_PREFIX_ID + attachment.getId();
                redisCache.setCacheObject(cacheKeyById, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            }
            log.debug("从MySQL获取附件并写入缓存: fileId={}", fileId);
        }
        return attachment;
    }

    @Override
    public List<Attachment> getAttachmentsByUploaderId(Long uploaderId) {
        if (uploaderId == null) {
            return new ArrayList<>();
        }

        // 先查 Redis 缓存
        String cacheKey = CACHE_KEY_PREFIX_UPLOADER + uploaderId;
        List<Object> cacheList = redisCache.getCacheList(cacheKey);
        List<Attachment> attachments = cacheList.stream()
                .map(json -> JSONObject.parseObject(json.toString(), Attachment.class))
                .toList();
        if (attachments != null && !attachments.isEmpty()) {
            log.debug("从Redis缓存获取上传者附件列表: uploaderId={}", uploaderId);
            return attachments;
        }

        // 缓存未命中，查 MySQL
        attachments = attachmentMapper.selectByUploaderId(uploaderId);
        if (attachments != null && !attachments.isEmpty()) {
            // 写入缓存
            redisCache.setCacheList(cacheKey, attachments);
            redisCache.expire(cacheKey, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            // 同时缓存每个附件对象
            for (Attachment attachment : attachments) {
                if (attachment.getId() != null) {
                    String cacheKeyById = CACHE_KEY_PREFIX_ID + attachment.getId();
                    redisCache.setCacheObject(cacheKeyById, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
                }
                String cacheKeyByFileId = CACHE_KEY_PREFIX_FILE_ID + attachment.getFileId();
                redisCache.setCacheObject(cacheKeyByFileId, attachment, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            }
            log.debug("从MySQL获取上传者附件列表并写入缓存: uploaderId={}, count={}", uploaderId, attachments.size());
        } else {
            // 防止缓存穿透：缓存空结果，但过期时间较短
            redisCache.setCacheList(cacheKey, new ArrayList<>());
            redisCache.expire(cacheKey, 1, TimeUnit.HOURS);
        }
        return attachments != null ? attachments : new ArrayList<>();
    }

    @Override
    public boolean deleteAttachment(Long id) {
        if (id == null) {
            return false;
        }

        // 先查缓存获取附件信息
        Attachment attachment = getAttachmentById(id);
        if (attachment == null) {
            log.warn("附件不存在: id={}", id);
            return false;
        }

        // 立即删除 Redis 缓存
        deleteAttachmentCache(id, attachment.getFileId(), attachment.getBusinessType(),
                attachment.getBusinessId(), attachment.getUploaderId());
        log.info("附件缓存已删除: id={}, fileId={}", id, attachment.getFileId());

        // 异步删除 MySQL 和 OSS（带事务保证）
        CompletableFuture.runAsync(() -> {
            try {
                transactionTemplate.execute(status -> {
                    try {
                        deleteAttachmentFromDatabaseAndOSS(id, attachment.getFileId());
                        log.info("附件异步删除MySQL和OSS成功: id={}, fileId={}", id, attachment.getFileId());
                        return null;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        throw new RuntimeException("附件异步删除MySQL和OSS失败: id=" + id
                                + ", fileId=" + attachment.getFileId(), e);
                    }
                });
            } catch (Exception e) {
                log.error("附件异步删除MySQL和OSS失败: id={}, fileId={}", id, attachment.getFileId(), e);
            }
        }, threadPoolExecutor);

        return true;
    }

    /**
     * 同步删除附件（数据库和OSS，不带事务注解，由调用方通过TransactionTemplate管理事务）
     */
    public void deleteAttachmentFromDatabaseAndOSS(Long id, String fileId) {
        // 删除OSS文件
        try {
            boolean deleted = aliOssUtil.delete(fileId);
            if (!deleted) {
                log.warn("OSS文件删除失败: fileId={}", fileId);
            }
        } catch (Exception e) {
            log.error("删除OSS文件异常: fileId={}", fileId, e);
            // OSS删除失败不影响数据库删除，继续执行
        }

        // 删除数据库记录
        attachmentMapper.deleteById(id);
    }

    @Override
    public boolean deleteAttachmentsByBusiness(String businessType, Long businessId) {
        if (businessType == null || businessId == null) {
            return false;
        }

        // 先查缓存获取附件列表
        List<Attachment> attachments = getAttachmentsByBusiness(businessType, businessId);
        if (attachments == null || attachments.isEmpty()) {
            log.warn("业务附件不存在: businessType={}, businessId={}", businessType, businessId);
            return false;
        }

        // 立即删除 Redis 缓存
        String businessCacheKey = CACHE_KEY_PREFIX_BUSINESS + businessType + ":" + businessId;
        redisCache.deleteObject(businessCacheKey);

        for (Attachment attachment : attachments) {
            if (attachment.getId() != null) {
                String cacheKeyById = CACHE_KEY_PREFIX_ID + attachment.getId();
                redisCache.deleteObject(cacheKeyById);
            }
            String cacheKeyByFileId = CACHE_KEY_PREFIX_FILE_ID + attachment.getFileId();
            redisCache.deleteObject(cacheKeyByFileId);

            // 清除上传者缓存（可能受影响）
            if (attachment.getUploaderId() != null) {
                invalidateUploaderCache(attachment.getUploaderId());
            }
        }
        log.info("业务附件缓存已删除: businessType={}, businessId={}, count={}",
                businessType, businessId, attachments.size());

        // 异步删除 MySQL 和 OSS（带事务保证）
        CompletableFuture.runAsync(() -> {
            try {
                transactionTemplate.execute(status -> {
                    try {
                        deleteAttachmentsByBusinessFromDatabaseAndOSS(businessType, businessId, attachments);
                        log.info("业务附件异步删除MySQL和OSS成功: businessType={}, businessId={}, count={}",
                                businessType, businessId, attachments.size());
                        return null;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        throw new RuntimeException("业务附件异步删除MySQL和OSS失败: businessType=" + businessType
                                + ", businessId=" + businessId, e);
                    }
                });
            } catch (Exception e) {
                log.error("业务附件异步删除MySQL和OSS失败: businessType={}, businessId={}",
                        businessType, businessId, e);
            }
        }, threadPoolExecutor);

        return true;
    }

    /**
     * 同步批量删除业务附件（数据库和OSS，不带事务注解，由调用方通过TransactionTemplate管理事务）
     */
    public void deleteAttachmentsByBusinessFromDatabaseAndOSS(String businessType, Long businessId,
            List<Attachment> attachments) {
        // 删除所有OSS文件
        for (Attachment attachment : attachments) {
            try {
                aliOssUtil.delete(attachment.getFileId());
            } catch (Exception e) {
                log.warn("删除OSS文件失败: fileId={}", attachment.getFileId(), e);
                // 继续删除其他文件
            }
        }

        // 删除数据库记录
        attachmentMapper.deleteByBusiness(businessType, businessId);
    }

    @Override
    public boolean deleteAttachmentByFileId(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return false;
        }

        // 先查缓存获取附件信息
        Attachment attachment = getAttachmentByFileId(fileId);
        if (attachment == null) {
            log.warn("附件不存在: fileId={}", fileId);
            return false;
        }

        // 立即删除 Redis 缓存
        deleteAttachmentCache(attachment.getId(), fileId, attachment.getBusinessType(),
                attachment.getBusinessId(), attachment.getUploaderId());
        log.info("附件缓存已删除: fileId={}", fileId);

        // 异步删除 MySQL 和 OSS（带事务保证）
        CompletableFuture.runAsync(() -> {
            try {
                transactionTemplate.execute(status -> {
                    try {
                        deleteAttachmentByFileIdFromDatabaseAndOSS(fileId);
                        log.info("附件异步删除MySQL和OSS成功: fileId={}", fileId);
                        return null;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        throw new RuntimeException("附件异步删除MySQL和OSS失败: fileId=" + fileId, e);
                    }
                });
            } catch (Exception e) {
                log.error("附件异步删除MySQL和OSS失败: fileId={}", fileId, e);
            }
        }, threadPoolExecutor);

        return true;
    }

    /**
     * 同步根据fileId删除附件（数据库和OSS，不带事务注解，由调用方通过TransactionTemplate管理事务）
     */
    public void deleteAttachmentByFileIdFromDatabaseAndOSS(String fileId) {
        // 删除OSS文件
        try {
            boolean deleted = aliOssUtil.delete(fileId);
            if (!deleted) {
                log.warn("OSS文件删除失败: fileId={}", fileId);
            }
        } catch (Exception e) {
            log.error("删除OSS文件异常: fileId={}", fileId, e);
            // OSS删除失败不影响数据库删除，继续执行
        }

        // 删除数据库记录
        attachmentMapper.deleteByFileId(fileId);
    }

    /**
     * 删除附件相关的所有缓存
     */
    private void deleteAttachmentCache(Long id, String fileId, String businessType,
            Long businessId, Long uploaderId) {
        if (id != null) {
            String cacheKeyById = CACHE_KEY_PREFIX_ID + id;
            redisCache.deleteObject(cacheKeyById);
        }
        if (fileId != null) {
            String cacheKeyByFileId = CACHE_KEY_PREFIX_FILE_ID + fileId;
            redisCache.deleteObject(cacheKeyByFileId);
        }
        if (businessType != null && businessId != null) {
            invalidateBusinessCache(businessType, businessId);
        }
        if (uploaderId != null) {
            invalidateUploaderCache(uploaderId);
        }
    }

    /**
     * 失效业务附件列表缓存
     */
    private void invalidateBusinessCache(String businessType, Long businessId) {
        if (businessType != null && businessId != null) {
            String cacheKey = CACHE_KEY_PREFIX_BUSINESS + businessType + ":" + businessId;
            redisCache.deleteObject(cacheKey);
        }
    }

    /**
     * 失效上传者附件列表缓存
     */
    private void invalidateUploaderCache(Long uploaderId) {
        if (uploaderId != null) {
            String cacheKey = CACHE_KEY_PREFIX_UPLOADER + uploaderId;
            redisCache.deleteObject(cacheKey);
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
