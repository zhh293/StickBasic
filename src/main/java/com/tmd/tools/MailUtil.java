package com.tmd.tools;

import cn.hutool.extra.mail.MailAccount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.File;

@Data
@AllArgsConstructor
@Builder
public class MailUtil { // 注意：类名和Hutool的MailUtil重名，建议改个名（比如CustomMailUtil），避免歧义
    private String host;
    private String port;
    private String username;
    private String password;
    private String from;
    private Boolean sslEnabled;
    private Boolean debug; // 建议改小写，和MailProperties统一，@Data生成的getter还是getDebug()
    private Boolean starttlsEnable;

    // 发送邮件的核心方法（内部用Hutool的MailAccount）
    public void sendMail(String to, String subject, String content, File file) {
        MailAccount mailAccount = new MailAccount();
        // 这里的参数映射要和自定义MailUtil的属性对应，之前是对的
        mailAccount.setHost(host);
        mailAccount.setPort(Integer.valueOf(port)); // String转int，没问题
        mailAccount.setFrom(from);
        mailAccount.setUser(username);
        mailAccount.setPass(password);
        mailAccount.setStarttlsEnable(starttlsEnable);
        mailAccount.setDebug(debug);
        mailAccount.setSslEnable(sslEnabled);

        // 调用Hutool的静态MailUtil发送（注意：如果自定义类名和Hutool重名，这里要写全类名）
        // 参数 false 表示不是HTML格式
        cn.hutool.extra.mail.MailUtil.send(mailAccount, to, subject, content, false, file);
    }

    /**
     * 发送HTML格式的邮件
     * 
     * @param to          收件人邮箱
     * @param subject     邮件主题
     * @param htmlContent HTML格式的邮件内容
     * @param file        附件（可选，传null表示无附件）
     */
    public void sendHtmlMail(String to, String subject, String htmlContent, File file) {
        MailAccount mailAccount = new MailAccount();
        mailAccount.setHost(host);
        mailAccount.setPort(Integer.valueOf(port));
        mailAccount.setFrom(from);
        mailAccount.setUser(username);
        mailAccount.setPass(password);
        mailAccount.setStarttlsEnable(starttlsEnable);
        mailAccount.setDebug(debug);
        mailAccount.setSslEnable(sslEnabled);

        // 调用Hutool的静态MailUtil发送，参数 true 表示内容是HTML格式
        cn.hutool.extra.mail.MailUtil.send(mailAccount, to, subject, htmlContent, true, file);
    }
}
