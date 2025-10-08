package com.example.seckillsystem.demos.web.Service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.seckillsystem.demos.web.Product;
import com.example.seckillsystem.demos.web.SeckillOrder;
import com.example.seckillsystem.demos.web.Repository.ProductRepository;
import com.example.seckillsystem.demos.web.Repository.SeckillOrderRepository;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class SeckillService {

    @Autowired
    private ExecutorService seckillExecutorService;// 注入线程池

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    @Autowired
    private ProductRepository productRepository;
    // 【新增】内存售罄标记
    private volatile boolean isSoldOut = false;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate; // 注入 RabbitMQ 的操作模板

    @Autowired
    private DefaultRedisScript<Long> seckillScript;
    //一个内存队列来存放成功秒杀的订单信息

    // 1. 注入平台事务管理器
    @Autowired
    private PlatformTransactionManager transactionManager;
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
        String stockKey = RedisPreheatService.STOCK_KEY + productId;
        Object stockObj = redisTemplate.opsForValue().get(stockKey);
        return stockObj != null ? Integer.parseInt(stockObj.toString()) : -1;
    }

    /*
        * 核心方法：处理写请求
        * 使用写锁，确保同一时刻只有一个线程在处理秒杀请求
     */
    private void processSeckill(Long productId, Long userId) {
        List<String> keys = Arrays.asList(
                RedisPreheatService.STOCK_KEY + productId,
                RedisPreheatService.USER_SET_KEY + productId
        );

        // 执行 Lua 脚本
        Long result = redisTemplate.execute(seckillScript, keys, userId.toString());

        if(result == 0){
            log.info("用户 {} 秒杀成功，商品ID: {}", userId, productId);
            // 秒杀成功，生成订单信息并放入内存队列
            // 此时订单尚未写入数据库
            Product product = getProduct(productId); // 获取商品信息用于创建订单
            SeckillOrder order = new SeckillOrder();
            order.setUserId(userId);
            order.setProductId(productId);
            order.setOrderPrice(product.getPrice());
            // 将订单放入队列
            rabbitTemplate.convertAndSend("seckill.order.queue", order);

        }
        else if(result == 2){
            log.warn("用户 {} 重复秒杀，商品ID: {}", userId, productId);
        }
        else if(result == 1){
            log.warn("用户 {} 秒杀失败，商品ID: {}，库存不足", userId, productId);
        }
        else{
            log.error("Lua脚本执行异常，返回值: {}", result);
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