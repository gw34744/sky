# WebSocket 消息与日志规范（订单全流程状态推送）

> 本文档约定苍穹外卖项目中 WebSocket 的消息协议、日志规范，以及订单从「接单 → 出餐 → 骑手运送 → 完成」全流程的状态推送设计。

---

## 一、背景与设计目标

原项目 WebSocket 存在以下问题：

1. **消息无规范**：用 `HashMap` + 硬编码数字 `type`（1=来单、2=催单），无消息结构、无时间戳、无订单状态字段。
2. **只群发、不定向**：`sendToAllClient` 一条消息推给所有连接，无法按「用户 / 订单」定向推送，管理端与用户端混在一起。
3. **状态流转不推送**：`confirm`（接单）、`delivery`（派送）、`complete`（完成）三个方法**没接 WebSocket**，用户端看不到订单状态实时变化。
4. **`complete()` 严重 bug**：`orders.setId(orders.getId())` 自己给自己赋值，导致 `id` 恒为 null，完成订单永远失败。
5. **`deliveryStatus`（1立即送出 / 0预定时间）字段未接入**，用户选择的配送方式没有在推送中体现。
6. **日志不规范**：`System.out.println`、`e.printStackTrace()`，生产环境应统一走 slf4j。

本次改造目标：**统一消息协议 + 定向推送 + 全流程状态推送 + 日志规范化 + 修复 complete bug**。

---

## 二、消息协议规范

### 2.1 连接地址约定

```
ws://host/ws/{sid}
```

`sid` 为业务标识：
- **用户端**：以 `userId` 作为 sid，用于接收「订单状态变更」定向推送。
- **管理端**：以固定标识（如 `admin`）连接，用于接收「来单提醒」「催单提醒」群发。

### 2.2 消息类型（type）

| type | 常量 | 含义 | 推送方向 | 推送方式 |
|------|------|------|----------|----------|
| 1 | `TYPE_ORDER_COMING` | 新订单来单提醒 | 服务端 → 管理端 | 群发 |
| 2 | `TYPE_REMINDER` | 用户催单提醒 | 服务端 → 管理端 | 群发 |
| 3 | `TYPE_STATUS_CHANGE` | 订单状态变更 | 服务端 → 下单用户 | 定向（按 userId） |
| 0 | （保留） | 服务端心跳 | 服务端 → 全部 | 群发 |

### 2.3 统一 JSON 消息结构

```json
{
  "type": 3,
  "orderId": 123,
  "orderNumber": "20261107...",
  "status": 3,
  "deliveryStatus": 1,
  "content": "商家已接单，正在备餐",
  "timestamp": 1730000000000
}
```

字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | Integer | 消息类型，见 2.2 |
| `orderId` | Long | 订单主键 id |
| `orderNumber` | String | 订单号 |
| `status` | Integer | 订单状态，见 2.4 |
| `deliveryStatus` | Integer | 配送方式：1 立即送出、0 预定时间 |
| `content` | String | 展示文案 |
| `timestamp` | Long | 服务端时间戳（毫秒） |

### 2.4 订单状态（status）与配送方式（deliveryStatus）

订单状态（复用 `Orders` 常量）：

| status | 常量 | 含义 |
|--------|------|------|
| 1 | `PENDING_PAYMENT` | 待付款 |
| 2 | `TO_BE_CONFIRMED` | 待接单 |
| 3 | `CONFIRMED` | 已接单（备餐/出餐中） |
| 4 | `DELIVERY_IN_PROGRESS` | 派送中（骑手运送） |
| 5 | `COMPLETED` | 已完成 |
| 6 | `CANCELLED` | 已取消 |

配送方式（`deliveryStatus`）：

| deliveryStatus | 含义 |
|----------------|------|
| 1 | 立即送出 |
| 0 | 预定时间送出 |

---

## 三、订单全流程状态推送

从商家视角，订单状态流转路径为：

```
待付款(1) --支付成功--> 待接单(2) --接单--> 已接单/备餐(3) --出餐/派送--> 派送中(4) --送达--> 已完成(5)
```

每一步的状态变更，都触发一次 WebSocket 推送（`type=3`，定向给下单用户）。

| 触发方法 | 状态变更 | 推送文案（content） | 说明 |
|----------|----------|---------------------|------|
| `paySuccess` | 1 → 2 | `新订单来单，请及时接单` | type=1 群发管理端 |
| `confirm`（接单） | 2 → 3 | `商家已接单，正在备餐` | type=3 定向用户 |
| `delivery`（派送） | 3 → 4 | `骑手已取餐，正在配送中` 或 `订单已按预定时间送出，骑手配送中` | 按 deliveryStatus 区分文案 |
| `complete`（完成） | 4 → 5 | `订单已送达，用餐愉快` | type=3 定向用户 |
| `rejection`（拒单） | 2 → 6 | `订单已被商家拒绝：{原因}` | type=3 定向用户 |
| `reminder`（催单） | 不变 | `用户催单，请尽快处理` | type=2 群发管理端 |

