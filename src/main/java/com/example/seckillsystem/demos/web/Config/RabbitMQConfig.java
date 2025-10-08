package com.example.seckillsystem.demos.web.Config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    @Bean
    public Queue seckillOrderQueue() {
        // durable(true) 表示队列是持久化的，即使RabbitMQ重启也不会丢失
        return new Queue("seckill.order.queue", true);
    }

    /**
     * 【新增】
     * 定义一个消息转换器 Bean，用于将消息序列化为 JSON 格式。
     * Spring Boot 在检测到这个 Bean 后，会自动用它来替换掉默认的 SimpleMessageConverter。
     */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
