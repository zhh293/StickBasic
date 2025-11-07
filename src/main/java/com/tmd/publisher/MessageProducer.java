package com.tmd.publisher;

import com.tmd.config.RabbitMQConfig;
import com.tmd.entity.dto.UserContent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MessageProducer {

    // RabbitTemplate是Spring AMQP提供的发送消息的工具类

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendDirectMessage(Object content, boolean isRoutingKey1) {
        MessageDTO message = new MessageDTO();
        if (content instanceof UserContent) {
            message.setId(((UserContent) content).getUserId().toString());
            message.setContent(((UserContent) content).getContents());
            message.setSendTime(LocalDateTime.now());
            message.setType("direct");
        }
        if(content instanceof Long){
            message.setId(content.toString());
            message.setContent(content);
            message.setType("direct");
            message.setSendTime(LocalDateTime.now());
            message.setTopicExchange(RabbitMQConfig.DIRECT_EXCHANGE);
        }
        // 根据参数选择不同的路由键
        String routingKey = isRoutingKey1 ? RabbitMQConfig.DIRECT_ROUTING_KEY_1 : RabbitMQConfig.DIRECT_ROUTING_KEY_2;

        // 发送消息，参数：交换机、路由键、消息内容、消息ID(用于确认机制)
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DIRECT_EXCHANGE,
                routingKey,
                message,
                new CorrelationData(message.getId()));
    }

    public void sendTopicMessage(String content, String routingKeySuffix) {
        MessageDTO message = new MessageDTO();
        message.setId(UUID.randomUUID().toString());
        message.setContent(content);
        message.setSendTime(LocalDateTime.now());
        message.setType("topic");

        // 完整路由键 = 前缀 + 后缀
        String fullRoutingKey = "topic.routing.key." + routingKeySuffix;

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TOPIC_EXCHANGE,
                fullRoutingKey,
                message,
                new CorrelationData(message.getId()));
    }

    public void sendFanoutMessage(String content) {
        MessageDTO message = new MessageDTO();
        message.setId(UUID.randomUUID().toString());
        message.setContent(content);
        message.setSendTime(LocalDateTime.now());
        message.setType("fanout");

        // Fanout交换机忽略路由键，这里可以传任意值或空
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.FANOUT_EXCHANGE,
                "", // 路由键无效
                message,
                new CorrelationData(message.getId()));
    }

    public void sendDeadLetterMessage(String content) {
        MessageDTO message = new MessageDTO();
        message.setId(UUID.randomUUID().toString());
        message.setContent(content);
        message.setSendTime(LocalDateTime.now());
        message.setType("dead_letter");

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DIRECT_EXCHANGE,
                "normal.routing.key",
                message,
                new CorrelationData(message.getId()));
    }

    /**
     * 发送邮件消息到队列
     * 
     * @param mailDTO 邮件数据对象
     */
    public void sendMailMessage(com.tmd.entity.dto.MailDTO mailDTO) {
        MessageDTO message = new MessageDTO();
        message.setId(UUID.randomUUID().toString());
        message.setContent(mailDTO);
        message.setSendTime(LocalDateTime.now());
        message.setType("mail");

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DIRECT_EXCHANGE,
                RabbitMQConfig.DIRECT_ROUTING_KEY_2,
                message,
                new CorrelationData(message.getId()));
    }
}
