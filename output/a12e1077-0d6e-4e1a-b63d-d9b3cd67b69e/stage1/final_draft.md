# 苍穹外卖项目 · 全内容教学技术报告

> 本报告基于苍穹外卖项目（Java 后端 + Vue 前端）在原始教学项目基础上所做的全部功能添加与改进，逐项拆解「为什么要改、怎么改、改完带来什么价值、面试怎么讲」，帮助读者完整吃透当前项目的技术深度与实现细节。

## TL;DR

这是一份面向「项目完整学习与面试准备」的系统性教学报告。它把项目从「黑马教学原版」到「中高级工程实践」的演进过程，按 15 个技术主题逐一讲解——从基础设施加固（事务、雪花算法、配置加密）到核心业务建模（订单状态机、支付回调幂等），再到可观测性（统一日志 + MDC 链路追踪）、安全（RBAC 权限体系）、高可用（限流熔断、缓存三防）、以及质量保障（63 个测试用例），每一块都包含「原缺陷 → 解决方案 → 核心代码 → 面试问答 → 落地文件」的完整闭环。

### 元信息

- 目标读者：中高级 Java 后端开发者（面试准备 / 项目复盘 / 技术进阶）
- 前置知识：Spring Boot、MyBatis、Redis、RabbitMQ、MySQL 基础
- 技术栈版本：Java 8、Spring Boot 2.7.3、MyBatis、Redis、RabbitMQ、JWT、jasypt 3.0.5、Resilience4j 1.7.1
- 预计阅读时间：约 45 分钟

---

## 一、项目全景：从教学项目到工程实践

### 1.1 项目定位与演进脉络

苍穹外卖是一个典型的「外卖点餐系统」，原始版本是黑马程序员的教学项目，采用「Java 后端 + Vue 前端」的分层架构。教学版已经具备基本的分层结构、缓存、认证等功能，但距离真实企业项目还有明显差距。

我们对项目做了系统性回顾（见《项目改进分析报告》），识别出若干「真实正确性问题」与「健壮性短板」，并按优先级逐一修复与补强。整个演进可以归纳为**六个方向、十五个主题**：

| 方向 | 主题 | 解决的核心问题 |
|------|------|---------------|
| 基础设施加固 | 下单事务、雪花算法、配置加密 | 数据一致性、并发唯一性、密钥安全 |
| 核心业务建模 | 订单状态机、支付回调幂等 | 业务规则集中化、回调可靠性 |
| 异步解耦 | RabbitMQ 支付回调异步化 | 回调响应速度、系统解耦 |
| 可观测性 | 统一日志 + MDC 链路追踪 | 日志检索、链路串联 |
| 安全体系 | RBAC 权限模型 | 细粒度访问控制 |
| 高可用 | 限流熔断、缓存三防、访问通道评估 | 流量防护、缓存可用性 |
| 质量保障 | 63 个测试用例、端到端集成测试 | 回归保障、信心验证 |

### 1.2 项目模块结构

项目采用 Maven 多模块划分，职责清晰：

```
sky-take-out
├── sky-common    # 通用模块：工具类、常量、枚举、上下文、注解
├── sky-pojo      # 实体模块：Entity、VO、DTO
└── sky-server    # 服务模块：Controller、Service、Mapper、配置、拦截器、监听器
```

**分层架构**（Controller → Service → Mapper），职责分明，是后续所有改进的基础。

### 1.3 关键设计原则（贯穿全部改进）

在动手之前，我们确立了几条贯穿始终的原则，这也是理解所有改进的「主线」：

1. **解决真实问题，不堆技术点**——每引入一个技术（MQ、限流、状态机）都必须有明确的问题驱动；
2. **最小侵入，渐进式演进**——尽量在不破坏存量代码的前提下补强（如 RBAC 对未标注接口向后兼容）；
3. **可测试、可验证**——每个改进都配套测试，用证据说话；
4. **安全意识内建**——密钥脱敏、日志脱敏、幂等设计，都是默认选项而非事后补救。

---

## 二、基础设施加固

### 2.1 下单事务：保证原子性

