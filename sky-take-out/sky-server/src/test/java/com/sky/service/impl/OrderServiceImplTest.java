package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.utils.WeChatPayUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 订单业务层单元测试
 *
 * <p>覆盖点：
 * <ul>
 *     <li>取消订单：订单不存在、状态错误（已完成订单不可取消）、待接单退款、正常取消</li>
 *     <li>支付成功：更新订单状态 + 发送 MQ 来单提醒消息（异步化）</li>
 *     <li>再来一单：订单明细转购物车并批量插入</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderDetailMapper orderDetailMapper;

    @Mock
    private ShoppingCartMapper shoppingCartMapper;

    @Mock
    private WeChatPayUtil weChatPayUtil;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    // ==================== 取消订单 ====================

    @Test
    @DisplayName("取消订单：订单不存在抛异常")
    void testUserCancelById_orderNotFound() throws Exception {
        when(orderMapper.getById(1L)).thenReturn(null);

        assertThrows(OrderBusinessException.class,
                () -> orderService.userCancelById(1L));

        verify(orderMapper, never()).update(any());
    }

    @Test
    @DisplayName("取消订单：已完成订单（状态>2）不可取消")
    void testUserCancelById_completedOrderCannotCancel() throws Exception {
        Orders order = Orders.builder()
                .id(1L)
                .number("1001")
                .status(Orders.COMPLETED) // 5 = 已完成
                .build();
        when(orderMapper.getById(1L)).thenReturn(order);

        assertThrows(OrderBusinessException.class,
                () -> orderService.userCancelById(1L));

        verify(orderMapper, never()).update(any());
    }

    @Test
    @DisplayName("取消订单：待接单状态取消需退款")
    void testUserCancelById_toBeConfirmedTriggersRefund() throws Exception {
        Orders order = Orders.builder()
                .id(1L)
                .number("1001")
                .status(Orders.TO_BE_CONFIRMED) // 2 = 待接单
                .build();
        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.userCancelById(1L);

        // 应调用退款
        verify(weChatPayUtil, times(1)).refund(eq("1001"), eq("1001"), any(), any());

        // 应更新订单状态为已取消、支付状态为退款
        verify(orderMapper, times(1)).update(argThat(o ->
                o.getStatus().equals(Orders.CANCELLED)
                        && o.getPayStatus().equals(Orders.REFUND)
        ));
    }

    @Test
    @DisplayName("取消订单：待付款状态取消不退款")
    void testUserCancelById_pendingPaymentNoRefund() throws Exception {
        Orders order = Orders.builder()
                .id(1L)
                .number("1001")
                .status(Orders.PENDING_PAYMENT) // 1 = 待付款
                .build();
        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.userCancelById(1L);

        // 待付款取消不触发退款
        verify(weChatPayUtil, never()).refund(anyString(), anyString(), any(), any());

        // 更新状态为已取消，但不设置退款
        verify(orderMapper, times(1)).update(argThat(o ->
                o.getStatus().equals(Orders.CANCELLED)
                        && o.getPayStatus() == null
        ));
    }

    // ==================== 支付成功 ====================

    @Test
    @DisplayName("支付成功：更新订单为待接单 + 记录交易号 + 发送 MQ 来单提醒消息")
    void testPaySuccess() {
        Orders orderDB = Orders.builder()
                .id(1L)
                .number("1001")
                .payStatus(Orders.UN_PAID)
                .build();
        when(orderMapper.getByNumber("1001")).thenReturn(orderDB);

        orderService.paySuccess("1001", "wxTxn123456");

        // 更新订单状态为待接单、支付状态为已支付、记录微信交易号
        verify(orderMapper, times(1)).update(argThat(o ->
                o.getStatus().equals(Orders.TO_BE_CONFIRMED)
                        && o.getPayStatus().equals(Orders.PAID)
                        && "wxTxn123456".equals(o.getTransactionId())
        ));

        // 发送 MQ 来单提醒消息（异步化，而非直接 WebSocket 推送）；第 4 参为 MessagePostProcessor（注入 traceId）
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq("order.notify.exchange"),
                eq("order.notify.routingKey"),
                anyString(),
                any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("支付成功：订单不存在抛异常")
    void testPaySuccess_orderNotFound() {
        when(orderMapper.getByNumber("1001")).thenReturn(null);

        assertThrows(OrderBusinessException.class,
                () -> orderService.paySuccess("1001", "wxTxn123456"));

        verify(orderMapper, never()).update(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("支付成功：重复回调（已支付）幂等，不重复处理")
    void testPaySuccess_idempotentOnRepeatCallback() {
        Orders orderDB = Orders.builder()
                .id(1L)
                .number("1001")
                .payStatus(Orders.PAID) // 已支付，说明是微信重复回调
                .build();
        when(orderMapper.getByNumber("1001")).thenReturn(orderDB);

        orderService.paySuccess("1001", "wxTxn123456");

        // 幂等：不重复更新、不重复发消息
        verify(orderMapper, never()).update(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), anyString());
    }

    // ==================== 再来一单 ====================

    @Test
    @DisplayName("再来一单：订单明细转购物车并批量插入")
    void testRepetition() {
        BaseContext.setCurrentId(1L);

        OrderDetail detail1 = OrderDetail.builder()
                .id(101L)
                .name("宫保鸡丁")
                .number(2)
                .amount(new java.math.BigDecimal("38.00"))
                .build();
        OrderDetail detail2 = OrderDetail.builder()
                .id(102L)
                .name("米饭")
                .number(1)
                .amount(new java.math.BigDecimal("2.00"))
                .build();
        List<OrderDetail> details = Arrays.asList(detail1, detail2);
        when(orderDetailMapper.getByOrderId(1L)).thenReturn(details);

        orderService.repetition(1L);

        // 批量插入购物车，且每个购物车 userId 为当前用户、id 被清空（复制时排除 id）
        verify(shoppingCartMapper, times(1)).insertBatch(argThat(list -> {
            if (list == null || list.size() != 2) {
                return false;
            }
            return list.stream().allMatch(c ->
                    c.getUserId().equals(1L) && c.getId() == null
            );
        }));
    }
}
