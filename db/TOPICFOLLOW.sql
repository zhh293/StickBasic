create table topic_follow
(
    id         bigint auto_increment
        primary key,
    user_id    bigint                              not null,
    topic_id   bigint                              not null,
    created_at timestamp default CURRENT_TIMESTAMP null,
    constraint uk_user_topic
        unique (user_id, topic_id)
);


