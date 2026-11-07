# MQ 面试笔记（RabbitMQ + 支付回调异步化）

> 苍穹外卖项目引入 RabbitMQ 的场景、设计与面试要点。

---

## 一、为什么在这个项目加 MQ？

先明确一个原则：**加 MQ 是为了解决真实问题，不是为了凑技术点**。

本项目唯一的真实痛点：**支付回调的响应速度**。

- 微信支付要求回调接口快速返回 `SUCCESS`，否则会判定超时并重试。
- 原来的 `paySuccess` 在回调线程里同步做「改订单状态 + 推 WebSocket 来单提醒」。
- 一旦"支付成功后的后续动作"变重（发短信、派单、生成电子发票、消息推送等），同步执行会拖慢回调响应，导致微信超时重试甚至重复回调。

**解法**：回调只做「最小必要操作」（幂等校验 + 落库），把「来单提醒」等后续动作通过 MQ 异步解耦。

```
微信回调 → 幂等校验 + 落库（快）→ 发送 MQ 消息 → 返回 SUCCESS
                                              ↓ 异步
                                     消费者推送 WebSocket
```

## 二、核心设计

### 1. 拓扑

```
生产者(支付回调) --convertAndSend--> Direct交换机 order.notify.exchange
                                         |
                                  路由键 order.notify.routingKey
                                         |
                                  队列 order.notify.queue
                                         |
                                 消费者 OrderNotifyListener → 推送 WebSocket
```

### 2. 为什么选 Direct 交换机？

- 点对点：一个路由键精确匹配一个队列，语义清晰。
- 本场景是「1 个生产者 → 1 个消费者」，无需 Fanout 广播或 Topic 模糊匹配。

### 3. 可靠性四件套

| 机制 | 配置/实现 | 作用 |
|------|-----------|------|
| **生产者确认** | `publisher-confirm-type: correlated` | 确认消息到达交换机，未到达可重发 |
| **路由失败返回** | `publisher-returns: true` | 消息未路由到队列时回调，避免丢消息 |
| **消费端手动 ACK** | `acknowledge-mode: manual` | 处理成功才 basicAck，失败 basicNack 重试 |
| **死信队列** | 队列绑定 DLX | 重试超限的消息进入死信队列，人工补偿，不丢消息 |

### 4. 幂等

- 生产端：`paySuccess` 已有幂等判断（`payStatus==PAID` 直接 return），重复回调不会重复发消息。
- 消费端：WebSocket 推送本身幂等（重复推只是客户端多收一次提醒，可接受），且消费端手动 ACK 保证消息不重复消费。

### 5. 持久化

队列用 `QueueBuilder.durable` 声明为持久化，交换机 durable=true，配合持久化消息，RabbitMQ 重启后消息不丢。

## 三、为什么不用 Spring Task 定时扫描替代？

| 维度 | Spring Task（定时扫描） | MQ（消息驱动） |
|------|------------------------|----------------|
| 实时性 | 定时轮询，有延迟（秒级~分钟级） | 事件驱动，毫秒级 |
| 解耦 | 业务耦合在定时任务里 | 生产者/消费者完全解耦 |
| 削峰 | 无 | 队列天然缓冲流量 |
| 复杂度 | 简单 | 需运维 MQ |

> 本项目「支付超时关单」仍用 Spring Task 定时扫描（`processTimeoutOrder`），因为它是「到点批量处理」的延迟场景，轮询合适；而「支付成功通知」是「事件实时驱动」，用 MQ 更合适。**两种机制各司其职，是很好的面试论据。**

## 四、MQ 选型对比（加分项）

| MQ | 特点 | 适用 |
|----|------|------|
| **RabbitMQ** | 功能全、AMQP 协议、可靠性高、延迟低 | 通用、金融、需要复杂路由 |
| **RocketMQ** | 阿里系、吞吐高、支持事务消息/延迟消息、顺序消息 | 电商、大流量、金融级 |
| **Kafka** | 超高吞吐、持久化、适合日志/大数据流 | 日志采集、埋点、流处理 |

> 本项目选 RabbitMQ：Spring Boot 集成最顺（`spring-boot-starter-amqp`），功能满足，学习曲线平缓。

## 五、面试高频问答

**Q1：消息丢失怎么办？**
生产端 confirm + 队列/消息持久化 + 消费端手动 ACK，三段保证不丢。

**Q2：消息重复消费怎么办？**
消费端做幂等（本项目 WebSocket 推送天然幂等；更严谨场景用消息唯一 id + 去重表）。

**Q3：消息积压怎么办？**
临时扩容消费者、增加 prefetch、排查慢消费；治本靠提高消费速度或削峰。

**Q4：怎么保证顺序？**
单队列单消费者天然有序；多消费者并发则需分区/路由键按业务维度保证局部有序。

**Q5：为什么支付回调要异步化？**
回调要快速返回 SUCCESS 避免超时重试；用 MQ 把「核心落库」和「后续通知」解耦，即使后续动作失败也不影响主流程（可重试/补偿）。

## 六、改动清单

| 文件 | 改动 |
|------|------|
| `sky-server/pom.xml` | 加 `spring-boot-starter-amqp` |
| `application.yml` | RabbitMQ 连接 + confirm/returns/manual-ack/retry 配置 |
| `application-dev.yml` | RabbitMQ host/port/账号 |
| `RabbitMQConfiguration.java`（新增） | 交换机/队列/死信队列/绑定声明 |
| `OrderServiceImpl.java` | `paySuccess` 改为发 MQ 消息（异步化） |
| `OrderNotifyListener.java`（新增） | 消费者监听，手动 ACK + 推 WebSocket |
| `OrderServiceImplTest.java` | 适配：WebSocket mock → RabbitTemplate mock |

## 七、运行前提（重要）

需要本机有 RabbitMQ（代码已就绪，缺的是运行环境）：

```bash
# 方式一：Docker（推荐，本机已装 Docker）
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
# 默认账号 guest/guest，管理台 http://localhost:15672

# 方式二：Windows 本机安装 RabbitMQ（需先装 Erlang）
```

启动前需确认 Docker Desktop 已启动（本机当前 Docker 引擎未运行，需手动打开 Docker Desktop）。
