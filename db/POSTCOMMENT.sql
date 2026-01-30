create table post_comment
(
    id           bigint auto_increment
        primary key,
    post_id      bigint                              not null,
    commenter_id bigint                              not null,
    content      text                                not null,
    created_at   timestamp default CURRENT_TIMESTAMP null,
    root_id      bigint                              null,
    parent_id    bigint                              null,
    likes        int       default 0                 null,
    dislikes     int       default 0                 null,
    reply_count  int       default 0                 null
)
    comment '帖子评论表';


