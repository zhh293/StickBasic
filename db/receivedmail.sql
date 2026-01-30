create table received_mail
(
    id               bigint auto_increment
        primary key,
    recipient_id     bigint                                            not null,
    sender_id        bigint                                            not null,
    content          text                                              not null,
    stamp_type       varchar(50)                                       not null,
    sender_nickname  varchar(50)                                       not null,
    original_mail_id bigint                                            null,
    status           enum ('unread', 'read') default 'unread'          null,
    read_at          timestamp                                         null,
    created_at       timestamp               default CURRENT_TIMESTAMP null,
    constraint received_mail_ibfk_1
        foreign key (recipient_id) references users (id),
    constraint received_mail_ibfk_2
        foreign key (sender_id) references users (id),
    constraint received_mail_ibfk_3
        foreign key (original_mail_id) references mail (id)
);

create index original_mail_id
    on received_mail (original_mail_id);

create index recipient_id
    on received_mail (recipient_id);

create index sender_id
    on received_mail (sender_id);


