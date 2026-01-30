create table mail
(
    id              bigint auto_increment
        primary key,
    sender_id       bigint                                                       not null,
    stamp_type      varchar(50)                                                  not null,
    stamp_content   varchar(500)                                                 null,
    sender_nickname varchar(50)                                                  not null,
    recipient_email varchar(100)                                                 not null,
    content         text                                                         not null,
    status          enum ('sent', 'delivered', 'read') default 'sent'            null,
    read_at         timestamp                                                    null,
    created_at      timestamp                          default CURRENT_TIMESTAMP null,
    constraint mail_ibfk_1
        foreign key (sender_id) references users (id)
);

create index sender_id
    on mail (sender_id);


