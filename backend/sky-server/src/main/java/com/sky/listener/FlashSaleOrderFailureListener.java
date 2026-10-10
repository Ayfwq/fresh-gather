package com.sky.listener;

import com.sky.constant.FlashSaleRabbitConstant;
import com.sky.message.FlashSaleOrderMessage;
import com.sky.service.FlashSaleOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Compensates admission stock/quota after all transient order-creation retries fail. */
@Component
@Slf4j
@ConditionalOnProperty(value = "sky.flash-sale.async-enabled", havingValue = "true")
public class FlashSaleOrderFailureListener {

    private final FlashSaleOrderService flashSaleOrderService;

    public FlashSaleOrderFailureListener(FlashSaleOrderService flashSaleOrderService) {
        this.flashSaleOrderService = flashSaleOrderService;
    }

    @RabbitListener(queues = FlashSaleRabbitConstant.ORDER_FAILURE_QUEUE)
    public void compensateFailedCreate(FlashSaleOrderMessage message) {
        log.error("特卖建单重试耗尽，执行库存与限购回补，requestId={}", message.getRequestId());
        flashSaleOrderService.failFlashSaleOrder(message, "订单创建失败，请重新参与活动");
    }
}
