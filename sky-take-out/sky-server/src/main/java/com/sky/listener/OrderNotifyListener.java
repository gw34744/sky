package com.sky.listener;

import com.rabbitmq.client.Channel;
import com.sky.config.RabbitMQConfiguration;
import com.sky.context.TraceIdUtil;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 订单通知消息消费者
 *
 * <p>消费支付回调异步化后投递到 MQ 的「来单提醒」消息，异步推送 WebSocket 给管理端。
 * 将「推送」这类非核心、可能变重的动作从微信回调主流程中剥离，保证回调快速返回。
 *
 * <p><b>链路追踪</b>：MDC 基于 ThreadLocal，MQ 消费者运行在独立线程，天然拿不到
 * 生产者线程的 traceId。因此生产者把 traceId 写入消息头（X-Trace-Id），消费者此处
 * 从消息头取出并重新 {@code MDC.put}，结束时 {@code finally MDC.remove}，使
 * 「支付回调 → MQ 消费 → WebSocket 推送」整条异步链路的日志都能按同一个 traceId 串联。
 */
@Component
@Slf4j
public class OrderNotifyListener {

    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 监听来单提醒队列
     *
     * @param message 消息体（来单提醒 JSON，结构见 WsMessageConstant）
     * @param channel RabbitMQ 通道，用于手动 ACK
     */
    @RabbitListener(queues = RabbitMQConfiguration.ORDER_NOTIFY_QUEUE)
    public void onOrderComing(Message message, Channel channel) throws IOException {
        // 1. 从消息头恢复生产者线程的 traceId（跨线程传递，保证异步链路日志可串联）
        String traceId = message.getMessageProperties().getHeader(TraceIdUtil.TRACE_ID_HEADER);
        if (traceId != null) {
            MDC.put(TraceIdUtil.TRACE_ID, traceId);
        }

        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("[MQ] 收到来单提醒消息：{}", body);

        try {
            // 推送给管理端（群发）
            webSocketServer.sendToAllClient(body);
            // 处理成功，手动确认，消息出队
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("[MQ] 来单提醒消息处理失败，body={}", body, e);
            // 消费失败不确认（basicNack 重新入队），配合配置的重试，超限后进入死信队列
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        } finally {
            // 2. 清理 MDC，防止消费者线程池复用导致 traceId 串号
            MDC.remove(TraceIdUtil.TRACE_ID);
        }
    }
}
