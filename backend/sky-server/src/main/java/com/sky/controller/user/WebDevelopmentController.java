package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.result.Result;
import com.sky.service.OrderService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import javax.annotation.PostConstruct;

/** Browser-only local development adapter. Never enabled in a production profile. */
@RestController
@Profile("webdev")
public class WebDevelopmentController {
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final RedisTemplate redisTemplate;

    public WebDevelopmentController(OrderMapper orderMapper, OrderService orderService,
                                    RedisTemplate redisTemplate) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void initializeDevelopmentShop() {
        if (Boolean.FALSE.equals(redisTemplate.hasKey(ShopController.KEY))) {
            redisTemplate.opsForValue().set(ShopController.KEY, 1);
        }
    }

    @PutMapping("/user/dev/payment/{id}")
    public Result<String> payment(@PathVariable Long id) {
        Orders order = orderMapper.getById(id);
        if (order == null || !order.getUserId().equals(BaseContext.getCurrentId())) {
            return Result.error("订单不存在");
        }
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())) {
            return Result.error("订单不是待付款状态");
        }
        orderService.paySuccess(order.getNumber());
        return Result.success("本地模拟支付成功，未产生真实扣款");
    }
}
