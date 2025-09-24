package com.example.seckillsystem.demos.web.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.seckillsystem.demos.web.Product;
import com.example.seckillsystem.demos.web.SeckillOrder;
import com.example.seckillsystem.demos.web.Repository.ProductRepository;
import com.example.seckillsystem.demos.web.Repository.SeckillOrderRepository;

import java.util.Date;
import java.util.Optional;

@Service
public class SeckillService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SeckillOrderRepository orderRepository;

    @Transactional // 开启事务，保证扣库存和创建订单要么都成功，要么都失败
    public String processSeckill(Long productId, Long userId) {
        // 1. 检查秒杀活动时间
        Optional<Product> productOpt = productRepository.findById(productId);
        if (!productOpt.isPresent()) {
            return "商品不存在";
        }
        Product product = productOpt.get();
        Date now = new Date();
        if (now.before(product.getStartTime()) || now.after(product.getEndTime())) {
            return "秒杀未开始或已结束";
        }

        // 2. 检查是否重复秒杀
        if (orderRepository.findByUserIdAndProductId(userId, productId) != null) {
            return "您已秒杀过此商品，请勿重复下单";
        }

        // 3. 【关键】检查库存
        if (product.getStock() <= 0) {
            return "商品已售罄";
        }

        // 4. 【关键】扣减库存
        // 这里就是并发问题的爆发点！
        product.setStock(product.getStock() - 1);
        productRepository.save(product);

        // 5. 创建订单
        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setProductId(productId);
        order.setOrderPrice(product.getPrice());
        orderRepository.save(order);

        return "秒杀成功！订单创建中...";
    }
}