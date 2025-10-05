package com.example.seckillsystem.demos.web.Service;

import com.example.seckillsystem.demos.web.Product;
import com.example.seckillsystem.demos.web.Repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class RedisPreheatService implements CommandLineRunner {

    public static final String STOCK_KEY = "seckill:stock:";
    public static final String PRODUCT_KEY = "seckill:product:";
    public static final String USER_SET_KEY = "seckill:users:";

    @Autowired
    private ProductRepository productRepository; // 假设你已注入

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 应用启动后自动执行
    @Override
    public void run(String... args) throws Exception {
        // 假设我们秒杀的商品ID是 1
        long productId = 1L;
        Product product = productRepository.findById(productId).orElse(null);

        if (product != null) {
            // 1. 清理旧数据（为了可重复测试）
            redisTemplate.delete(STOCK_KEY + productId);
            redisTemplate.delete(USER_SET_KEY + productId);

            // 2. 加载库存到 Redis String
            redisTemplate.opsForValue().set(STOCK_KEY + productId, product.getStock());

            System.out.println("=========================================");
            System.out.println("Product " + productId + " stock preheated to Redis: " + product.getStock());
            System.out.println("=========================================");
        }
    }
}