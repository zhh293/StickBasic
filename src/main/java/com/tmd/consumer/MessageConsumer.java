package com.tmd.consumer;

import com.rabbitmq.client.Channel;

import cn.hutool.core.util.StrUtil;
import com.tmd.WebSocket.WebSocketServer;
import com.tmd.config.RabbitMQConfig;
import com.tmd.entity.dto.AliOssUtil;
import com.tmd.entity.dto.MailDTO;
import com.tmd.publisher.MessageDTO;
import com.tmd.tools.MailUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class MessageConsumer {

    @Autowired
    private ChatClient summaryClient;

    @Autowired
    private MailUtil mailUtil;
    @Autowired
    private AliOssUtil aliOssUtil;
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 从URL下载图片内容
     * 
     * @param imageUrl 图片URL
     * @return 图片字节数组
     */
    private byte[] downloadImage(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream in = connection.getInputStream()) {
                    return in.readAllBytes();
                }
            } else {
                log.error("下载图片失败，HTTP状态码: {}", responseCode);
            }
        } catch (Exception e) {
            log.error("下载图片异常: {}", e.getMessage(), e);
        }
        return null;
    }

    @RabbitListener(queues = RabbitMQConfig.DIRECT_QUEUE_1)
    public void consumeDirectQueue1(MessageDTO message, Channel channel, Message amqpMessage) throws IOException {
        try {
            log.info("【Direct队列1】收到消息: {}", message);

            // 处理业务逻辑...
            Object content = message.getContent();

            // 处理MailDTO类型消息（邮件发送）
            if (content instanceof MailDTO) {
                MailDTO mailDTO = (MailDTO) content;
                String htmlContent = generateHtmlEmail(mailDTO);
                String subject = "一封来自 " + (mailDTO.getSenderNickname() != null ? mailDTO.getSenderNickname() : "朋友")
                        + " 的邮件";
                
                // 使用 MailUtil 发送HTML格式邮件
                mailUtil.sendHtmlMail(mailDTO.getRecipientEmail(), subject, htmlContent, null);
                log.info("邮件已成功发送到: {}", mailDTO.getRecipientEmail());
            }
            // 处理String类型消息（AI摘要等）
            else if (content instanceof String) {
                String string = (String) content;
                log.info("directQueue1接收到消息: {}", string);
                // 调用AI生成摘要
                String response = summaryClient.prompt()
                        .user(string)
                        .call()
                        .content();
                log.info("摘要生成完成: {}", response);
            }
            // 处理List类型消息（摘要生成）
            else if (content instanceof List<?>) {
                String response = summaryClient.prompt()
                        .user(content.toString())
                        .call()
                        .content();
                log.info(response);
                if (!webSocketServer.Open(message.getId())) {
                    throw new RuntimeException("用户未连接websocket");
                }
                if (message.getId() != null) {
                    webSocketServer.sendToUser(message.getId(), response);
                    webSocketServer.sendToUser(message.getId(), "[END]");
                    log.info("[END]");
                }
            }
            // 处理Long类型消息（用户ID，用于生成书签）
            else if (content instanceof Long) {
                Long userId = (Long) content;
                log.info("开始为用户生成书签: userId={}", userId);
                try {
                    // 这里应该是调用大模型生成书签图片URL的代码
                    // 由于模型调用不需要实现，这里模拟生成一个图片URL
                    String imageUrl = "https://example.com/generated-bookmark.jpg"; // 模拟的生成图片URL

                    // 下载图片内容
                    byte[] imageBytes = downloadImage(imageUrl);
                    if (imageBytes == null || imageBytes.length == 0) {
                        log.error("图片下载失败: userId={}, url={}", userId, imageUrl);
                        return;
                    }

                    // 生成文件ID（objectKey），使用与CommonController一致的格式
                    String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                    String uuid = UUID.randomUUID().toString().replace("-", "");
                    String fileId = String.format("image/%s/%s.jpg", dateDir, uuid);

                    // 上传到OSS
                    String ossUrl = aliOssUtil.upload(imageBytes, fileId);
                    log.info("书签图片上传成功: userId={}, fileId={}, ossUrl={}", userId, fileId, ossUrl);

                    // 这里应该有将书签URL保存到用户表的代码
                    // 由于没有提供UserMapper.updateDailyBookmark方法，这里仅记录日志
                    //还要上传到附件表
                    log.info("书签URL应更新到用户表: userId={}, bookmarkUrl={}", userId, ossUrl);

                    // 发送WebSocket通知用户书签已生成
                    if (webSocketServer != null && webSocketServer.Open(String.valueOf(userId))) {
                        webSocketServer.sendToUser(String.valueOf(userId), "书签已生成: " + ossUrl);
                    }

                } catch (Exception e) {
                    log.error("为用户生成书签失败: userId={}", userId, e);
                }
            }
            // 手动确认消息已消费
            // 参数1: 消息标识，参数2: 是否批量确认
            channel.basicAck(amqpMessage.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("处理Direct队列1消息出错", e);
            // 处理失败，拒绝消息并重新入队
            // 参数3: 是否重新入队
            channel.basicNack(amqpMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.DIRECT_QUEUE_2)
    public void consumeDirectQueue2(MessageDTO message, Channel channel, Message amqpMessage) throws IOException {
        try {
            log.info("【Direct队列2】收到消息: {}", message);

            // 处理邮件发送
            Object content = message.getContent();
            if (content instanceof MailDTO) {
                MailDTO mailDTO = (MailDTO) content;
                String htmlContent = generateHtmlEmail(mailDTO);
                String subject = "一封来自 " + (mailDTO.getSenderNickname() != null ? mailDTO.getSenderNickname() : "朋友")
                        + " 的邮件";

                // 使用 MailUtil 发送HTML格式邮件
                mailUtil.sendHtmlMail(mailDTO.getRecipientEmail(), subject, htmlContent, null);
                log.info("邮件已成功发送到: {}", mailDTO.getRecipientEmail());
            }

            channel.basicAck(amqpMessage.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("处理Direct队列2消息出错", e);
            channel.basicNack(amqpMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    /**
     * 生成美观的 HTML 邮件模板
     */
    private String generateHtmlEmail(MailDTO mailDTO) {
        String senderName = mailDTO.getSenderNickname() != null ? mailDTO.getSenderNickname() : "朋友";
        String stampType = mailDTO.getStampType() != null ? mailDTO.getStampType() : "";
        String stampContent = mailDTO.getStampContent() != null ? mailDTO.getStampContent() : "";
        // 先转义 HTML 防止 XSS，然后将换行符转换为 <br> 标签
        String rawContent = mailDTO.getContent() != null ? mailDTO.getContent() : "";
        // 先处理换行符，因为转义后 \n 还是 \n
        String content = escapeHtml(rawContent.replace("\r\n", "\n").replace("\r", "\n")).replace("\n", "<br>");

        return "<!DOCTYPE html>" +
                "<html lang=\"zh-CN\">" +
                "<head>" +
                "    <meta charset=\"UTF-8\">" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "    <title>邮件</title>" +
                "    <style>" +
                "        * {" +
                "            margin: 0;" +
                "            padding: 0;" +
                "            box-sizing: border-box;" +
                "        }" +
                "        body {" +
                "            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;"
                +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);" +
                "            padding: 20px;" +
                "            min-height: 100vh;" +
                "        }" +
                "        .email-container {" +
                "            max-width: 600px;" +
                "            margin: 0 auto;" +
                "            background: #ffffff;" +
                "            border-radius: 16px;" +
                "            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);" +
                "            overflow: hidden;" +
                "        }" +
                "        .email-header {" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);" +
                "            color: #ffffff;" +
                "            padding: 40px 30px;" +
                "            text-align: center;" +
                "        }" +
                "        .email-header h1 {" +
                "            font-size: 28px;" +
                "            font-weight: 600;" +
                "            margin-bottom: 10px;" +
                "            letter-spacing: 0.5px;" +
                "        }" +
                "        .sender-info {" +
                "            font-size: 16px;" +
                "            opacity: 0.9;" +
                "            margin-top: 10px;" +
                "        }" +
                "        .stamp-section {" +
                "            padding: 30px;" +
                "            background: #f8f9fa;" +
                "            border-bottom: 1px solid #e9ecef;" +
                "            text-align: center;" +
                "        }" +
                "        .stamp-type {" +
                "            display: inline-block;" +
                "            padding: 8px 16px;" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);" +
                "            color: #ffffff;" +
                "            border-radius: 20px;" +
                "            font-size: 14px;" +
                "            font-weight: 500;" +
                "            margin-bottom: 10px;" +
                "        }" +
                "        .stamp-content {" +
                "            font-size: 15px;" +
                "            color: #495057;" +
                "            margin-top: 10px;" +
                "        }" +
                "        .email-body {" +
                "            padding: 40px 30px;" +
                "            color: #212529;" +
                "            line-height: 1.8;" +
                "            font-size: 16px;" +
                "        }" +
                "        .email-body p {" +
                "            margin-bottom: 20px;" +
                "        }" +
                "        .email-footer {" +
                "            padding: 30px;" +
                "            background: #f8f9fa;" +
                "            border-top: 1px solid #e9ecef;" +
                "            text-align: center;" +
                "            color: #6c757d;" +
                "            font-size: 14px;" +
                "        }" +
                "        .footer-line {" +
                "            height: 2px;" +
                "            background: linear-gradient(90deg, transparent, #667eea, transparent);" +
                "            margin-bottom: 20px;" +
                "        }" +
                "        @media (max-width: 600px) {" +
                "            .email-container {" +
                "                margin: 10px;" +
                "                border-radius: 12px;" +
                "            }" +
                "            .email-header, .email-body, .email-footer {" +
                "                padding: 25px 20px;" +
                "            }" +
                "            .email-header h1 {" +
                "                font-size: 24px;" +
                "            }" +
                "        }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"email-container\">" +
                "        <div class=\"email-header\">" +
                "            <h1>✉️ 您有一封新邮件</h1>" +
                "            <div class=\"sender-info\">来自: " + escapeHtml(senderName) + "</div>" +
                "        </div>" +
                (StrUtil.isNotBlank(stampType) || StrUtil.isNotBlank(stampContent)
                        ? "        <div class=\"stamp-section\">" +
                                (StrUtil.isNotBlank(stampType)
                                        ? "<div class=\"stamp-type\">" + escapeHtml(stampType) + "</div>"
                                        : "")
                                +
                                (StrUtil.isNotBlank(stampContent)
                                        ? "<div class=\"stamp-content\">" + escapeHtml(stampContent) + "</div>"
                                        : "")
                                +
                                "        </div>"
                        : "")
                +
                "        <div class=\"email-body\">" +
                "            <div>" + content + "</div>" +
                "        </div>" +
                "        <div class=\"email-footer\">" +
                "            <div class=\"footer-line\"></div>" +
                "            <p>此邮件由系统自动发送，请勿直接回复</p>" +
                "            <p style=\"margin-top: 10px; font-size: 12px; opacity: 0.7;\">© "
                + java.time.LocalDate.now().getYear() + " All rights reserved</p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }

    /**
     * HTML 转义，防止 XSS 攻击
     */
    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @RabbitListener(queues = RabbitMQConfig.TOPIC_QUEUE_1)
    public void consumeTopicQueue1(MessageDTO message, Channel channel, Message amqpMessage) throws IOException {
        try {
            log.info("【Topic队列1(用户相关)】收到消息: {}", message);

            // 处理用户相关业务...

            channel.basicAck(amqpMessage.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("处理Topic队列1消息出错", e);
            channel.basicNack(amqpMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.TOPIC_QUEUE_2)
    public void consumeTopicQueue2(MessageDTO message, Channel channel, Message amqpMessage) throws IOException {
        try {
            log.info("【Topic队列2(订单相关)】收到消息: {}", message);

            // 处理订单相关业务...

            channel.basicAck(amqpMessage.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("处理Topic队列2消息出错", e);
            channel.basicNack(amqpMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.FANOUT_QUEUE_1)
    public void consumeFanoutQueue1(MessageDTO message, Channel channel, Message amqpMessage) throws IOException {
        try {
            log.info("【Fanout队列1】收到消息: {}", message);

            // 处理业务逻辑...

            channel.basicAck(amqpMessage.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("处理Fanout队列1消息出错", e);
            channel.basicNack(amqpMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.FANOUT_QUEUE_2)
    public void consumeFanoutQueue2(MessageDTO message, Channel channel, Message amqpMessage) throws IOException {
        try {
            log.info("【Fanout队列2】收到消息: {}", message);

            // 处理业务逻辑...

            channel.basicAck(amqpMessage.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("处理Fanout队列2消息出错", e);
            channel.basicNack(amqpMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.DEAD_LETTER_QUEUE)
    public void consumeDeadLetterQueue(MessageDTO message, Channel channel, Message amqpMessage) throws IOException {
        try {
            log.info("【死信队列】收到消息: {}", message);

            // 处理死信消息，通常是一些需要特殊处理的失败消息

            channel.basicAck(amqpMessage.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("处理死信队列消息出错", e);
            channel.basicNack(amqpMessage.getMessageProperties().getDeliveryTag(), false, false);
        }
    }
}
