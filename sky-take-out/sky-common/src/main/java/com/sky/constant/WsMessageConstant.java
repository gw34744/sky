package com.sky.constant;

/**
 * WebSocket 消息协议常量
 *
 * <p>统一 WebSocket 推送消息的类型（type）与配送方式（deliveryStatus）字段取值，
 * 避免在业务代码中散落魔法数字。前端根据 type 分支渲染，根据 status 显示订单状态。
 *
 * <p>推送消息统一 JSON 结构（见 {@code WebSocketServer} 的注释）：
 * <pre>
 * {
 *   "type": 3,                    // 消息类型，见下表
 *   "orderId": 123,               // 订单id
 *   "orderNumber": "2026...",     // 订单号
 *   "status": 3,                  // 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
 *   "deliveryStatus": 1,          // 配送方式 1立即送出 0预定时间
 *   "content": "订单已接单",       // 文案
 *   "timestamp": 1730000000000    // 服务端时间戳（毫秒）
 * }
 * </pre>
 */
public class WsMessageConstant {

    /** 消息类型：新订单来单提醒（支付成功，管理端弹窗接单） */
    public static final Integer TYPE_ORDER_COMING = 1;

    /** 消息类型：用户催单（管理端提醒） */
    public static final Integer TYPE_REMINDER = 2;

    /** 消息类型：订单状态变更（接单/出餐/派送/完成/取消，推送给用户端） */
    public static final Integer TYPE_STATUS_CHANGE = 3;

    /** 配送方式：立即送出 */
    public static final Integer DELIVERY_IMMEDIATE = 1;

    /** 配送方式：预定时间送出 */
    public static final Integer DELIVERY_SCHEDULED = 0;

    private WsMessageConstant() {
    }
}