**原始缺陷**：`OrderServiceImpl.submitOrder` 有 3 次数据库写操作（插订单、批量插明细、清购物车），但方法上没有 `@Transactional`。若第 2/3 步失败，会产生「有订单无明细」或「订单生成但购物车未清」的脏数据。

**解决方案**：给 `submitOrder` 添加 `@Transactional` 注解，使三步操作成为一个原子单元。

```java
@Transactional
public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) { ... }
```

**核心知识**：
- **事务的 ACID 特性**：原子性（Atomicity）、一致性（Consistency）、隔离性（Isolation）、持久性（Durability）；
- **为什么下单必须原子**：订单、明细、购物车三者是强关联数据，任何一步失败都必须整体回滚。

**面试要点**：能讲出「事务失效的常见原因」——自调用（同类内部方法调用不走代理）、异常被吞（catch 后不抛出）、非 public 方法、数据库引擎不支持事务等。

### 2.2 雪花算法：订单号唯一性

**原始缺陷**：`order.setNumber(String.valueOf(System.currentTimeMillis()))` 用毫秒时间戳当订单号，高并发下同一毫秒多个请求会生成相同订单号，导致订单号冲突。

**解决方案**：新增 `SnowflakeIdUtil`（标准雪花算法），订单号改为 `SnowflakeIdUtil.nextIdStr()`。

雪花算法 64 位结构：

```
1 位符号位 | 41 位时间戳 | 10 位机器标识 | 12 位序列号
```

**核心特性**：
- **趋势递增**：利于数据库索引（B+ 树）；
- **分布式唯一**：机器标识 + 序列号保证不重复；
- **无第三方依赖**：纯本地生成，不依赖 Redis/数据库。

**面试要点**：额外处理了「时钟回拨」异常场景；能对比雪花算法与 UUID 的优劣（雪花递增利于索引，UUID 无序导致页分裂）。

### 2.3 配置加密：jasypt 密钥安全

**原始缺陷**：`application-dev.yml` 里数据库密码、阿里云 AccessKey、微信支付密钥全部明文，且随代码提交到 git，仓库一旦泄露则所有密钥暴露。

**解决方案**：引入 jasypt-spring-boot-starter，敏感信息全部替换为 `ENC(密文)`，解密密钥通过环境变量 `JASYPT_ENCRYPTOR_PASSWORD` 在运行时注入。

```yaml
# 配置文件里只留密文
sky:
  datasource:
    password: ENC(UG/5d22c2uxK67UDZz35KC+...)
```

**核心设计原则：密钥与密文分离**。密文可以进仓库，密钥只能在运行时由运维/环境注入。即使仓库泄露，没有密钥也解不开。

**加密算法**：`PBEWITHHMACSHA512ANDAES_256`（AES-256 加密 + HMAC-SHA512 完整性校验 + 随机 IV）。

**面试要点**：
- 判断标准：泄露后能否被用来「冒充身份/访问资源/伪造数据」——能则加密，不能则不加密；
- **密钥轮换意识**：如果明文密钥曾进过 git 历史，光改密文不够，必须重置密钥（rotate），这是重要的安全意识加分项。

---

## 三、核心业务建模

### 3.1 订单状态机：业务规则集中化

**原始缺陷**：订单有 6 个状态，原项目在每个状态转换方法里「各自手写 if 判断当前状态」，规则散落、魔法数字、难以测试、易遗漏。

**解决方案**：新增 `OrderStatus` 枚举，用 `EnumMap<状态, Set<目标状态>>` 集中定义合法流转表，业务方法统一调 `checkTransition(current, target)` 校验。

**状态流转图（主流程）**：

```
待付款(1) ──支付──▶ 待接单(2) ──接单──▶ 已接单(3) ──派送──▶ 派送中(4) ──完成──▶ 已完成(5)
   │                    │ 拒单                 │ 取消
   │取消                 ▼                     ▼
   └───────────────▶ 已取消(6) ◀───────────────┘
```

**核心实现**：

