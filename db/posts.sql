create table posts
(
    id               bigint auto_increment
        primary key,
    user_id          bigint                                                           not null,
    topic_id         bigint                                                           null,
    title            varchar(200)                                                     not null,
    content          text                                                             not null,
    post_type        enum ('story', 'daily_sign')                                     not null,
    status           enum ('draft', 'published', 'deleted') default 'published'       null,
    like_count       int                                    default 0                 null,
    collect_count    int                                    default 0                 null,
    comment_count    int                                    default 0                 null,
    share_count      int                                    default 0                 null,
    view_count       int                                    default 0                 null,
    publish_location varchar(100)                                                     null,
    latitude         decimal(10, 8)                                                   null,
    longitude        decimal(11, 8)                                                   null,
    deleted_at       timestamp                                                        null,
    created_at       timestamp                              default CURRENT_TIMESTAMP null,
    updated_at       timestamp                              default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
);

create index idx_posts_topic_status_created
    on posts (topic_id, status, created_at);

create index idx_posts_type_status_created_at
    on posts (post_type asc, status asc, created_at desc);

create index idx_topic_id
    on posts (topic_id);

create index idx_user_id
    on posts (user_id);