> **关于「立即配送」与「预定时间」**：用户下单时通过 `deliveryStatus` 字段选择配送方式（1 立即送出 / 0 预定时间）。该字段贯穿全程推送，前端可据此差异化展示：
> - `deliveryStatus=1`：实时显示「骑手配送中」。
> - `deliveryStatus=0`：显示「已按预定时间安排配送」，在预计送达时间前展示倒计时。

---

## 四、日志规范

WebSocket 相关日志统一使用 slf4j，禁止 `System.out.println` / `e.printStackTrace()`。

### 4.1 日志级别

| 场景 | 级别 | 示例 |
|------|------|------|
| 连接建立/断开 | INFO | `[WebSocket] 建立连接，sid={}，sessionId={}` |
| 群发/定向推送 | INFO | `[WebSocket] 群发消息，在线客户端数={}，message={}` |
| 目标客户端不在线（定向） | INFO | `[WebSocket] 目标客户端不在线，忽略推送，sid={}` |
| 连接异常 / 发送失败 | ERROR | `[WebSocket] 消息发送失败，sessionId={}` |
| 心跳推送 | DEBUG | `[WebSocket] 心跳推送完成，在线客户端数={}` |

### 4.2 统一前缀

所有 WebSocket 日志统一以 `[WebSocket]` 前缀开头，便于 grep 过滤。

### 4.3 日志原则

1. **发送失败要记完整堆栈**：用 `log.error(..., e)` 而非 `printStackTrace`。
2. **异常连接主动清理**：`@OnError` 时移除失效会话，避免残留僵尸连接。
3. **推送关键信息**：群发记录在线客户端数、消息内容；定向记录目标 sid，便于排查「用户收不到推送」问题。

---

## 五、并发安全

会话集合 `SESSION_MAP` 从 `HashMap` 改为 `ConcurrentHashMap`：

- 多个业务线程可能同时调用 `sendToAllClient` / `sendToUser` 遍历集合。
- 普通 `HashMap` 在并发遍历 + 增删时可能抛 `ConcurrentModificationException`。
- `ConcurrentHashMap` 保证并发安全，且 `sendToUser` 的 `get` 天然线程安全。

---

## 六、改造清单

| 文件 | 改动 |
|------|------|
| `WsMessageConstant.java`（新增） | 消息类型 / 配送方式常量，消除魔法数字 |
| `WebSocketServer.java` | 日志规范化 + `@OnError` + `sendToUser` 定向推送 + `ConcurrentHashMap` |
| `WebSocketTask.java` | 心跳消息日志规范化 |
| `OrderServiceImpl.java` | `confirm`/`delivery`/`complete`/`rejection` 增加状态推送；`complete` 修复 id bug；`delivery` 接入 deliveryStatus；抽取 `pushStatus`/`buildMessage` 统一构建 |

### 6.1 `complete()` 修复说明（重要）

修复前：

```java
orders.setId(orders.getId());        // bug：自己赋值自己，id 恒为 null
orders.setStatus(Orders.CONFIRMED);  // bug：完成订单状态写成了「已接单」，应为「已完成」
```

修复后：

```java
orders.setId(orderDB.getId());       // 用查询出来的订单 id
orders.setStatus(Orders.COMPLETED);  // 完成订单状态应为 5 已完成
```

---

## 七、面试要点（WebSocket 相关）

1. **为什么用 WebSocket 而不是轮询？**
   - 轮询（HTTP 定时拉取）浪费带宽、延迟高、服务器压力大；WebSocket 是长连接，服务端可主动推送，实时性高、开销小。
2. **如何做定向推送？**
   - 连接时以 `userId` 作为 sid 建立 `sid → Session` 映射，推送时按 sid 定位 Session 定向发送；不在线则忽略（可配合消息落库补发）。
3. **为什么用 `ConcurrentHashMap`？**
   - 多线程并发推送遍历会话集合，普通 HashMap 会抛 `ConcurrentModificationException`，需并发安全容器。
4. **群发与定向的区别场景？**
   - 管理端提醒（来单、催单）用群发；用户端状态变更用定向（按 userId），避免用户收到他人订单消息。
5. **离线用户如何保证消息不丢？**
   - 定向推送前判断 sid 是否在线；不在线时可将消息落库（未读消息表），用户上线后拉取补发（本项目暂未实现，可作为后续优化点）。
