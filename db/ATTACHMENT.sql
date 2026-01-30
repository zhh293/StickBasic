create table attachment
(
    id            bigint auto_increment
        primary key,
    file_id       varchar(500)                        not null comment 'OSS objectKey',
    file_url      varchar(500)                        not null comment '文件访问URL',
    file_name     varchar(255)                        null comment '原始文件名',
    file_size     bigint                              null comment '文件大小（字节）',
    file_type     varchar(50)                         not null comment '文件类型：image, video, audio, document',
    mime_type     varchar(100)                        null comment 'MIME类型，如 image/jpeg, video/mp4',
    business_type varchar(50)                         not null comment '业务类型：post, mail, user, topic等',
    business_id   bigint                              null comment '业务对象ID（如帖子ID、邮件ID等）',
    uploader_id   bigint                              not null comment '上传者用户ID',
    upload_time   timestamp default CURRENT_TIMESTAMP null comment '上传时间',
    created_at    timestamp default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at    timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '通用附件表';

create index idx_business
    on attachment (business_type, business_id);

create index idx_file_id
    on attachment (file_id);

create index idx_upload_time
    on attachment (upload_time);

create index idx_uploader
    on attachment (uploader_id);


