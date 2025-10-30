package com.tmd.consumer;

import com.rabbitmq.client.Channel;

import com.tmd.WebSocket.WebSocketServer;
import com.tmd.config.RabbitMQConfig;
import com.tmd.publisher.MessageDTO;
import com.tmd.tools.MailUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
    private WebSocketServer webSocketServer;
    @RabbitListener(queues = RabbitMQConfig.DIRECT_QUEUE_1)
    public void consumeDirectQueue1(MessageDTO message, Channel channel, Message amqpMessage) throws IOException {
        try {
            log.info("【Direct队列1】收到消息: {}", message);

            // 处理业务逻辑...
            // TODO: 实际业务处理代码
            Object content = message.getContent();
            if(content instanceof List<?>){
                String response = summaryClient.prompt()
                        .user(content.toString())
                        .call()
                        .content();
                log.info(response);
                //直接调用onmessage，onopen，onclose等方法也是可以的，会执行对应函数的代码，但是不符合规范，毕竟这几个函数只针对客户端发来的消息做出相应的回应，一般不在服务器端主动调用。。
                //OK，这个功能也算是完成了，美滋滋。。。。。。。。。。。。。。。
                if(!webSocketServer.Open(message.getId())){
                    throw new RuntimeException("用户未连接websocket");
                }
                if(message.getId()!=null){
                    webSocketServer.sendToUser(message.getId(),response);
                    webSocketServer.sendToUser(message.getId(),"[END]");
                    log.info("[END]");
                }
            }
//            mailUtil.sendMail(to,subject,mail);
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



            channel.basicAck(amqpMessage.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("处理Direct队列2消息出错", e);
            channel.basicNack(amqpMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
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

