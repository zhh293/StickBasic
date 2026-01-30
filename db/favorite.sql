create table favorite
(
    id         bigint auto_increment
        primary key,
    user_id    bigint                              not null,
    post_id    bigint                              not null,
    created_at timestamp default CURRENT_TIMESTAMP null,
    constraint uk_user_post
        unique (user_id, post_id)
);

create index idx_post_id
    on favorite (post_id);

create index idx_user_id
    on favorite (user_id);


