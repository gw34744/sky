package com.sky.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列 / 交换机 / 绑定配置
 *
 * <p>用于支付回调异步化：支付回调只做「幂等校验 + 落库」，然后发送一条「来单提醒」消息到 MQ，
 * 由消费者异步消费并推送 WebSocket，避免后续动作变重时拖慢微信回调响应。
 *
 * <p>拓扑：
 * <pre>
 * 生产者(支付回调) --发送--> 交换机 order.notify.exchange (Direct) --路由key order.notify.routingKey--> 队列 order.notify.queue
 * </pre>
 */
@Configuration
public class RabbitMQConfiguration {

    /** 交换机名称 */
    public static final String ORDER_NOTIFY_EXCHANGE = "order.notify.exchange";

    /** 来单提醒队列 */
    public static final String ORDER_NOTIFY_QUEUE = "order.notify.queue";

    /** 来单提醒死信队列（消费失败超限后进入，便于人工排查/补偿） */
    public static final String ORDER_NOTIFY_DLQ = "order.notify.dlq";

    /** 死信交换机 */
    public static final String ORDER_NOTIFY_DLX_EXCHANGE = "order.notify.dlx.exchange";

    /** 路由键 */
    public static final String ORDER_NOTIFY_ROUTING_KEY = "order.notify.routingKey";

    /** 死信路由键 */
    public static final String ORDER_NOTIFY_DLQ_ROUTING_KEY = "order.notify.dlq.routingKey";

    /**
     * 直连交换机
     */
    @Bean
    public DirectExchange orderNotifyExchange() {
        return new DirectExchange(ORDER_NOTIFY_EXCHANGE, true, false);
    }

    /**
     * 来单提醒队列（绑定死信交换机，消费失败超限的消息进入死信队列）
     */
    @Bean
    public Queue orderNotifyQueue() {
        return QueueBuilder.durable(ORDER_NOTIFY_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_NOTIFY_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_NOTIFY_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange orderNotifyDlxExchange() {
        return new DirectExchange(ORDER_NOTIFY_DLX_EXCHANGE, true, false);
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue orderNotifyDlq() {
        return QueueBuilder.durable(ORDER_NOTIFY_DLQ).build();
    }

    /**
     * 队列绑定交换机
     */
    @Bean
    public Binding orderNotifyBinding() {
        return BindingBuilder.bind(orderNotifyQueue())
                .to(orderNotifyExchange())
                .with(ORDER_NOTIFY_ROUTING_KEY);
    }

    /**
     * 死信队列绑定死信交换机
     */
    @Bean
    public Binding orderNotifyDlqBinding() {
        return BindingBuilder.bind(orderNotifyDlq())
                .to(orderNotifyDlxExchange())
                .with(ORDER_NOTIFY_DLQ_ROUTING_KEY);
    }
}
