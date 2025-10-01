package com.example.seckillsystem.demos.web.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.seckillsystem.demos.web.Product;
import com.example.seckillsystem.demos.web.SeckillOrder;
import com.example.seckillsystem.demos.web.Repository.ProductRepository;
import com.example.seckillsystem.demos.web.Repository.SeckillOrderRepository;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class SeckillService {
    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SeckillOrderRepository orderRepository;

    // 1. 注入平台事务管理器
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    private final Lock writeLock = rwLock.writeLock();
    private final Lock readLock = rwLock.readLock();

    /*
        * 新增方法，用于查询商品库存，专门用于处理读请求
        * 使用读写锁中的读锁，允许并发读取，互不堵塞
     */
    public Integer checkStock(Long productId) {
        log.info("线程 {} 尝试获取读锁...", Thread.currentThread().getName());
        readLock.lock(); // 读操作上读锁
        log.info("线程 {} 成功获取到读锁", Thread.currentThread().getName());
        try {
            Optional<Product> productOpt = productRepository.findById(productId);
            if (!productOpt.isPresent()) {
                throw new RuntimeException("商品不存在");
            }
            log.info("线程 {} 读取库存为: {}", Thread.currentThread().getName(), productOpt.get().getStock());
            return productOpt.get().getStock();
        } finally {
            log.info("线程 {} 准备释放读锁.", Thread.currentThread().getName());
            readLock.unlock();
        }
    }

    /*
        * 核心方法：处理写请求
        * 使用写锁，确保同一时刻只有一个线程在处理秒杀请求
     */
    public String processSeckill(Long productId, Long userId) {
        log.info("线程 {} 尝试获取写锁...", Thread.currentThread().getName());
        writeLock.lock(); // 2. 秒杀操作上写锁，确保互斥
        log.info("线程 {} 成功获取到写锁", Thread.currentThread().getName());
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
            log.info("线程 {} 秒杀成功，提交事务。", Thread.currentThread().getName());

            return "秒杀成功！订单创建中...";
        } catch (Exception e) {
            // 6. 如果发生任何异常，手动回滚事务
            transactionManager.rollback(status);
            log.error("线程 {} 秒杀失败: {}", Thread.currentThread().getName(), e.getMessage());
            // 将异常信息返回或记录日志
            return e.getMessage();
        } finally {
            // 7. 最后，释放锁
            log.info("线程 {} 准备释放写锁.", Thread.currentThread().getName());
            writeLock.unlock();
        }
    }

    /*
        * 内部辅助方法：查询商品信息
        * 这个方法没有加锁：因为他总是在外部方法已经加锁的情况下被调用
     */
    private Product getProduct(Long productId) {
        Optional<Product> productOpt = productRepository.findById(productId);
        return productOpt.orElse(null);
    }
}