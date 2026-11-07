package com.sky.enumeration;

import com.sky.exception.OrderBusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单状态机单元测试
 *
 * <p>直接验证状态机的合法流转与非法流转，确保状态机规则正确、可回归。
 * 这是状态机「集中定义规则」的价值体现：业务方法只需调用 checkTransition，
 * 规则是否正确由本测试单独保证。</p>
 */
class OrderStatusTest {

    @Test
    @DisplayName("主流程合法流转：待付款→待接单→已接单→派送中→已完成")
    void testMainFlowTransitions() {
        assertTrue(OrderStatus.canTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.TO_BE_CONFIRMED));
        assertTrue(OrderStatus.canTransition(OrderStatus.TO_BE_CONFIRMED, OrderStatus.CONFIRMED));
        assertTrue(OrderStatus.canTransition(OrderStatus.CONFIRMED, OrderStatus.DELIVERY_IN_PROGRESS));
        assertTrue(OrderStatus.canTransition(OrderStatus.DELIVERY_IN_PROGRESS, OrderStatus.COMPLETED));
    }

    @Test
    @DisplayName("取消分支：待付款/待接单/已接单均可取消")
    void testCancelTransitions() {
        assertTrue(OrderStatus.canTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED));
        assertTrue(OrderStatus.canTransition(OrderStatus.TO_BE_CONFIRMED, OrderStatus.CANCELLED));
        assertTrue(OrderStatus.canTransition(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("终态不可再流转：已完成/已取消没有合法出口")
    void testTerminalStatesNoExit() {
        assertFalse(OrderStatus.canTransition(OrderStatus.COMPLETED, OrderStatus.CONFIRMED));
        assertFalse(OrderStatus.canTransition(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        assertFalse(OrderStatus.canTransition(OrderStatus.CANCELLED, OrderStatus.CONFIRMED));
        assertFalse(OrderStatus.canTransition(OrderStatus.CANCELLED, OrderStatus.COMPLETED));
    }

    @Test
    @DisplayName("非法跳转被拒绝：已接单不能直接完成，派送中不能回退到已接单")
    void testIllegalTransitionsRejected() {
        assertFalse(OrderStatus.canTransition(OrderStatus.CONFIRMED, OrderStatus.COMPLETED));
        assertFalse(OrderStatus.canTransition(OrderStatus.DELIVERY_IN_PROGRESS, OrderStatus.CONFIRMED));
        assertFalse(OrderStatus.canTransition(OrderStatus.TO_BE_CONFIRMED, OrderStatus.DELIVERY_IN_PROGRESS));
        assertFalse(OrderStatus.canTransition(OrderStatus.COMPLETED, OrderStatus.PENDING_PAYMENT));
    }

    @Test
    @DisplayName("checkTransition 非法流转抛业务异常")
    void testCheckTransitionThrowsOnIllegal() {
        assertThrows(OrderBusinessException.class,
                () -> OrderStatus.checkTransition(OrderStatus.CONFIRMED, OrderStatus.COMPLETED));
    }

    @Test
    @DisplayName("checkTransition 合法流转不抛异常")
    void testCheckTransitionPassesOnLegal() {
        assertDoesNotThrow(() -> OrderStatus.checkTransition(OrderStatus.TO_BE_CONFIRMED, OrderStatus.CONFIRMED));
    }

    @Test
    @DisplayName("状态码与枚举互转")
    void testCodeMapping() {
        assertEquals(OrderStatus.TO_BE_CONFIRMED, OrderStatus.of(2));
        assertEquals(OrderStatus.COMPLETED, OrderStatus.of(5));
        assertEquals(2, OrderStatus.TO_BE_CONFIRMED.getCode());
        assertThrows(IllegalArgumentException.class, () -> OrderStatus.of(999));
    }
}
