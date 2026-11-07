package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 下单接口幂等单元测试（Controller 层）
 *
 * <p>覆盖点：
 * <ul>
 *     <li>token 缺失 → 拒绝，返回「请勿重复提交订单」</li>
 *     <li>token 与缓存不一致（被篡改/已用）→ 拒绝</li>
 *     <li>token 一致 → 删除 token 并正常下单</li>
 *     <li>重复提交（token 已被删除，缓存为空）→ 拒绝</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private RedisTemplate redisTemplate;

    @Mock
    private ValueOperations valueOperations;

    @InjectMocks
    private OrderController orderController;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 直接设置 ThreadLocal 用户上下文，模拟当前登录用户 id = 1001
        BaseContext.setCurrentId(1001L);
    }

    @AfterEach
    void tearDown() {
        // 清理 ThreadLocal，避免污染其他测试
        BaseContext.removeCurrentId();
    }

    @Test
    @DisplayName("下单：token 缺失，拒绝请求")
    void testSubmit_tokenMissing() {
        when(valueOperations.get("submit_token_1001")).thenReturn("valid-token");

        Result<OrderSubmitVO> result = orderController.submit(buildDTO(), null);

        assertEquals(0, result.getCode());
        assertEquals("请勿重复提交订单", result.getMsg());
        // token 缺失，不删除、不下单
        verify(redisTemplate, never()).delete(anyString());
        verify(orderService, never()).submitOrder(any());
    }

    @Test
    @DisplayName("下单：token 与缓存不一致（被篡改），拒绝请求")
    void testSubmit_tokenMismatch() {
        when(valueOperations.get("submit_token_1001")).thenReturn("correct-token");

        Result<OrderSubmitVO> result = orderController.submit(buildDTO(), "wrong-token");

        assertEquals(0, result.getCode());
        assertEquals("请勿重复提交订单", result.getMsg());
        verify(redisTemplate, never()).delete(anyString());
        verify(orderService, never()).submitOrder(any());
    }

    @Test
    @DisplayName("下单：token 一致，删除 token 并正常下单")
    void testSubmit_success() {
        when(valueOperations.get("submit_token_1001")).thenReturn("valid-token");
        when(redisTemplate.delete("submit_token_1001")).thenReturn(true);
        OrderSubmitVO vo = new OrderSubmitVO();
        when(orderService.submitOrder(any(OrdersSubmitDTO.class))).thenReturn(vo);

        Result<OrderSubmitVO> result = orderController.submit(buildDTO(), "valid-token");

        assertEquals(1, result.getCode());
        // 校验通过后删除 token（一次性），并正常下单
        verify(redisTemplate, times(1)).delete("submit_token_1001");
        verify(orderService, times(1)).submitOrder(any(OrdersSubmitDTO.class));
    }

    @Test
    @DisplayName("下单：重复提交（token 已被使用，缓存为空），拒绝请求")
    void testSubmit_repeatSubmit() {
        // token 已在上一次被删除，缓存里没有 → get 返回 null
        when(valueOperations.get("submit_token_1001")).thenReturn(null);

        Result<OrderSubmitVO> result = orderController.submit(buildDTO(), "valid-token");

        assertEquals(0, result.getCode());
        assertEquals("请勿重复提交订单", result.getMsg());
        verify(orderService, never()).submitOrder(any());
    }

    private OrdersSubmitDTO buildDTO() {
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);
        dto.setAmount(new BigDecimal("39.90"));
        return dto;
    }
}
