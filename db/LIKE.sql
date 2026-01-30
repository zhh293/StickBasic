create table likes
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                              not null,
    target_type enum ('post', 'comment')            not null,
    target_id   bigint                              not null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    constraint uk_user_target
        unique (user_id, target_type, target_id)
);

create index idx_target
    on likes (target_type, target_id);

create index idx_user_id
    on likes (user_id);


