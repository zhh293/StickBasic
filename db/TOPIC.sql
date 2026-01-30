create table topic
(
    id             bigint auto_increment
        primary key,
    name           varchar(100)                                                       not null,
    description    text                                                               null,
    cover_image    varchar(255)                                                       null,
    post_count     int                                      default 0                 null,
    follower_count int                                      default 0                 null,
    status         enum ('pending', 'approved', 'rejected') default 'pending'         null,
    user_id        bigint                                                             null,
    created_at     timestamp                                default CURRENT_TIMESTAMP null,
    updated_at     timestamp                                default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
);

create index created_by
    on topic (user_id);