```java
public enum OrderStatus {
    PENDING_PAYMENT(1), TO_BE_CONFIRMED(2), CONFIRMED(3),
    DELIVERY_IN_PROGRESS(4), COMPLETED(5), CANCELLED(6);

    // 状态机核心：合法流转表
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = buildTransitions();

    private static Map<OrderStatus, Set<OrderStatus>> buildTransitions() {
        map.put(PENDING_PAYMENT, setOf(TO_BE_CONFIRMED, CANCELLED));
        map.put(TO_BE_CONFIRMED, setOf(CONFIRMED, CANCELLED));
        map.put(CONFIRMED, setOf(DELIVERY_IN_PROGRESS, CANCELLED));
        map.put(DELIVERY_IN_PROGRESS, setOf(COMPLETED));
        map.put(COMPLETED, setOf());   // 终态无出口
        map.put(CANCELLED, setOf());   // 终态无出口
    }

    public static void checkTransition(OrderStatus current, OrderStatus target) {
        if (!canTransition(current, target)) throw new OrderBusinessException(ORDER_STATUS_ERROR);
    }
}
```

**设计要点**：
- **状态码自持**：枚举在 sky-common，实体在 sky-pojo，用字面量自持状态码避免模块反向依赖（循环依赖）；
- **EnumMap + Set**：O(1) 查询的合法流转表；
- **终态无出口**：已完成/已取消的合法目标为空集，任何流转都被拒绝。

**面试要点**：
- 为什么用枚举而非 if-else/switch：规则集中、类型安全、可单独测试；
- 终态处理：空集拒绝所有出口；
- 订单状态与支付状态是正交的两个维度；
- 并发问题：状态机解决「合法性」，并发安全还需乐观锁/CAS 加固（`update ... where status = 旧值`）。

### 3.2 支付回调幂等：保证只处理一次

**原始缺陷**（三个严重问题）：
1. **依赖登录态（致命 bug）**：`paySuccess` 用 `BaseContext.getCurrentId()` 拿 userId，但微信回调是服务器主动发起，没走 JWT 拦截器，ThreadLocal 里是 null，永远查不到订单；
2. **无幂等判断**：微信重复回调时无脑重复更新状态 + 重复推送；
3. **无交易号字段**：无法记录微信交易号，无法判重/对账。

**解决方案**：
1. 新增 `transaction_id` 字段记录微信交易号；
2. 新增按订单号查询 `getByNumber`（不依赖 userId）；
3. `paySuccess` 改造：先按订单号查，判断 `payStatus == PAID` 则直接返回（幂等）。

```java
public void paySuccess(String outTradeNo, String transactionId) {
    Orders orderDB = orderMapper.getByNumber(outTradeNo);  // 不依赖 userId
    if (orderDB == null) throw new OrderBusinessException(ORDER_NOT_FOUND);

    // 幂等判断：已支付 → 重复回调，直接返回
    if (Orders.PAID.equals(orderDB.getPayStatus())) {
        log.info("重复回调，忽略：{}", outTradeNo);
        return;
    }
    // ... 更新状态 + 记录交易号 + 推送提醒
}
```

**为什么支付回调必须幂等**：微信回调不保证只发一次（15s/15s/30s/3m... 递增间隔重试），异步、可能重复。

**幂等方案对比**：

| 方案 | 原理 | 适用场景 |
|------|------|---------|
| 状态判断（本项目） | 查状态已处理则跳过 | 状态流转简单 |
| 唯一约束 + 数据库 | transaction_id 唯一索引 | 需强一致、有流水表 |
| Redis 分布式锁 | setIfAbsent 抢锁 | 高并发、耗时场景 |
| token/流水号去重 | Redis 判重 | 通用接口防重 |

**面试要点**：能讲清「回调为什么没有登录态」这个关键认知，以及从「状态判断」到「唯一索引 + 流水表 + 分布式锁」的严格幂等演进。

---

## 四、异步解耦：RabbitMQ 支付回调异步化

### 4.1 为什么引入 MQ

**真实痛点**：微信支付要求回调接口快速返回 `SUCCESS`，否则判定超时重试。原来的 `paySuccess` 在回调线程里同步做「改状态 + 推 WebSocket」，一旦后续动作变重会拖慢响应。

**解法**：回调只做「最小必要操作」（幂等校验 + 落库），把「来单提醒」等后续动作通过 MQ 异步解耦。

