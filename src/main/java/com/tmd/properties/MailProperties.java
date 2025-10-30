package com.tmd.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mail")
@Data
public class MailProperties {
    private String host;      // 对应 mail.host
    private int port;         // 对应 mail.port（已改为int，避免转义）
    private String user;      // 对应 mail.user（不是username！）
    private String pass;      // 对应 mail.pass（不是password！）
    private String from;      // 对应 mail.from
    private Boolean sslEnabled; // 对应 mail.ssl.enable
    private Boolean debug;    // 对应 mail.debug
    private Boolean starttlsEnable; // 对应 mail.starttls.enable
}
