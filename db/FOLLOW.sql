create table follow
(
    id           bigint auto_increment
        primary key,
    follower_id  bigint                              not null,
    following_id bigint                              not null,
    created_at   timestamp default CURRENT_TIMESTAMP null,
    constraint uk_follow
        unique (follower_id, following_id)
);

create index idx_follower_id
    on follow (follower_id);

create index idx_following_id
    on follow (following_id);


