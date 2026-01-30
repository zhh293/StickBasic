create table user_behavior_log
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                              not null,
    action_type varchar(50)                         not null,
    target_type varchar(50)                         null,
    target_id   bigint                              null,
    ip_address  varchar(45)                         null,
    user_agent  text                                null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    constraint user_behavior_log_ibfk_1
        foreign key (user_id) references users (id)
);

create index idx_target
    on user_behavior_log (target_type, target_id);

create index idx_user_action
    on user_behavior_log (user_id, action_type);


