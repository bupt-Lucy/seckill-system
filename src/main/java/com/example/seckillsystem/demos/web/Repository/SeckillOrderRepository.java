package com.example.seckillsystem.demos.web.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.seckillsystem.demos.web.SeckillOrder;
public interface SeckillOrderRepository extends JpaRepository<SeckillOrder, Long> {
    // 根据用户ID和商品ID查询订单，用于判断是否重复秒杀
    SeckillOrder findByUserIdAndProductId(Long userId, Long productId);
}