```
微信回调 → 幂等校验 + 落库（快）→ 发送 MQ 消息 → 返回 SUCCESS
                                      ↓ 异步
                             消费者推送 WebSocket
```

### 4.2 消息拓扑与可靠性

```
生产者 → Direct交换机 order.notify.exchange → 队列 order.notify.queue → 消费者
```

**可靠性四件套**：

| 机制 | 配置 | 作用 |
|------|------|------|
| 生产者确认 | `publisher-confirm-type: correlated` | 确认到达交换机 |
| 路由失败返回 | `publisher-returns: true` | 未路由时回调，避免丢消息 |
| 消费端手动 ACK | `acknowledge-mode: manual` | 成功才 basicAck，失败重试 |
| 死信队列 | 队列绑定 DLX | 重试超限进入死信，人工补偿 |

### 4.3 为什么不是定时扫描

「支付超时关单」仍用 Spring Task 定时扫描（`processTimeoutOrder`），因为它是「到点批量处理」的延迟场景；「支付成功通知」是「事件实时驱动」，用 MQ 更合适。**两种机制各司其职**，是很好的面试论据。

**面试要点**：消息丢失（confirm + 持久化 + 手动 ACK 三段保证）、重复消费（幂等）、积压（扩容消费者 + prefetch）、顺序性（单队列单消费者天然有序）。

---

## 五、可观测性：统一日志 + MDC 链路追踪

### 5.1 问题背景

一个外卖订单从下单到完成横跨多个 Controller/Service、异步 MQ 消费者、WebSocket 推送，甚至多个应用实例。没有链路追踪时，日志散落、格式不一、并发下无法区分哪行日志属于哪次请求。

**目标**：给每次请求打上全局唯一 `traceId`，所有日志都带这个 ID，`grep` 一个 traceId 就能还原整条调用链。

### 5.2 核心概念：MDC

MDC（Mapped Diagnostic Context）是 SLF4J 提供的**线程级（ThreadLocal 实现）**日志上下文容器，可用 `%X{key}` 在日志格式中取出。

```java
MDC.put("traceId", "abc123");   // 注入
log.info("下单成功");            // 日志自动带 traceId=abc123
MDC.remove("traceId");          // 必须清理
```

**三个必须牢记的坑**：

| 坑 | 后果 | 解法 |
|----|------|------|
| 不清理 MDC | Tomcat 线程复用导致串号 | finally 里 MDC.remove |
| 跨线程丢上下文 | MQ/@Async/线程池里 traceId 丢失 | 手动传递再 put |
| 只写 %X{} 没 put | 日志里 traceId 为空 | 入口 Filter 统一注入 |

### 5.3 三层实现方案

**第一层：TraceIdUtil（sky-common）**——生成 32 位 traceId、定义 MDC key 与请求头常量。

**第二层：TraceIdFilter（sky-server）**——用 Filter（而非 Interceptor）在 Servlet 容器最外层注入/透传/清理：

```java
String traceId = req.getHeader("X-Trace-Id");  // 上游有则透传
if (traceId == null) traceId = TraceIdUtil.generateTraceId();
MDC.put("traceId", traceId);
resp.setHeader("X-Trace-Id", traceId);          // 回写，前端报错能带 traceId
try {
    chain.doFilter(req, resp);
} finally {
    MDC.remove("traceId");                        // 关键：清理防串号
}
```

**第三层：logback-spring.xml**——统一格式 `%d %-5level [%thread] [traceId:%X{traceId}] %logger - %msg`，异步 Appender + 按天/按大小滚动归档 + 环境差异化（dev 打印 SQL，prod 不打印）。

### 5.4 异步链路如何保证 traceId 不丢（核心考点）

MDC 基于 ThreadLocal，异步线程天然拿不到主线程的 traceId。项目落地了**三种跨线程传递**：

**① MQ 消费者——消息头透传**：发送端用 `MessagePostProcessor` 把 traceId 写入消息头，消费端取出后 `MDC.put`；

**② @Async 线程池——TaskDecorator 统一复制（官方推荐，一处配置全局生效）**：

