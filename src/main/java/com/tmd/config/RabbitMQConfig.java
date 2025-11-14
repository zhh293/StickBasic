package com.tmd.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.tmd.properties.MailProperties;
import com.tmd.tools.MailUtil;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {
    @Autowired
    private MailProperties mailProperties;

    // ========================== Direct 交换机示例 ==========================
    // Direct交换机：精确匹配路由键
    public static final String DIRECT_EXCHANGE = "direct.exchange";
    public static final String DIRECT_QUEUE_1 = "direct.queue.1";
    public static final String DIRECT_QUEUE_2 = "direct.queue.2";
    public static final String DIRECT_ROUTING_KEY_1 = "direct.routing.key.1";
    public static final String DIRECT_ROUTING_KEY_2 = "direct.routing.key.2";

    // ========================== Topic 交换机示例 ==========================
    // Topic交换机：模糊匹配路由键，支持通配符
    public static final String TOPIC_EXCHANGE = "topic.exchange";
    public static final String TOPIC_QUEUE_1 = "topic.queue.1"; // 接收user相关消息
    public static final String TOPIC_QUEUE_2 = "topic.queue.2"; // 接收order相关消息
    public static final String TOPIC_ROUTING_KEY_USER = "topic.routing.key.user.#"; // #匹配多个单词
    public static final String TOPIC_ROUTING_KEY_ORDER = "topic.routing.key.order.*"; // *匹配一个单词

    // ========================== Fanout 交换机示例 ==========================
    // Fanout交换机：广播消息，忽略路由键
    public static final String FANOUT_EXCHANGE = "fanout.exchange";
    public static final String FANOUT_QUEUE_1 = "fanout.queue.1";
    public static final String FANOUT_QUEUE_2 = "fanout.queue.2";

    // ========================== 死信队列示例 ==========================
    public static final String DEAD_LETTER_EXCHANGE = "dead.letter.exchange";
    public static final String DEAD_LETTER_QUEUE = "dead.letter.queue";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead.letter.routing.key";
    public static final String NORMAL_QUEUE = "normal.queue"; // 会产生死信的普通队列


    // 1. Direct交换机配置
    @Bean
    public DirectExchange directExchange() {
        // durable: 是否持久化, autoDelete: 当最后一个绑定被删除后是否自动删除交换机
        return ExchangeBuilder.directExchange(DIRECT_EXCHANGE)
                .durable(true)
                .autoDelete()
                .build();
    }

    @Bean
    public Queue directQueue1() {
        java.util.Map<String, Object> arguments = new java.util.HashMap<>(2);
        arguments.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY);
        return QueueBuilder.durable(DIRECT_QUEUE_1)
                .withArguments(arguments)
                .build();
    }

    @Bean
    public Queue directQueue2() {
        java.util.Map<String, Object> arguments = new java.util.HashMap<>(2);
        arguments.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY);
        return QueueBuilder.durable(DIRECT_QUEUE_2)
                .withArguments(arguments)
                .build();
    }

    // 绑定关系：将directQueue1与directExchange通过DIRECT_ROUTING_KEY_1绑定
    @Bean
    public Binding directBinding1(Queue directQueue1, DirectExchange directExchange) {
        return BindingBuilder.bind(directQueue1)
                .to(directExchange)
                .with(DIRECT_ROUTING_KEY_1);
    }

    @Bean
    public Binding directBinding2(Queue directQueue2, DirectExchange directExchange) {
        return BindingBuilder.bind(directQueue2)
                .to(directExchange)
                .with(DIRECT_ROUTING_KEY_2);
    }


    // 2. Topic交换机配置
    @Bean
    public TopicExchange topicExchange() {
        return ExchangeBuilder.topicExchange(TOPIC_EXCHANGE)
                .durable(true)
                .autoDelete()
                .build();
    }

    @Bean
    public Queue topicQueue1() {
        return QueueBuilder.durable(TOPIC_QUEUE_1)
                .build();
    }

    @Bean
    public Queue topicQueue2() {
        return QueueBuilder.durable(TOPIC_QUEUE_2)
                .build();
    }

    @Bean
    public Binding topicBinding1(Queue topicQueue1, TopicExchange topicExchange) {
        // 绑定路由键：topic.routing.key.user.# 可以匹配 user相关的所有消息
        return BindingBuilder.bind(topicQueue1)
                .to(topicExchange)
                .with(TOPIC_ROUTING_KEY_USER);
    }

    @Bean
    public Binding topicBinding2(Queue topicQueue2, TopicExchange topicExchange) {
        // 绑定路由键：topic.routing.key.order.* 可以匹配 order相关的一级子消息
        return BindingBuilder.bind(topicQueue2)
                .to(topicExchange)
                .with(TOPIC_ROUTING_KEY_ORDER);
    }


    // 3. Fanout交换机配置
    @Bean
    public FanoutExchange fanoutExchange() {
        return ExchangeBuilder.fanoutExchange(FANOUT_EXCHANGE)
                .durable(true)
                .autoDelete()
                .build();
    }

    @Bean
    public Queue fanoutQueue1() {
        return QueueBuilder.durable(FANOUT_QUEUE_1)
                .build();
    }

    @Bean
    public Queue fanoutQueue2() {
        return QueueBuilder.durable(FANOUT_QUEUE_2)
                .build();
    }

    // Fanout交换机绑定不需要路由键
    @Bean
    public Binding fanoutBinding1(Queue fanoutQueue1, FanoutExchange fanoutExchange) {
        return BindingBuilder.bind(fanoutQueue1)
                .to(fanoutExchange);
    }

    @Bean
    public Binding fanoutBinding2(Queue fanoutQueue2, FanoutExchange fanoutExchange) {
        return BindingBuilder.bind(fanoutQueue2)
                .to(fanoutExchange);
    }


    // 4. 死信队列配置
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Queue normalQueue() {
        // 配置普通队列，指定死信交换机和死信路由键
        Map<String, Object> arguments = new HashMap<>(3);
        // 消息过期时间，单位：毫秒
        arguments.put("x-message-ttl", 10000);
        // 死信交换机
        arguments.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
        // 死信路由键
        arguments.put("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY);

        return QueueBuilder.durable(NORMAL_QUEUE)
                .withArguments(arguments)
                .build();
    }

    @Bean
    public Binding normalBinding(Queue normalQueue, DirectExchange directExchange) {
        // 将普通队列绑定到direct交换机
        return BindingBuilder.bind(normalQueue)
                .to(directExchange)
                .with("normal.routing.key");
    }

    @Bean
    public MailUtil mailUtil() {
        return new MailUtil(
                "smtp.qq.com",
                "465",
                "766045749@qq.com",
                "rdezwyfvvynnbddi",
                "766045749@qq.com",
                true,
                true,
                false
        );
    }
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 注册 JavaTime 模块，支持 LocalDateTime 等 Java 8 时间类型
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
