-- 通用附件表
CREATE TABLE IF NOT EXISTS attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_id VARCHAR(500) NOT NULL COMMENT 'OSS objectKey',
    file_url VARCHAR(500) NOT NULL COMMENT '文件访问URL',
    file_name VARCHAR(255) COMMENT '原始文件名',
    file_size BIGINT COMMENT '文件大小（字节）',
    file_type VARCHAR(50) NOT NULL COMMENT '文件类型：image, video, audio, document',
    mime_type VARCHAR(100) COMMENT 'MIME类型，如 image/jpeg, video/mp4',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型：post, mail, user, topic等',
    business_id BIGINT COMMENT '业务对象ID（如帖子ID、邮件ID等）',
    uploader_id BIGINT NOT NULL COMMENT '上传者用户ID',
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_business (business_type, business_id),
    INDEX idx_file_id (file_id),
    INDEX idx_uploader (uploader_id),
    INDEX idx_upload_time (upload_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用附件表';