```java
public class MdcTaskDecorator implements TaskDecorator {
    public Runnable decorate(Runnable runnable) {
        String traceId = TraceIdUtil.getTraceId();
        return () -> {
            try {
                if (traceId != null) MDC.put(TraceIdUtil.TRACE_ID, traceId);
                runnable.run();
            } finally {
                MDC.remove(TraceIdUtil.TRACE_ID);
            }
        };
    }
}
```

**③ 定时任务——独立线程池并行执行**。

### 5.5 进阶：SkyWalking / Sleuth

手写 MDC 只能解决单服务内的串联；微服务跨进程需专业分布式链路追踪。SkyWalking（Java Agent 字节码增强，零侵入，推荐）或 Spring Cloud Sleuth（已合并进 Micrometer Tracing）。手写 traceId 是「轻量版」，未来可平滑升级。

**面试高频问答**：为什么用 Filter 不用 Interceptor（覆盖范围）、MDC 底层是什么（ThreadLocal）、traceId 会串号吗（线程复用）、异步为什么丢 traceId、为什么用 AsyncAppender（避免磁盘 IO 阻塞业务线程）。

---

## 六、安全体系：RBAC 权限模型

### 6.1 原始缺陷

教学版默认「两个角色（admin/user）+ 两套拦截器 + 两把 JWT 密钥」硬隔离，粒度太粗：只要登录成功，所有管理员能调所有接口，无法区分「店长」「运营」的职责，权限写死在代码里。

### 6.2 RBAC 核心概念

**RBAC = Role-Based Access Control**，核心思想：不直接给用户授权，而是给「角色」授权，再把「角色」分配给「用户」。

```
用户(User) ──多对多──▶ 角色(Role) ──多对多──▶ 权限(Permission)
 员工张三  ──拥有──▶  店长     ──拥有──▶   dish:add / dish:delete
 员工李四  ──拥有──▶  运营     ──拥有──▶   order:confirm / order:delivery
```

### 6.3 数据库设计（RBAC0 模型）

四张表：`role`（角色）、`permission`（权限，细粒度到接口/操作）、`employee_role`（用户-角色关联）、`role_permission`（角色-权限关联）。

权限编码（perm_code）与 `@RequirePermission` 注解值一一对应，如 `dish:add`、`order:confirm`。

### 6.4 代码实现

**① 权限注解**：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String[] value();  // 多个值时满足任一即可
}
```

**② 拦截器叠加授权校验**（认证 401 / 授权 403 分离）：

```java
public boolean preHandle(...) {
    // 第一步：认证（JWT 解析）
    Claims claims = JwtUtil.parseJWT(adminSecretKey, token);
    Long empId = ...;
    BaseContext.setCurrentId(empId);

    // 第二步：授权（RBAC 权限校验）
    RequirePermission rp = handlerMethod.getMethodAnnotation(RequirePermission.class);
    if (rp != null) {
        Set<String> permCodes = permissionService.listPermCodes(empId);
        boolean has = Arrays.stream(rp.value()).anyMatch(permCodes::contains);
        if (!has) { response.setStatus(403); return false; }
    }
    return true;
}
```

**③ 权限查询带缓存**：`PermissionService` 用 `@Cacheable(cacheNames = "permCodes", key = "#employeeId")` 缓存权限集合，避免每请求查库。

**设计要点**：
- 认证与授权分离；
- 权限校验只对标注了 `@RequirePermission` 的方法生效，未标注的只做登录态校验，**向后兼容、渐进式加权限**；
- 权限查询走 Redis 缓存。

**面试要点**：RBAC 演进（RBAC0 → RBAC1 角色继承 → RBAC2 约束 → RBAC3）、为什么用角色中间层、401 与 403 的区别。

---

## 七、高可用：限流、熔断与缓存三防

### 7.1 限流与熔断（Resilience4j）

**限流**：控制单位时间内进入系统的请求数，防流量突增（刷单、秒杀）。**熔断**：下游连续失败时暂时切断调用，快速失败防雪崩。

**为什么选 Resilience4j**：轻量、纯注解、Spring Cloud 官方推荐、无需独立控制台，适合单体项目；对比 Sentinel（功能强但需 Dashboard）、Hystrix（已停止维护）。

**实现**：
- 限流（Controller 层）：`@RateLimiter(name = "orderSubmit", fallbackMethod = "submitFallback")`；
- 熔断（Service 层）：`@CircuitBreaker(name = "orderService", fallbackMethod = "submitOrderFallback")`。

**熔断器状态机（面试必背）**：

```
       失败率超阈值
  CLOSED ────────────> OPEN（快速失败，走 fallback）
    ↑                    │ 等待 wait-duration
    │                    ↓
    └──── 成功 ────── HALF_OPEN（放行少量探测）
              失败 ────────────> OPEN（继续熔断）
