create table sticks
(
    id         bigint auto_increment comment '主键ID'
        primary key,
    user_id    bigint                                  not null comment '用户ID',
    content    varchar(255) collate utf8mb4_general_ci not null comment '内容',
    created_at datetime(6)                             null comment '创建时间',
    updated_at datetime(6)                             null comment '更新时间'
)
    comment '公共便签表';


