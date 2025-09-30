package com.example.seckillsystem.demos.web.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;

import com.example.seckillsystem.demos.web.Product;
import com.example.seckillsystem.demos.web.SeckillOrder;
import com.example.seckillsystem.demos.web.Repository.ProductRepository;
import com.example.seckillsystem.demos.web.Repository.SeckillOrderRepository;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class SeckillService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SeckillOrderRepository orderRepository;

    // 1. 注入平台事务管理器
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final Lock lock = new ReentrantLock();

    // 2. 【重要】移除方法上的 @Transactional 注解
    public String processSeckill(Long productId, Long userId) {

        lock.lock();
        // 3. 定义事务
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        // 4. 开启事务
        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            // 所有业务逻辑，和之前一样
            Optional<Product> productOpt = productRepository.findById(productId);
            if (!productOpt.isPresent()) {
                throw new RuntimeException("商品不存在");
            }
            Product product = productOpt.get();
            Date now = new Date();
            if (now.before(product.getStartTime()) || now.after(product.getEndTime())) {
                throw new RuntimeException("秒杀未开始或已结束");
            }

            if (orderRepository.findByUserIdAndProductId(userId, productId) != null) {
                throw new RuntimeException("您已秒杀过此商品，请勿重复下单");
            }

            if (product.getStock() <= 0) {
                throw new RuntimeException("商品已售罄");
            }

            product.setStock(product.getStock() - 1);
            productRepository.save(product);

            SeckillOrder order = new SeckillOrder();
            order.setUserId(userId);
            order.setProductId(productId);
            order.setOrderPrice(product.getPrice());
            orderRepository.save(order);

            // 5. 【关键】在锁释放前，手动提交事务
            transactionManager.commit(status);

            return "秒杀成功！订单创建中...";
        } catch (Exception e) {
            // 6. 如果发生任何异常，手动回滚事务
            transactionManager.rollback(status);
            // 将异常信息返回或记录日志
            return e.getMessage();
        } finally {
            // 7. 最后，释放锁
            lock.unlock();
        }
    }
}