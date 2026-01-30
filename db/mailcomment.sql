create table mail_comment
(
    id           bigint auto_increment
        primary key,
    mail_id      bigint                              not null,
    commenter_id bigint                              not null,
    content      text                                not null,
    created_at   timestamp default CURRENT_TIMESTAMP null,
    constraint mail_comment_ibfk_1
        foreign key (mail_id) references mail (id),
    constraint mail_comment_ibfk_2
        foreign key (commenter_id) references users (id)
);

create index commenter_id
    on mail_comment (commenter_id);

create index mail_id
    on mail_comment (mail_id);


