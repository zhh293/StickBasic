create table system_config
(
    id           bigint auto_increment
        primary key,
    config_key   varchar(100)                                                           not null,
    config_value text                                                                   null,
    description  varchar(500)                                                           null,
    config_type  enum ('string', 'number', 'boolean', 'json') default 'string'          null,
    updated_at   timestamp                                    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint config_key
        unique (config_key)
);


