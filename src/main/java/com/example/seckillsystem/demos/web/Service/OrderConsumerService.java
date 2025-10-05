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

@Service
public class OrderConsumerService {

    @Autowired
    private SeckillService seckillService; // 获取队列所在的Service

    @Autowired
    private SeckillOrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    @Lazy
    private OrderConsumerService self;
    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);


    // 应用启动后，开启一个后台线程来消费订单
    @PostConstruct
    private void startConsumer() {
        new Thread(() -> {
            while (true) {
                try {
                    SeckillOrder order = seckillService.getOrderQueue().take();
                    // 2. 循环体内部现在只调用这个新的、带事务的方法
                    self.createOrderInDb(order);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("订单消费者线程被中断", e);
                    break;
                } catch (Exception e) {
                    // 捕获所有其他可能的异常，防止线程意外终止
                    log.error("处理订单时发生未知异常", e);
                }
            }
        }).start();
    }

    /**
     * 3. 【新增】一个公开的、带事务注解的方法，专门用于数据库操作
     * @param order 从队列中取出的订单信息
     */
    @Transactional
    public void createOrderInDb(SeckillOrder order) {
        log.info("正在创建订单并扣减MySQL库存: {}", order);

        // 将所有数据库操作都放在这个方法里
        orderRepository.save(order);

        int result = productRepository.deductStock(order.getProductId());
        if (result == 0) {
            // 这是一个补偿逻辑，理论上在Redis阶段已经保证了库存充足
            // 但为了数据最终一致性，如果MySQL库存扣减失败，应抛出异常让事务回滚
            throw new RuntimeException("MySQL a's stock deduction failed for order: " + order);
        }

        log.info("数据库订单创建成功");
    }
}