package com.example.seckillsystem.demos.web.Service;

import com.example.seckillsystem.demos.web.Repository.ProductRepository;
import com.example.seckillsystem.demos.web.Repository.SeckillOrderRepository;
import com.example.seckillsystem.demos.web.SeckillOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderConsumerService {

    // 【移除】不再需要 @PostConstruct 和手动创建的 new Thread()

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    @Autowired
    private SeckillOrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    /**
     * 【核心改动】
     * 使用 @RabbitListener 注解来监听指定的队列
     * Spring AMQP 会自动为我们处理消息的接收、反序列化等工作
     * @param order 从队列中接收到的订单对象
     */
    @RabbitListener(queues = "seckill.order.queue")
    @Transactional // 数据库操作依然需要事务保护
    public void createOrderInDb(SeckillOrder order) {
        try {
            log.info("从RabbitMQ接收到订单消息，准备创建订单: {}", order);

            orderRepository.save(order);

            int result = productRepository.deductStock(order.getProductId());
            if (result == 0) {
                throw new RuntimeException("MySQL库存扣减失败，订单回滚: " + order);
            }

            log.info("数据库订单创建成功");
        } catch (Exception e) {
            // 如果发生异常，Spring AMQP 默认会进行重试，
            // 最终如果还是失败，消息会进入“死信队列”（需要额外配置）
            // 这里我们先简单地打印错误日志
            log.error("消费订单消息时发生异常: {}", order, e);
            // 抛出异常，以便Spring AMQP知道处理失败
            throw e;
        }
    }
}