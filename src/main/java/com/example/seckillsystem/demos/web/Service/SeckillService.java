package com.example.seckillsystem.demos.web.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.concurrent.*;

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

    @Autowired
    private ExecutorService seckillExecutorService;// 注入线程池

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SeckillOrderRepository orderRepository;

    // 【新增】内存售罄标记
    private volatile boolean isSoldOut = false;

    // 1. 注入平台事务管理器
    @Autowired
    private PlatformTransactionManager transactionManager;
    private final Semaphore semaphore = new Semaphore(10); // 限制最多10个线程同时访问
    /*
       * 新的入口方法：负责接收请求并提交到线程池
       * 这个方法会立刻返回，不会等待后台程序执行完毕
     */
    public String submitSeckillOrder(Long productId, Long userId) {
        if (isSoldOut) {
            return "商品已售罄（内存标记拦截）";
        }
        log.info("用户 {} 提交秒杀请求，商品ID: {}", userId, productId);

        // 【关键改动】在这里为 Runnable 任务增加一个 try-catch 块
        Runnable task = () -> {
            try {
                // 原来的调用逻辑不变
                processSeckill(productId, userId);
            } catch (Exception e) {
                // 当后台任务抛出任何异常时，我们在这里捕获并清晰地打印出来
                log.error("后台秒杀任务执行失败！productId={}, userId={}", productId, userId, e);
            }
        };

        try {
            seckillExecutorService.submit(task);
        } catch (RejectedExecutionException e) {
            log.warn("线程池已满，任务被拒绝。 productId={}, userId={}", productId, userId);
            return "当前活动过于火爆，请稍后重试！";
        }

        return "请求已接收，正在排队处理中，请稍后查看订单状态。";
    }
    /*
        * 新增方法，用于查询商品库存，专门用于处理读请求
        * 使用读写锁中的读锁，允许并发读取，互不堵塞
     */
    public Integer checkStock(Long productId) {
        try {
            Optional<Product> productOpt = productRepository.findById(productId);
            if (!productOpt.isPresent()) {
                throw new RuntimeException("商品不存在");
            }
            return productOpt.get().getStock();
        } finally {
            log.info("线程 {} 准备释放读锁.", Thread.currentThread().getName());
        }
    }

    /*
        * 核心方法：处理写请求
        * 使用写锁，确保同一时刻只有一个线程在处理秒杀请求
     */
    private void processSeckill(Long productId, Long userId) {
        boolean acquired = false;
        try {
            // 1. 先尝试获取许可，这是一个“卫语句”，不成功直接返回
            acquired = semaphore.tryAcquire(3, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("线程 {} 获取信号量许可超时，放弃秒杀请求。", Thread.currentThread().getName());
                return;
            }

            log.info("线程 {} 成功获取信号量许可，准备执行业务。", Thread.currentThread().getName());


            // 3. 获取互斥锁，执行必须串行的数据库操作
            // 将这部分逻辑封装到另一个私有方法中，使代码更清晰
            executeDbOperationsWithoutLock(productId, userId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("线程 {} 在等待或许可时被中断。", Thread.currentThread().getName(), e);
        } catch (Exception e) {
            // 捕获所有其他可能的异常
            log.error("处理秒杀时发生未知异常, productId={}, userId={}", productId, userId, e);
        }
        finally {
            // 4. 只有在成功获取了许可的情况下，才释放许可
            if (acquired) {
                semaphore.release();
                log.info("线程 {} 释放信号量许可。", Thread.currentThread().getName());
            }
        }
    }

    // 将所有数据库和锁相关的操作封装起来
    private void executeDbOperationsWithoutLock(Long productId, Long userId) {
        // 【移除】不再需要 writeLock.lock()
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = transactionManager.getTransaction(def);
        try {
            // 前置检查（重复下单等）依然可以保留
            if (orderRepository.findByUserIdAndProductId(userId, productId) != null) {
                throw new RuntimeException("您已秒杀过此商品，请勿重复下单");
            }

            // 1. 【核心改动】直接调用原子更新方法扣减库存
            int result = productRepository.deductStock(productId);

            // 2. 检查结果
            if (result == 0) {
                // 如果更新行数为0，说明库存不足
                isSoldOut = true; // 【优化】设置内存售罄标记
                throw new RuntimeException("商品已售罄");
            }

            // 3. 如果扣减成功，才创建订单
            Product product = getProduct(productId); // 获取商品信息用于创建订单
            SeckillOrder order = new SeckillOrder();
            order.setUserId(userId);
            order.setProductId(productId);
            order.setOrderPrice(product.getPrice());
            orderRepository.save(order);

            transactionManager.commit(status);
            log.info("线程 {} 秒杀成功，提交事务。", Thread.currentThread().getName());

        } catch (Exception e) {
            transactionManager.rollback(status);
            log.error("线程 {} 秒杀失败，回滚事务: {}", Thread.currentThread().getName(), e.getMessage());
        }
        // 【移除】不再需要 finally { writeLock.unlock() }
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