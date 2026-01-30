create table users
(
    id                  bigint auto_increment
        primary key,
    username            varchar(50)                                                    not null,
    password            varchar(255)                                                   not null,
    email               varchar(100)                                                   null,
    avatar              varchar(255)                                                   null,
    daily_bookmark      varchar(500)                                                   null,
    homepage_background varchar(255)                                                   null,
    account_days        int                                  default 0                 null,
    personal_signature  varchar(200)                                                   null,
    status              enum ('active', 'banned', 'deleted') default 'active'          null,
    deleted_at          timestamp                                                      null,
    created_at          timestamp                            default CURRENT_TIMESTAMP null,
    updated_at          timestamp                            default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    nickname            varchar(50)                                                    null,
    constraint username
        unique (username)
);


