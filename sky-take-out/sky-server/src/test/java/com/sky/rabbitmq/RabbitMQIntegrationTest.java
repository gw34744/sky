package com.sky.rabbitmq;

import com.sky.config.RabbitMQConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RabbitMQ 集成测试（不依赖 MySQL / Redis）
 *
 * <p>直接连接本机 RabbitMQ（localhost:5672，guest/guest），验证：
 * <ol>
 *     <li>连接是否建立</li>
 *     <li>交换机的声明</li>
 *     <li>消息发送</li>
 *     <li>消息消费（同步 receive）</li>
 * </ol>
 *
 * <p><b>为什么用独立测试队列：</b>生产队列 {@code order.notify.queue} 绑定在正在运行的
 * SkyApplication 上，其 {@code OrderNotifyListener} 会立刻消费任何进队列的消息，
 * 导致测试的同步 receive 永远等不到消息。故本测试使用一个带随机后缀的独立队列，
 * 完整验证「声明 → 绑定 → 发送 → 消费」链路，且不干扰运行中的服务。</p>
 */
class RabbitMQIntegrationTest {

    private CachingConnectionFactory factory;

    /** 测试专用队列（随机后缀，避免与生产队列/消费者冲突） */
    private final String testQueue = "test.notify.queue." + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanup() {
        if (factory != null) {
            try {
                new RabbitAdmin(factory).deleteQueue(testQueue);
            } catch (Exception ignore) {
                // 清理失败不阻塞测试结果
            }
            factory.destroy();
        }
    }

    @Test
    @DisplayName("RabbitMQ 连接 + 交换机声明 + 发送 + 消费 全链路验证")
    void testRabbitMQFullChain() {
        // 1. 连接工厂（与本机 RabbitMQ 一致）
        factory = new CachingConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");

        // 2. 声明交换机 + 独立测试队列 + 绑定（复用项目真实的交换机/路由键，验证拓扑配置正确）
        RabbitAdmin admin = new RabbitAdmin(factory);
        DirectExchange exchange = new DirectExchange(
                RabbitMQConfiguration.ORDER_NOTIFY_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(testQueue).build();
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange)
                .with(RabbitMQConfiguration.ORDER_NOTIFY_ROUTING_KEY));

        // 3. 发送一条模拟「支付成功来单提醒」消息（byte[] 发送，绕过 converter 序列化歧义）
        RabbitTemplate template = new RabbitTemplate(factory);
        String payload = "{\"type\":1,\"orderId\":1,\"orderNumber\":\"20261107TEST\",\"content\":\"新订单来单，请及时接单\"}";
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        template.send(RabbitMQConfiguration.ORDER_NOTIFY_EXCHANGE,
                RabbitMQConfiguration.ORDER_NOTIFY_ROUTING_KEY,
                new Message(body, new org.springframework.amqp.core.MessageProperties()));

        // 4. 从独立测试队列同步接收（无其他消费者争抢，一定能收到）
        Message received = template.receive(testQueue, 5000);
        assertNotNull(received, "5 秒内未收到消息，消费失败");
        String receivedBody = new String(received.getBody(), StandardCharsets.UTF_8);
        assertEquals(payload, receivedBody, "收到的消息内容与发送不一致");

        System.out.println("[RabbitMQ 集成测试] 通过：连接/声明/绑定/发送/消费全链路 OK，收到：" + receivedBody);
    }
}
