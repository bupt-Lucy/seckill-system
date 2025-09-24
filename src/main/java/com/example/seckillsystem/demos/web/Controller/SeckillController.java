package com.example.seckillsystem.demos.web.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam; // 引入这个注解
import org.springframework.web.bind.annotation.RestController;

import com.example.seckillsystem.demos.web.Service.SeckillService;

@RestController
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    // 修改方法签名，增加一个 @RequestParam
    @PostMapping("/seckill/{productId}")
    public String doSeckill(@PathVariable Long productId,
                            @RequestParam("userId") Long userId) {
        // userId不再是写死的，而是从请求参数中获取
        // 例如，请求的 URL 可能是： /seckill/1?userId=10002
        return seckillService.processSeckill(productId, userId);
    }
}