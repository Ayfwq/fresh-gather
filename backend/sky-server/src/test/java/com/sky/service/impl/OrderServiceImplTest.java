package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Dish;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.FlashSaleOrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final Long USER_ID = 81L;

    @Mock private OrderMapper orderMapper;
    @Mock private OrderDetailMapper orderDetailMapper;
    @Mock private AddressBookMapper addressBookMapper;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Mock private DishMapper dishMapper;
    @Mock private SetmealMapper setmealMapper;
    @Mock private RedisTemplate redisTemplate;
    @Mock private ValueOperations valueOperations;
    @Mock private UserMapper userMapper;
    @Mock private WeChatPayUtil weChatPayUtil;
    @Mock private WebSocketServer webSocketServer;
    @Mock private FlashSaleOrderService flashSaleOrderService;

    @InjectMocks private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(USER_ID);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("SHOP_STATUS")).thenReturn(1);
    }

    @AfterEach
    void clearUserContext() {
        BaseContext.removeCurrentId();
    }

    @Test
    void calculatesOrderAmountFromCurrentCatalogPriceInsteadOfClientValue() {
        when(addressBookMapper.getByIdAndUserId(8L, USER_ID)).thenReturn(AddressBook.builder()
                .id(8L).userId(USER_ID).detail("新鲜街 8 号").phone("13800000000").consignee("顾客").build());
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Collections.singletonList(
                ShoppingCart.builder().dishId(3L).number(2).amount(new BigDecimal("0.01")).build()));
        when(dishMapper.getById(3L)).thenReturn(Dish.builder()
                .id(3L).name("当季蔬菜").image("veg.png").status(1).price(new BigDecimal("12.50")).build());
        doAnswer(invocation -> {
            ((Orders) invocation.getArgument(0)).setId(501L);
            return null;
        }).when(orderMapper).insert(any(Orders.class));

        OrdersSubmitDTO request = new OrdersSubmitDTO();
        request.setAddressBookId(8L);
        request.setAmount(new BigDecimal("0.01"));
        service.submitOrder(request);

        ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertEquals(new BigDecimal("25.00"), orderCaptor.getValue().getAmount());
        assertEquals(Orders.UN_PAID, orderCaptor.getValue().getPayStatus());
        ArgumentCaptor<java.util.List<OrderDetail>> detailCaptor = ArgumentCaptor.forClass(java.util.List.class);
        verify(orderDetailMapper).insertBatch(detailCaptor.capture());
        assertEquals(new BigDecimal("12.50"), detailCaptor.getValue().get(0).getAmount());
    }

    @Test
    void rejectsCheckoutWhenAnotherUsersAddressIsSubmitted() {
        OrdersSubmitDTO request = new OrdersSubmitDTO();
        request.setAddressBookId(999L);
        when(addressBookMapper.getByIdAndUserId(999L, USER_ID)).thenReturn(null);

        assertThrows(AddressBookBusinessException.class, () -> service.submitOrder(request));
        verify(orderMapper, never()).insert(any(Orders.class));
    }
}
