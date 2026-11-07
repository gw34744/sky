package com.sky.enumeration;

import com.sky.constant.MessageConstant;
import com.sky.exception.OrderBusinessException;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机（商家侧主流程状态）
 *
 * <p>把订单状态的「合法流转关系」集中定义在枚举中，替代原先散落在各 Service 方法里的
 * {@code if (status != 期望状态) throw ...} 手写判断。这样：</p>
 * <ul>
 *   <li>状态机规则 <b>集中、可读、可测试</b>，一眼看清订单全生命周期的合法流转；</li>
 *   <li>新增状态/调整流转规则只需改这一处，避免遗漏；</li>
 *   <li>业务方法调用 {@link #checkTransition(OrderStatus, OrderStatus)} 统一校验，
 *       非法流转统一抛异常，避免每个方法重复写校验逻辑。</li>
 * </ul>
 *
 * <p><b>状态码说明</b>：1~6 与实体 {@code Orders} 中的状态常量（PENDING_PAYMENT / TO_BE_CONFIRMED /
 * CONFIRMED / DELIVERY_IN_PROGRESS / COMPLETED / CANCELLED）保持一致。此处用字面量自持状态码，
 * 使状态机保持纯逻辑、不反向依赖实体类（本类位于 sky-common，实体位于 sky-pojo，避免模块耦合）。</p>
 *
 * <p>状态流转图（主流程）：</p>
 * <pre>
 * 待付款(1) ──支付──▶ 待接单(2) ──接单──▶ 已接单(3) ──派送──▶ 派送中(4) ──完成──▶ 已完成(5)
 *    │                    │ 拒单                 │ 取消
 *    │取消                 ▼                     ▼
 *    └───────────────▶ 已取消(6) ◀───────────────┘
 * </pre>
 */
public enum OrderStatus {

    /** 待付款 */
    PENDING_PAYMENT(1),
    /** 待接单 */
    TO_BE_CONFIRMED(2),
    /** 已接单（备餐中） */
    CONFIRMED(3),
    /** 派送中 */
    DELIVERY_IN_PROGRESS(4),
    /** 已完成（终态） */
    COMPLETED(5),
    /** 已取消（终态） */
    CANCELLED(6);

    /** 状态码（与实体 Orders 的状态常量保持一致） */
    private final int code;

    OrderStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 状态码 → 枚举 */
    public static OrderStatus of(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知订单状态码：" + code);
    }

    /** 每个状态「允许流转到的目标状态」集合（状态机核心：合法流转表） */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = buildTransitions();

    private static Map<OrderStatus, Set<OrderStatus>> buildTransitions() {
        Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);

        map.put(PENDING_PAYMENT, setOf(TO_BE_CONFIRMED, CANCELLED));      // 付款→待接单；未付款取消
        map.put(TO_BE_CONFIRMED, setOf(CONFIRMED, CANCELLED));            // 待接单→接单/拒单(取消)
        map.put(CONFIRMED, setOf(DELIVERY_IN_PROGRESS, CANCELLED));       // 已接单→派送/取消
        map.put(DELIVERY_IN_PROGRESS, setOf(COMPLETED));                  // 派送中→完成
        map.put(COMPLETED, setOf());                                      // 终态
        map.put(CANCELLED, setOf());                                      // 终态

        return map;
    }

    private static Set<OrderStatus> setOf(OrderStatus... statuses) {
        return new HashSet<>(Arrays.asList(statuses));
    }

    /**
     * 校验状态流转是否合法。
     *
     * @param current 当前状态
     * @param target  目标状态
     * @return 合法返回 true，非法返回 false（由调用方决定抛异常或降级）
     */
    public static boolean canTransition(OrderStatus current, OrderStatus target) {
        if (current == null || target == null) {
            return false;
        }
        Set<OrderStatus> allowed = TRANSITIONS.get(current);
        return allowed != null && allowed.contains(target);
    }

    /**
     * 校验状态流转是否合法，非法抛异常（配合全局异常处理器返回友好提示）。
     *
     * @param current 当前状态
     * @param target  目标状态
     * @throws OrderBusinessException 非法流转时抛出
     */
    public static void checkTransition(OrderStatus current, OrderStatus target) {
        if (!canTransition(current, target)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }
}
