package com.example.seckillsystem.demos.web.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.example.seckillsystem.demos.web.Service.SeckillService;

@RestController
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    /*
        * 写操作接口，保持不变
    */
    @PostMapping("/seckill/{productId}")
    public String doSeckill(@PathVariable Long productId,
                            @RequestParam("userId") Long userId) {
        // userId不再是写死的，而是从请求参数中获取
        // 例如，请求的 URL 可能是： /seckill/1?userId=10002
        return seckillService.submitSeckillOrder(productId, userId);
    }

    /*
        * 新增读操作接口
        * 用于查询商品库存
    */
    @GetMapping("/seckill/stock/{productId}")
    public String checkStock(@PathVariable Long productId) {
        Integer stock = seckillService.checkStock(productId);
        if (stock < 0) {
            return "商品不存在";
        }
        return "当前库存为: " + stock;
    }

}