```

**与 Nginx 双层防护**：网关层粗粒度限流挡大流量，应用层对核心链路（下单、支付）精细限流 + 熔断降级，既防外部刷量又防内部雪崩。

**面试要点**：限流算法（固定窗口/滑动窗口/漏桶/令牌桶）、令牌桶 vs 漏桶、熔断 vs 降级、fallback 方法签名要求。

### 7.2 缓存三防：穿透、击穿、雪崩

- **穿透**：查不存在的 key 每次都打到 DB → 空值缓存 / 布隆过滤器（项目补了空值缓存）；
- **击穿**：热点 key 过期瞬间大量并发 → 互斥锁 / 逻辑过期（项目用 setIfAbsent 互斥锁 + 自旋）；
- **雪崩**：大量 key 同时过期 → 过期时间加随机值 / 集群高可用。

### 7.3 缓存两套实现对比（面试高频）

项目里「菜品」用 RedisTemplate 手动缓存，「套餐」用 Spring Cache 注解，刻意形成对比：

| 维度 | RedisTemplate 手动 | Spring Cache 注解 |
|------|-------------------|------------------|
| 实现方式 | 自己写 get/set/delete | 注解声明式，AOP 代理 |
| 代码侵入性 | 缓存逻辑混在业务代码 | 业务代码干净 |
| key 管理 | 手动拼接 | SpEL 表达式 |
| 失效策略 | 手动 keys(pattern)+delete | @CacheEvict 自动 |
| 灵活性 | 高 | 相对受限 |

> 一句话总结：RedisTemplate 是「手动挡」，控制力强但繁琐；Spring Cache 是「自动挡」，简洁但灵活度受限。

### 7.4 Redis 序列化优化

原始缺陷：RedisTemplate 只配了 key 的 String 序列化，value 默认 JDK 序列化（`\xAC\xED...` 二进制流，redis-cli 不可读、实体必须实现 Serializable、跨语言不可用）。

修复：value 改用 `GenericJackson2JsonRedisSerializer`（JSON + `@class` 类型信息），与 Spring Cache 的序列化策略对齐。

---

## 八、质量保障：63 个测试用例

### 8.1 测试覆盖全景

| 测试类 | 用例数 | 覆盖点 |
|--------|:---:|--------|
| CategoryServiceImplTest | 6 | 分类增删改查 |
| EmployeeServiceImplTest | 7 | 员工登录、BCrypt 校验 |
| OrderServiceImplTest | 8 | 订单取消/支付/再来一单/回调幂等 |
| DishServiceImplTest | 8 | 菜品删除/起停售/查询 |
| ShoppingCartServiceImplTest | 8 | 购物车增删改查 |
| DishControllerTest | 4 | 缓存命中/击穿并发/穿透空值 |
| OrderControllerTest | 4 | 下单 token 幂等四分支 |
| GlobalExceptionHandlerTest | 2 | 参数校验异常处理 |
| OrderStatusTest | 7 | 状态机合法/非法流转 |
| RabbitMQIntegrationTest | 1 | RabbitMQ 全链路 |
| AdminLoginIntegrationTest | 7 | 管理端登录端到端 + 认证链路 |
| **合计** | **63** | — |

测试演进：21 → 32 → 40 → 55 → 63 个用例，逐步补齐 Controller 层、缓存击穿并发、参数校验边界、状态机、Service 层广度、端到端集成。

### 8.2 三个高价值测试的设计思路

**① 缓存击穿并发测试（最有技术含量）**：用 `CountDownLatch` + 线程池 + `AtomicReference` 模拟 10 线程同时请求同一分类，断言「并发击穿时查库只发生一次」。这证明了击穿防护（互斥锁 setIfAbsent + 自旋）真的有效。

**② 下单幂等四分支测试**：token 缺失 / token 篡改 / token 一致 / token 已用，覆盖完整幂等逻辑。

**③ 端到端集成测试**：`@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` 走完整 HTTP → 拦截器 → Controller → Service → Mapper → DB 链路，覆盖登录、401 拦截、合法/伪造 token、真实 Redis 查询。

### 8.3 测试技术要点

- **为什么用 Mockito 而非 @SpringBootTest**：单测快（毫秒级）且隔离，集成测试慢但测完整链路，分层策略——纯逻辑用单测，跨中间件用集成测试；
- **ThreadLocal 处理**：测试前 set，`@AfterEach` 里 remove，防残留污染；
- **并发测试保证「同时」**：CountDownLatch 先就位再放行，标准写法。

---

## 九、其他关键改进（速览）

### 9.1 登录安全与密码加密

BCrypt 密码加密（自带盐值、抗彩虹表）、JWT 令牌认证（无状态、可扩展）。

### 9.2 数据库优化

高频字段补索引、B+ 树原理（聚簇索引、联合索引最左前缀）、分库分表设计（知识储备）。

### 9.3 WebSocket 日志与消息规范

`System.out.println` 改为 `@Slf4j + log.info`；订单全流程状态推送规范；催单 bug 修复（消息 type 过滤 + 用户端独立推送）。

### 9.4 访问通道承载能力评估

对「同步写日志阻塞业务线程」等吞吐瓶颈做分析，配合异步日志、线程池等优化。

---

## 十、学习路径与面试串联

### 10.1 建议学习顺序（由浅入深）

1. **先看懂分层架构与模块划分**（地基）；
2. **基础设施加固**（事务、雪花算法、配置加密）——理解「正确性」；
3. **核心业务建模**（状态机、幂等）——理解「业务规则工程化」；
4. **异步解耦**（MQ）——理解「系统设计」；
5. **可观测性**（日志 + 链路追踪）——理解「运维视角」；
6. **安全**（RBAC）——理解「权限模型」；
7. **高可用**（限流熔断、缓存三防）——理解「稳定性」；
8. **质量保障**（测试）——理解「工程素养」。

### 10.2 面试叙事主线

整个项目可以用一句话串起来：「我在一个外卖教学项目基础上，系统性补齐了它的正确性（事务/幂等/状态机）、安全性（RBAC/配置加密）、稳定性（限流熔断/缓存三防）、可观测性（日志链路追踪）和质量保障（63 个测试），把教学项目改造成了接近真实企业级的工程实践。」

### 10.3 落地文件索引

| 主题 | 关键文件 |
|------|---------|
| 链路追踪 | TraceIdUtil、TraceIdFilter、MdcTaskDecorator、AsyncConfiguration、logback-spring.xml |
| RBAC | RequirePermission、PermissionService、PermissionMapper、JwtTokenAdminInterceptor、RBAC权限体系SQL设计.sql |
| 订单状态机 | OrderStatus（枚举）、OrderServiceImpl、OrderStatusTest |
| 支付幂等 | OrderMapper（getByNumber）、OrderServiceImpl.paySuccess |
| MQ | RabbitMQConfiguration、OrderNotifyListener |
| 配置加密 | application.yml、application-dev.yml、pom.xml（jasypt） |
| 限流熔断 | OrderController、OrderServiceImpl、application.yml（resilience4j） |
| 测试 | 11 个测试类（含 AdminLoginIntegrationTest、RabbitMQIntegrationTest） |

---

## 结语

这份报告覆盖了苍穹外卖项目从教学版到工程实践版的完整演进。每一处改进都遵循「问题驱动 → 最小侵入 → 可验证」的原则，既解决了真实的技术痛点，又沉淀了可迁移的工程方法论。建议读者结合代码逐项对照学习，重点掌握「为什么这么做」而非仅仅「怎么做」，这才能真正把项目变成自己面试的底气。
