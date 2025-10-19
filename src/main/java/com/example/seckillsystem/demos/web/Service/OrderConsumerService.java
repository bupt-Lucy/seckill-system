package com.example.seckillsystem.demos.web.Service;
import com.example.seckillsystem.demos.web.Repository.ProductRepository;
import com.example.seckillsystem.demos.web.Repository.SeckillOrderRepository;
import com.example.seckillsystem.demos.web.SeckillOrder;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderConsumerService {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumerService.class);

    @Autowired
    private SeckillOrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    // 注入自身代理，以解决 AOP 方法自调用的问题
    @Autowired
    @Lazy
    private OrderConsumerService self;

    /**
     * 【第一层：消费者入口 & 熔断层】
     * 这个方法是 RabbitMQ 消息的直接入口。
     * 它只负责一件事：提供熔断保护，然后将任务委托给内部的事务方法。
     * 它本身不带 @Transactional 注解。
     */
    @RabbitListener(queues = "seckill.order.queue")
    @CircuitBreaker(name = "dbWrite", fallbackMethod = "fallbackForCreateOrder")
    public void receiveOrderMessage(SeckillOrder order) {
        log.info("从RabbitMQ接收到订单消息，准备进行数据库操作: {}", order);
        // 【关键】通过 self 代理对象，调用带有 @Transactional 注解的内部方法
        // 这样可以确保 @Transactional 生效
        self.createOrderInDb(order);
    }

    /**
     * 【第二层：事务与业务逻辑层】
     * 这个方法现在是一个内部方法，只负责核心的数据库操作。
     * 它只关心一件事：保证这些操作在一个事务中完成。
     */
    @Transactional
    public void createOrderInDb(SeckillOrder order) {
        // 内部不再需要 try-catch，让异常自然抛出，以便 @CircuitBreaker 能够捕获
        log.info("进入事务方法，准备创建订单: {}", order);
        orderRepository.save(order);
        int result = productRepository.deductStock(order.getProductId());
        if (result == 0) {
            // 抛出异常，让事务回滚
            throw new RuntimeException("MySQL库存扣减失败，订单回滚: " + order);
        }
        log.info("数据库订单创建成功，事务即将提交。");
    }

    /**
     * 降级方法，保持不变。
     * 它的方法签名需要与【第一层】的 @CircuitBreaker 所在的方法匹配。
     */
    public void fallbackForCreateOrder(SeckillOrder order, Throwable t) {
        log.error("数据库写入熔断器已打开！执行降级逻辑。 订单: {}, 异常: {}", order, t.getMessage());
    }
}