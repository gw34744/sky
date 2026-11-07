package com.sky.rabbitmq;

import com.sky.config.RabbitMQConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RabbitMQ 集成测试（不依赖 MySQL / Redis）
 *
 * <p>直接连接本机 RabbitMQ（localhost:5672，guest/guest），验证：
 * <ol>
 *     <li>连接是否建立</li>
 *     <li>项目真实拓扑（order.notify.exchange / order.notify.queue）声明与绑定</li>
 *     <li>消息发送</li>
 *     <li>消息消费（轮询接收）</li>
 * </ol>
 */
class RabbitMQIntegrationTest {

    @Test
    @DisplayName("RabbitMQ 连接 + 项目真实拓扑声明 + 发送 + 消费 全链路验证")
    void testRabbitMQFullChain() {
        // 1. 连接工厂（与本机 RabbitMQ 一致）
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");

        // 2. 声明「项目真实配置」的交换机、队列、绑定
        RabbitAdmin admin = new RabbitAdmin(factory);
        DirectExchange exchange = new DirectExchange(
                RabbitMQConfiguration.ORDER_NOTIFY_EXCHANGE, true, false);
        Queue queue = new Queue(RabbitMQConfiguration.ORDER_NOTIFY_QUEUE, true);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange)
                .with(RabbitMQConfiguration.ORDER_NOTIFY_ROUTING_KEY));

        // 3. 发送一条模拟「支付成功来单提醒」消息
        RabbitTemplate template = new RabbitTemplate(factory);
        String payload = "{\"type\":1,\"orderId\":1,\"orderNumber\":\"20261107TEST\",\"content\":\"新订单来单，请及时接单\"}";
        template.convertAndSend(RabbitMQConfiguration.ORDER_NOTIFY_EXCHANGE,
                RabbitMQConfiguration.ORDER_NOTIFY_ROUTING_KEY, payload);

        // 4. 消费消息（同步 receive，验证消息确实进队列且可消费）
        Message received = template.receive(RabbitMQConfiguration.ORDER_NOTIFY_QUEUE, 5000);
        assertNotNull(received, "5 秒内未收到消息，消费失败");
        String body = new String(received.getBody(), StandardCharsets.UTF_8);
        assertEquals(payload, body, "收到的消息内容与发送不一致");

        System.out.println("[RabbitMQ 集成测试] 通过：项目真实拓扑连接/声明/发送/消费全链路 OK，收到：" + body);
    }
}
