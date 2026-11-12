# 苍穹外卖（Sky Take-Out）项目 · 交接报告

> **接收方**：后续接手的 AI 教练 / 改进者
> **生成方**：WorkBuddy（本项目前序会话的执行者）
> **生成日期**：2026-11-13
> **文档用途**：完整移交"这个项目做成了什么样"和"这位用户到底想要什么"，让你可以无缝接手，继续做**工程改进 + 技术教学**两件事，不需要重新摸索。

---

## 0. 30 秒速读（如果只看一段，看这段）

**项目是什么**：一个基于黑马程序员《苍穹外卖》课程项目改造的 Java 后端工程（Spring Boot 2.7.3 + MyBatis + MySQL + Redis + RabbitMQ）。原始版本是教学 Demo，前序会话已经把它改造为**具备生产级技术要素、可用于中高级 Java 面试讲述**的增强版，并补充了 63 个自动化测试和 20+ 份技术文档。

**用户是谁**：一位**转行求职阶段的 Java 学习者**。他跟着视频学过 SSM 和这个外卖项目，但基础不牢、细节大量遗忘。他在前序会话中的原话是——**"我都是许愿式开发"**：他把需求描述给 AI，AI 直接实现，代码能跑，但他**自己并没有真正掌握原理**。

**你现在要做的两件事**：
1. **教学**（第一优先级）：把他"许愿"来的知识点，用新手能听懂、能亲手跑起来的方式讲透。
2. **改进**（第二优先级）：在现有基础上继续增强项目，但每加一个功能，必须同时交付对应的教学讲解。

**三条铁律**：
- ❌ 不要只给结论和代码。他现在最缺的不是代码，是**"为什么"**。
- ❌ 不要输出"项目总结式报告"。他要的是**教学**，不是汇报。
- ✅ 每讲一个知识点，必须配**可运行的最小演示** + **逐行注释** + **指回项目真实代码**。

**另外两条已定的边界**（已确认，不要重提）：
- 🚫 **项目就叫 `sky`，不改名**。曾讨论过 FoodForge，用户已否决，包名 `com.sky` / 模块名 `sky-*` 全部保持原样。
- 🚫 **GitHub 删仓库由用户本人操作**，你只给步骤，不要代做。

---

## 1. 用户画像与真实诉求

### 1.1 技术背景（已确认）

| 维度 | 实际情况 |
|------|---------|
| 学习路径 | 跟黑马程序员视频学过 SSM 框架，做过《苍穹外卖》课程项目 |
| 框架熟练度 | 能照教程写 Controller / Service / Mapper，但**原理说不清** |
| 中间件认知 | 对事务、Redis、RabbitMQ、JWT 等概念"听说过"，但**没有真正理解** |
| 掌握程度自评 | 用户自选标签为：**"跟着视频学过，但很多都忘了，基础不扎实"** |
| 当前目标 | 求职 / 面试。项目要能撑起简历，面试被追问时能答得上来 |
| 操作习惯 | 依赖 AI 直接实现（"许愿式开发"），自己动手调试能力弱 |

### 1.2 他的"许愿式开发"意味着什么

这是理解这位用户的**关键钥匙**。

前序会话中，他连续提出了这些需求：统一日志与链路追踪、RBAC 权限体系、订单状态机、测试覆盖补强……每一次都是**一句话描述需求 → AI 直接给出完整实现 → 他确认能跑 → 进入下一个需求**。

结果：项目技术含量上去了，但他本人对这些代码处于**"认识每个字，但不知道它为什么这么写"**的状态。

后来他自己意识到了这个问题，明确说出：

> "因为我们新加的功能都是我没有正经学习掌握的，我都是许愿式开发，我希望你以这个基准出发，联系那些新功能知识点进行新手教学方案编写，带演示案例和注释讲解的那种"

**所以：任何后续工作，都必须以"他能不能真正学会"为验收标准，而不是"代码能不能跑"。**

### 1.3 他真正想要的四件事（按优先级排序）

**P0 — 教学：把已有功能讲透**
不是讲概念，是讲"这个功能在我们项目里的哪一行、为什么要这么写、不这么写会出什么事"。要求：生活化比喻 → 概念 → 可运行演示 → 逐行注释 → 对照项目真实代码 → 自测题。

**P1 — 面试可用性**
他需要能把这个项目"讲成一条完整的故事线"，应对中高级面试的深挖。每一项技术点都必须能回答：为什么用？不用会怎样？怎么保证可靠？有什么坑？

**P2 — 项目可运行、可演示**
不满足于"代码在仓库里"。他要求真的能启动、能在浏览器里登录进去操作（前序会话中已完成：`http://localhost/`，账号 `admin/123456`）。

**P3 — 仓库干净、可对外展示**
他要的是能在面试时直接把 GitHub 链接发出去的仓库：结构清晰、无无关杂物、无密钥泄露。

### 1.4 他明确反感 / 无效的东西

| 他反感的形式 | 他的原话 / 表现 |
|-------------|----------------|
| 项目总结式报告 | 第一版技术报告生成后，他回："**你这个还是不够**" |
| 只给结论不给原理 | 他要"注释讲解的那种" |
| 只给代码不给验证 | 他要求"带演示案例"，且演示要能真的跑 |
| 长篇大论但没有可操作性 | 他更偏爱表格、短段落、明确步骤 |

### 1.5 沟通偏好

- 中文，务实，不客套。
- 喜欢**表格化对比**（方案 A vs 方案 B）。
- 喜欢**明确的分步操作指引**（尤其是 IDEA、Git、GitHub 这类 GUI 操作）。
- 遇到报错时，他要的是**根因 + 修复步骤**，不是"可能是环境问题"。
- 他会在你给出方案后追问细节，属于愿意深挖的类型。

---

## 2. 诉求演进时间线（前序会话全记录）

按阶段归纳，方便你理解"项目为什么会变成现在这个样子"。

### 阶段一：把项目技术含量拉起来（工程改造期）

| # | 用户诉求（原话节选） | 做了什么 |
|---|---------------------|---------|
| 1 | "统一日志收集 + 链路追踪 —— 目前日志散在各处，可补充 Logback 规范 + MDC 追踪 ID" | 落地 `logback-spring.xml` 规范化 + `TraceIdFilter` + MDC traceId 全链路 |
| 2 | "需我把 MQ 消费者和 @Async 线程池的 traceId 跨线程传递也一并落地（面试加分项）" | 新增 `MdcTaskDecorator` + `AsyncConfiguration`，实现跨线程 MDC 传递 |
| 3 | "现在这个项目完整度和内容深度怎么样了" | 做项目完整度评估，输出雷达图分析 |
| 4 | "RBAC 权限体系" | 设计 4 张表（role / permission / employee_role / role_permission）+ `@RequirePermission` + `PermissionService` |
| 5 | "订单状态机" | 新增 `OrderStatus` 枚举（EnumMap 合法流转表），改造订单 4 个状态操作 |
| 6 | "继续补测试覆盖广度" | 新增 DishServiceImplTest、ShoppingCartServiceImplTest 等，用例从 33 → 55 |

### 阶段二：交付与排障（落地期）

| # | 用户诉求 | 做了什么 |
|---|---------|---------|
| 7 | "教教我怎么在 IDEA 里提交 git" | 教学 + 发现 `E:/ detected dubious ownership` 报错 |
| 8 | （贴出完整报错） | 定位到 `E:/.git` 旧仓库污染，重命名为 `E:\.git_bak_20261107` |
| 9 | "还是你来给我上传这些重要代码吧" | 代为提交推送，同时发现 `.gitignore` 忽略测试文件、笔记泄露 jasypt 密钥，修复后 6 组 commit 推送成功 |
| 10 | "旧的删了" | 清理旧仓库残留 |
| 11 | "？见鬼，我启动主文件 sky application 一堆报错" | 排查 jasypt 密钥未注入，改为用户级环境变量 |
| 12 | "你来模仿我的真人使用流程，启动主文件，在 chrome 浏览器上进入平台" | Maven 启动成功，浏览器验证登录可用 |
| 13 | "这个逻辑有问题吧，刚进来什么都没做就催单" | 定位到前端 `onmessage` 无 type 过滤 + 后端心跳群发管理端，双侧修复 |
| 14 | "方案 A：在 IDEA 的启动配置里加 VM 参数…你帮我弄好" | 配置 VM 参数，同时发现 IDEA 模块冲突（`sky-server (1)` 指向旧副本） |
| 15 | "执行预编译任务…找不到符号 TraceIdUtil" | 根因：工程根挂了 5 个 Maven 项目含同名副本，改为以 `sky-take-out` 为工程根 |
| 16 | "为什么让你启动就可以，我这里手动启动就失败" | 解释 IDEA 运行时覆盖 workspace.xml，最终改用系统环境变量方案 |
| 17 | "你来生成个自动化测试试试，从登录等模块到集成测试" | 新增 `AdminLoginIntegrationTest`（7 用例端到端），修复 MQ 测试隔离，全量 62 用例通过 |

### 阶段三：从"改造"转向"教学"（认知转折期）

| # | 用户诉求 | 做了什么 |
|---|---------|---------|
| 18 | "生成一份详细的教学技术报告，我需要用这份报告来进行全内容了解吃透学习" | 生成 10 章技术报告（Word，含封面目录页码） |
| 19 | **"你这个还是不够…我都是许愿式开发…带演示案例和注释讲解的那种"** | **认知转折点**。改为新手教学方案：3 章正文 + 6 个零依赖可运行 Demo |
| 20 | （咨询改名）"我的仓库里有好几个 sky 相关的，能换个特殊名字吗" | 探查命名分布，曾提议 **FoodForge**；**用户最终决定不改名，保持 `sky` 原样**（议题已关闭） |
| 21 | "git 里把其他的全部清除吧" | 澄清后确认指 **GitHub 账号下其他仓库**：只留 `sky`，其余 6 个全删 |
| 22 | "确定 sky 是我完善的仓库吗" | 用提交哈希比对验证：本地 = 远程 = `f3419e6`，确认为本人项目 |
| 23 | "提交" | 分 4 组提交推送成功（`f3419e6..4dd442c`） |
| 24 | **本报告** | 移交文档 |

### 时间线里最有价值的三条信息

1. **第 19 轮是转折点**。在这之前所有工作都是"加功能"，在这之后必须"讲功能"。你接手后要延续后者。
2. **他对"跑不起来"极其敏感**。前序会话有 6 轮都在排障（jasypt、IDEA 模块、MQ 队列、前端构建）。你给的任何方案，必须给出**可验证的启动/运行步骤**。
3. **他愿意为正确的事反复纠正你**（"你这个还是不够"）。这说明他不排斥深度内容，只是排斥**形式不对**的深度内容。

---

## 3. 项目现状台账（事实清单）

### 3.1 位置与仓库

| 项 | 值 |
|----|-----|
| 仓库根（git root） | `E:/sky-take-out-main/sky-take-out-main` |
| 远程仓库 | `https://github.com/gw34744/sky.git` |
| 分支 | `main` |
| 最新提交 | `4dd442c`（docs: 归档项目教学技术报告与教学方案成品） |
| 工作区状态 | **干净，已全部推送，本地与远程同步** |
| 跟踪文件数 | 387 |
| 提交总数 | 12 |

> ⚠️ **注意目录是三层嵌套**：
> `E:/sky-take-out-main/`（外层，有 .idea/.workbuddy）
> └── `sky-take-out-main/` ← **git 仓库根在这里**
> 　　└── `sky-take-out/` ← **Maven 工程根在这里**（pom.xml）
> 　　　　├── `sky-common/`、`sky-pojo/`、`sky-server/`
> 　　　　└── （前端、workbuddy 文档在上一层）

### 3.2 模块结构

```
sky-take-out/                    Maven 聚合根（parent pom，artifactId=sky-take-out）
├── sky-common/                  公共层：constant / context / enumeration / exception
│                                / json / properties / result / utils / annotation
├── sky-pojo/                    数据层：entity / dto / vo
└── sky-server/                  服务层：config / controller / interceptor / filter
                                 / mapper / service / task / websocket / listener
```

**关键架构约束（已踩过坑）**：
`sky-common` **不能**依赖 `sky-pojo`，否则模块循环依赖，Maven 直接构建失败。
→ 这就是为什么 `OrderStatus` 枚举（在 sky-common）自己维护了一份状态码字面量，而不是复用 `sky-pojo` 里的 `Orders` 实体。

### 3.3 技术栈版本

| 组件 | 版本 / 说明 |
|------|-----------|
| JDK（项目编译目标） | **Java 8**（`pom.xml` 中 `java.version`） |
| JDK（本机实际） | **17.0.12**（Java 8 语法可编译，注意不要用 `var` 等 9+ 语法） |
| Spring Boot | **2.7.3** |
| MyBatis | mybatis-spring-boot-starter + PageHelper |
| MySQL | 主库 `sky_take_out`，连接串走 `${sky.datasource.*}` 变量注入 |
| Redis | 用于缓存 + 权限码缓存 |
| RabbitMQ | 订单通知异步化 |
| 限流 | Resilience4j（`@RateLimiter`，订单提交 5 QPS） |
| 接口文档 | Knife4j（`/doc.html`） |
| 前端 | `project-rjwm-admin-vue-ts`（Vue 2.6 + webpack 4 + ElementUI） |
| 服务端口 | `8080`（后端），`80`（nginx 前端） |

### 3.4 已落地的 14 项增强（核心资产，逐项说明）

> 这一节是交接的核心。每一项都包含：**原始缺陷 → 解决方案 → 落地文件 → 面试价值**。

---

#### E1 · 下单事务缺失（ACID）

- **原始缺陷**：`submitOrder()` 一次下单要写 4 张表（`orders` / `order_detail` / `address_book` 的用量 / 清空 `shopping_cart`），但**没有 `@Transactional`**。中途任何一步失败，前面已写入的数据不会回滚 → 脏数据、超卖、购物车被清空但订单不存在。
- **解决方案**：方法级 `@Transactional(rollbackFor = Exception.class)`，四步写库纳入同一事务；明确 `rollbackFor` 覆盖受检异常。
- **落地文件**：`sky-server/.../service/impl/OrderServiceImpl.java`
- **面试价值**：`@Transactional` 失效的四大场景（同类内部调用、非 public、异常被吞、非运行时异常）、事务传播行为、`rollbackFor` 为什么必须写。

#### E2 · 订单号并发撞号（雪花算法）

- **原始缺陷**：原实现用时间戳（`System.currentTimeMillis()`）生成订单号，同一毫秒内的并发请求会生成**完全相同的订单号**，导致订单主键冲突 / 数据串单。
- **解决方案**：新增 `SnowflakeIdUtil`（125 行），标准雪花结构：`1 位符号 + 41 位时间戳 + 10 位机器ID + 12 位序列号`，单机单毫秒可产 4096 个不重复 ID。
- **落地文件**：`sky-common/.../utils/SnowflakeIdUtil.java`
- **面试价值**：为什么用雪花不用 UUID（UUID 无序导致 B+ 树页分裂）、时钟回拨怎么处理、workerId 怎么分配、位数怎么分配。

#### E3 · 配置明文泄露（jasypt 加密）

- **原始缺陷**：`application-dev.yml` 里数据库密码、阿里云 OSS AccessKey、微信支付 `apiV3Key`、JWT secret **全部明文**。一旦提交到 GitHub 就是安全事故。
- **解决方案**：引入 jasypt，敏感项改为 `ENC(密文)`；主密码通过**环境变量** `JASYPT_ENCRYPTOR_PASSWORD` 注入，不落盘、不入库。
- **落地文件**：`sky-server/src/main/resources/application-dev.yml`（**已被 .gitignore 排除，从未进 git 历史**）、`pom.xml`
- **面试价值**：配置加密方案对比（jasypt / KMS / Vault）、密钥本身怎么保管、为什么不能把主密码写进配置文件。

#### E4 · 支付回调同步阻塞 + 重复回调

- **原始缺陷**：(a) 支付回调里同步处理全部业务，微信侧超时重试；(b) 微信会重复推送同一笔回调，原实现会**重复处理**（重复改状态、重复发通知）。
- **解决方案**：
  - 异步化：回调收到即返回成功，业务通过 RabbitMQ 异步处理；
  - 幂等：以订单号 + 状态做幂等判断，已处理过的回调直接返回成功。
- **落地文件**：`OrderServiceImpl`、`OrderNotifyListener`、`RabbitMQConfiguration`
- **面试价值**：幂等三种实现（唯一索引 / 状态机 / 分布式锁）对比、MQ 削峰的代价、回调必须"先应答再处理"的原因。

#### E5 · 订单状态乱跳（状态机）

- **原始缺陷**：订单状态用魔数（1/2/3/4/5/6）散落在各处，`confirm` / `rejection` / `delivery` / `complete` 各自判断，**没有统一的合法性校验**。可以从"已完成"直接改回"待接单"，数据彻底乱掉。
- **解决方案**：`OrderStatus` 枚举（119 行）用 `EnumMap` 定义**合法流转表**，每个操作前调 `checkTransition()` 校验；终态（已完成/已取消）无出口。
- **落地文件**：`sky-common/.../enumeration/OrderStatus.java`
- **面试价值**：状态机建模（状态 + 事件 + 转移）、为什么用 EnumMap 而不是 HashMap、如何用状态机实现幂等、终态设计原则。

#### E6 · MQ 消息丢失

- **原始缺陷**：直接 `convertAndSend` 发消息，没有持久化保障、没有重试、失败的消息直接消失。
- **解决方案**：可靠性四件套 —— 生产者确认（publisher confirm）、消费者手动 ACK、消息持久化、死信队列兜底。
- **落地文件**：`RabbitMQConfiguration`、`OrderNotifyListener`
- **面试价值**：MQ 三问（不丢 / 不重 / 不乱序）、为什么需要死信队列、手动 ACK 与自动 ACK 的区别。

#### E7 · 日志无法串联（MDC 全链路追踪）

- **原始缺陷**：日志散落各处，格式不统一；一个请求经过 Controller → Service → MQ → 异步线程后，**日志彻底断开**，线上排障只能靠时间戳猜。
- **解决方案**：
  - `TraceIdFilter`（`@Order(HIGHEST_PRECEDENCE)`）在请求入口生成 32 位 traceId 放入 MDC，出口清理；
  - `logback-spring.xml`（131 行）统一格式 + 异步 Appender + 滚动归档 + `%X{traceId}`；
  - 跨线程传递：MQ 通过消息头透传 traceId；`@Async` 通过 `MdcTaskDecorator` 复制 MDC。
- **落地文件**：`TraceIdUtil`(51行) / `TraceIdFilter`(67行) / `logback-spring.xml`(131行) / `MdcTaskDecorator`(39行) / `AsyncConfiguration`(43行)
- **面试价值**：MDC 底层是 ThreadLocal、ThreadLocal 内存泄漏与 `remove()`、为什么线程池必须装饰、TraceId 与 SpanId 的关系（SkyWalking 原理铺垫）。

#### E8 · 无权限控制（RBAC）

- **原始缺陷**：只要登录成功，就能调用**所有**接口。普通员工可以调用员工管理、删除菜品。
- **解决方案**：RBAC0 模型 4 张表（`role` / `permission` / `employee_role` / `role_permission`）+ `@RequirePermission("xxx")` 注解 + `JwtTokenAdminInterceptor` 叠加鉴权 + `PermissionService` 用 `@Cacheable("permCodes")` 缓存权限码。
- **认证与授权的分离**：未登录 → **401**；已登录但无权限 → **403**。这是很多项目做错的地方。
- **落地文件**：`RBAC权限体系SQL设计.sql`、`RequirePermission.java`(34行)、`PermissionService.java`(42行)、`JwtTokenAdminInterceptor.java`
- **面试价值**：RBAC0/1/2/3 的区别、用户-角色-权限多对多表设计、权限缓存怎么保证一致性（`@CacheEvict`）、按钮级权限怎么落到前端。

#### E9 · 缓存穿透 / 击穿 / 雪崩

- **原始缺陷**：`DishController` 查菜品直接落库，无缓存；热点数据每次请求都打 DB。
- **解决方案**：
  - 穿透 → 空值缓存（查不到也缓存 null，短 TTL）；
  - 击穿 → 热点 key 加互斥锁，只放一个线程回源；
  - 雪崩 → TTL 加随机偏移，避免同时失效。
  - 另外提供 `RedisTemplate` 与 Spring Cache (`@Cacheable`) **两套实现对照**。
- **落地文件**：`DishController`、`CacheConfiguration`、`RedisConfiguration`
- **面试价值**：三防的区别（这是最高频的面试题，很多人答混）、缓存一致性（先删缓存还是先更库）、`GenericJackson2JsonRedisSerializer` 为什么比 JDK 序列化好（可读、跨语言、体积小），以及它的**类型信息坑**。

#### E10 · 无流量防护（限流熔断）

- **原始缺陷**：下单接口无任何限流，突发流量直接打穿 DB。
- **解决方案**：Resilience4j `@RateLimiter(name="orderSubmit", fallbackMethod="submitFallback")`，1 秒 5 个令牌，配合 Nginx 网关限流形成双层防护。
- **落地文件**：`OrderController`（第 69、101 行）、`OrderServiceImpl`
- **面试价值**：四种限流算法（计数器 / 滑动窗口 / 漏桶 / 令牌桶）对比、限流 vs 熔断 vs 降级的区别、`fallbackMethod` 签名必须与原方法一致（含异常参数）。

#### E11 · 密码弱哈希（BCrypt）

- **原始缺陷**：课程原版用 MD5 存密码，无盐，彩虹表可秒破。
- **解决方案**：改用 `BCryptPasswordEncoder`，自带随机盐、可调 cost。
- **落地文件**：`EmployeeServiceImpl`
- **面试价值**：MD5 / SHA / BCrypt / Argon2 对比、为什么"加盐"仍不够（要慢哈希）、BCrypt 的 cost 参数怎么选。

#### E12 · WebSocket 消息乱推（催单幽灵）

- **原始缺陷**（用户实测发现的真实 bug）：管理端**刚登录进去、什么都没做**，就弹出"催单"提示。
- **根因**：前端 `Navbar/index.vue` 的 `onmessage` **没有过滤消息 type**，任何消息都当业务消息处理；后端 `WebSocketTask` 心跳把消息**群发给了管理端**。
- **解决方案**（双侧修复）：
  - 前端：`onmessage` 只处理 `type=1`（来单）和 `type=2`（催单）；
  - 后端：新增 `sendToUserClients()`，心跳只发用户端会话，不群发管理端。
- **落地文件**：`project-rjwm-admin-vue-ts/src/layout/components/Navbar/index.vue`、`WebSocketServer.java`、`WebSocketTask.java`
- **面试价值**：WebSocket 会话管理（怎么区分管理端/用户端）、心跳的作用、消息协议设计（type 字段的必要性）。

#### E13 · 零测试（63 个用例）

- **原始缺陷**：无任何自动化测试，改代码全靠手动点。
- **解决方案**：11 个测试类 / **63 个用例**（详见 3.5）。
- **面试价值**：单元测试 vs 集成测试的边界、Mockito 用法（`@Mock` / `@InjectMocks` / `mockStatic` 需 mockito-inline）、`@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` 怎么做端到端。

#### E14 · 知识资产（23 份文档 + 教学方案）

详见 3.6。这部分是**给你接手时最重要的输入**——几乎所有技术决策的背景都在里面。

---

### 3.5 测试现状（11 类 / 63 用例，全绿）

| 测试类 | 用例数 | 类型 | 覆盖点 |
|--------|-------|------|--------|
| `AdminLoginIntegrationTest` | 8 | **端到端集成** | 登录成功 / 密码错误 / 空用户名 / 401 拦截 / 合法 token / 伪造 token / 分类查询 |
| `OrderServiceImplTest` | 8 | 单元（Mockito） | 下单事务、状态流转、金额计算 |
| `DishServiceImplTest` | 8 | 单元 | 菜品增删改查、分类校验 |
| `ShoppingCartServiceImplTest` | 8 | 单元 | 加购 / 减购 / 清空 |
| `OrderStatusTest` | 7 | 纯逻辑 | 状态机合法/非法流转 |
| `EmployeeServiceImplTest` | 7 | 单元 | 登录、BCrypt 校验、账号锁定 |
| `CategoryServiceImplTest` | 6 | 单元 | 分类管理 |
| `DishControllerTest` | 4 | Web 层 | 参数校验、返回结构 |
| `OrderControllerTest` | 4 | Web 层 | 限流 fallback |
| `GlobalExceptionHandlerTest` | 2 | 单元 | 统一异常处理 |
| `RabbitMQIntegrationTest` | 1 | **MQ 集成** | 消息投递与消费 |
| **合计** | **63** | | |

**运行方式**：`mvn test`（需 MySQL / Redis / RabbitMQ 起好；MQ 测试用独立随机队列，不抢生产队列）
**最近一次全量结果**：BUILD SUCCESS，63/63 通过

---

### 3.6 文档与教学资产

**技术笔记（`workbuddy/`，23 份）** —— 这是每个技术点的"决策记录"，强烈建议你先读：

```
项目改进分析报告.md              ← 总纲，先读这个
统一日志收集与链路追踪面试笔记.md
订单状态机面试笔记.md
支付回调幂等面试笔记.md
RBAC权限体系面试笔记.md
缓存穿透与空值缓存面试笔记.md
Redis缓存两套实现对比说明.md
Redis序列化与幂等优化面试笔记.md
MQ面试笔记.md
限流与熔断降级面试笔记.md
配置安全与jasypt加密面试笔记.md
登录安全与密码加密面试笔记.md
单元测试覆盖面试笔记.md
数据库优化与B+树面试笔记.md
数据库索引优化.sql
分库分表设计方案.md
分库分表SQL设计.sql
WebSocket日志与订单状态推送规范.md
访问通道承载能力与高负载优化说明.md
AI开发环境说明.md
面试高频问答笔记.md
RBAC权限体系SQL设计.sql
+ 新手教学方案/（3 章正文 + 6 个 Demo）
```

**教学方案（`workbuddy/新手教学方案/`）** —— 已经做了 3 章，**是你要延续的模板**：

| 文件 | 内容 |
|------|------|
| `第1章_Redis缓存.md` | 缓存价值、穿透/击穿/雪崩三防、两套实现对比、序列化 |
| `第2章_事务与并发.md` | ACID、`@Transactional` 失效四大坑、隔离级别、雪花算法 |
| `第3章_多线程.md` | 竞态条件、synchronized/volatile/原子类、ThreadLocal、线程池 |
| `demo/` | **6 个零依赖可运行 Demo**（详见下表） |

**6 个 Demo（已实测通过，纯 Java 无第三方依赖）**：

| Demo | 演示内容 | 实测输出 |
|------|---------|---------|
| `Demo1_CounterBug` | 复现并发计数 bug | count = 99131（丢失 869 次） |
| `Demo2_FixCounter` | 三种修复方案对比 | synchronized ✅ / volatile ❌ / AtomicInteger ✅ |
| `Demo3_ThreadLocal` | ThreadLocal 跨线程丢失 | 子线程 get → null |
| `Demo4_CacheBreakdown` | 缓存击穿 + 互斥锁 | 10 并发 → 只查库 1 次 |
| `Demo5_Snowflake` | 时间戳 vs 雪花撞号 | 时间戳撞 9 次 / 雪花 0 次 |
| `Demo6_Transaction` | 事务回滚 | 异常时操作被撤销 |

> 运行提示：Windows 命令行需加 `-Dfile.encoding=UTF-8` 防中文乱码；IDEA 直接运行无需。

**成品文档（`output/a12e1077-.../stage3/`）**：
- `苍穹外卖项目全内容教学技术报告.docx`（10 章技术报告）
- `苍穹外卖新手教学方案.docx`（3 章教学方案）

---

## 4. 运行与开发环境

### 4.1 依赖服务（必须先起）

| 服务 | 默认端口 | 用途 |
|------|---------|------|
| MySQL | 3306 | 主库 `sky_take_out` |
| Redis | 6379 | 缓存 / 权限码 |
| RabbitMQ | 5672 | 订单异步通知 |
| nginx | 80 | 前端静态资源 + 反向代理 |

### 4.2 启动步骤

**① 环境变量（关键，不设必启动失败）**
```bash
# 必须是用户级/系统级环境变量，不要只配在 IDEA 里
JASYPT_ENCRYPTOR_PASSWORD = <向用户索取，勿写入任何文档>
```
> 为什么：`application-dev.yml` 里所有敏感配置都是 `ENC(...)` 密文，jasypt 需要主密码解密。**这个密码值只存在于用户本机环境变量中，不要传输、不要写进文档、不要提交到仓库。**

**② 起依赖服务**：MySQL → Redis → RabbitMQ → nginx

**③ 启动后端**
```bash
cd E:/sky-take-out-main/sky-take-out-main/sky-take-out
mvn clean install -DskipTests
cd sky-server
mvn spring-boot:run
# 或 IDEA 运行 SkyApplication
```

**④ 验证**
- 后端端口：`8080`
- 接口文档：`http://localhost:8080/doc.html`
- 管理端（nginx）：`http://localhost/`
- 登录账号：`admin / 123456`

### 4.3 环境版本注意事项

| 差异 | 说明 |
|------|------|
| 项目编译目标 Java 8 / 本机 JDK 17 | 可以用 JDK 17 编译，但**禁止使用 `var`、`record`、`switch` 表达式**等 Java 9+ 语法（前序会话踩过 `var` 的坑） |
| 本机 Node 22 / 前端要求 webpack 4 + Node 14~16 | **前端构建会失败**，见 5.3 |
| `sky-common` 不能依赖 `sky-pojo` | 否则模块循环依赖，构建失败 |

---

## 5. 已知问题与坑（必读，避免重复踩）

> 这一节是前序会话用**大量时间**换来的经验，请务必读完再动手。

### K1 · jasypt 解密失败（最高频）
- **现象**：`Failed to bind properties under 'sky.jwt.admin-secret-key' ... Required a bean of type 'String'`
- **根因**：环境变量 `JASYPT_ENCRYPTOR_PASSWORD` 没生效。
- **坑**：手改 IDEA 的 `workspace.xml` 加 VM 参数**不管用**，因为 IDEA 运行时会用自己的配置覆盖文件内容。
- **正解**：设为**用户级环境变量**（Windows：`setx JASYPT_ENCRYPTOR_PASSWORD "..."`），重启 IDEA。

### K2 · IDEA 模块冲突（找不到符号 TraceIdUtil）
- **现象**：`15 个错误 … 找不到符号 类 TraceIdUtil`
- **根因**：外层目录 `E:/sky-take-out-main` 的 `.idea/misc.xml` 里挂了 5 个 Maven 项目，其中包含**同名 `sky-take-out` 副本**，导致模块被识别成 `sky-server (1)` 并指向旧副本。
- **正解**：以 **`E:/sky-take-out-main/sky-take-out-main/sky-take-out`** 为工程根重新打开 IDEA（不是外层目录）。

### K3 · 前端构建失败（Node 22 太新）
- **现象**：`npm install` / `npm run build` 报 `node-sass` 编译失败
- **根因**：Vue 2 + webpack 4 项目的 `node-sass` 只支持 Node ≤ 16
- **现状**：**未修复**。`node_modules` 存在（1112 个包）但无 `dist`。后端不依赖前端构建，不影响后端开发与测试。
- **可选方案**：nvm 切 Node 16 / 把 `node-sass` 换 `sass`（dart-sass）/ 直接用已有的 nginx 静态包

### K4 · RabbitMQ 队列参数与测试隔离
- **坑 1**：`PRECONDITION_FAILED` —— 旧队列 `order.notify.queue` 创建时没有死信参数，后来代码加了死信参数，两者不匹配。需用管理 API `DELETE` 后重建。
- **坑 2**：测试会**抢生产队列的消息** —— 后台跑着的 `SkyApplication` 的 `OrderNotifyListener` 会秒消费测试消息。**正解**：测试改用**随机后缀的独立队列**。
- **坑 3**：`convertAndSend` 有四参重载，Mockito 用 `any()` 会歧义，要写 `any(MessagePostProcessor.class)`。

### K5 · 敏感文件绝不能入库
`.gitignore` 已排除（改动前请确认仍有效）：
```
application-dev.yml    ← 含所有 ENC 密文和 DB 配置
application-*.yml      ← 各类环境配置
.idea/  node_modules/  target/  logs/
*.pem  *.key  *.keystore  .env  secrets/
```
> 历史事故：前序会话发现 `workbuddy/` 笔记里**明文写了 3 处 jasypt 密钥**，已脱敏。你新增任何文档时，**不要贴密钥、不要贴 ENC 密文对应的明文**。

### K6 · `@RateLimiter` fallback 方法签名
`fallbackMethod` 指向的方法，参数列表必须与原方法**完全一致**，且末尾可加一个 `Throwable` 参数。签名不匹配会在启动时报 `NoSuchMethodException`。

### K7 · OrderStatus 不能复用 Orders 实体
`OrderStatus` 在 `sky-common`，`Orders` 实体在 `sky-pojo`。`sky-common` 依赖 `sky-pojo` 会造成**模块循环依赖**。因此 `OrderStatus` 自己维护状态码字面量。**不要"优化"成引用实体**。

---

## 6. 下一步建议（给你的 brief）

### 6.1 教学：待补的四章（第一优先级）

已有三章（Redis / 事务并发 / 多线程）是最底层的基础。**剩下的高价值主题**建议按此顺序补，每章沿用**同一套五步闭环**，并配对应的可运行 Demo：

| 建议章节 | 核心知识点 | 建议 Demo |
|---------|-----------|----------|
| **第 4 章 · 消息队列** | 为什么用 MQ、削峰/解耦/异步、可靠性三问、死信队列、幂等消费 | 用 `BlockingQueue` 模拟 MQ，对比"同步阻塞 10s"vs"异步立即返回"；演示消息丢失与 ACK |
| **第 5 章 · 认证授权** | JWT 结构（Header.Payload.Signature）、签名验证、无状态 vs Session、RBAC 模型、401/403 区别 | 手写一个 mini JWT（Base64 + HMAC），演示改 payload 后签名失效 |
| **第 6 章 · 状态机与幂等** | 状态机建模、终态设计、幂等三种方案（唯一索引/状态判断/分布式锁）、支付回调为什么要幂等 | Demo 演示"重复回调两次"在有/无幂等判断下的结果差异 |
| **第 7 章 · 可观测性与防护** | ThreadLocal 与 MDC、日志规范、限流四算法、熔断降级 | 复用 Demo3 讲 ThreadLocal，新增限流算法对比 Demo |

**教学模板（务必沿用）**：
```
1. 生活化比喻（先建立直觉，不要一上来就讲概念）
2. 核心概念（三句话内说清）
3. 可运行 Demo（最小可复现，能亲手跑）
4. 逐行注释（每一句在干嘛）
5. 对照项目真实代码（指到具体文件和行号）
6. 本章自测题（3~5 题，答不上来说明没学会）
```

### 6.2 工程：改进待办（第二优先级）

**P0（用户会问、面试会考，优先做）**
- [ ] 修复前端 Node 兼容性（`node-sass` → `sass`），让管理端能 `npm run build`
- [ ] 给 RBAC 补一个端到端测试（无权限员工调管理接口 → 断言 403）
- [ ] `OrderStatus` 流转补全表格化文档（一张图看全所有合法转移）

**P1（锦上添花，能讲出深度）**
- [ ] 缓存一致性：`@CacheEvict` 改成"先更新库再删缓存 + 延迟双删"
- [ ] 分布式锁（Redisson）落地：用于下单扣库存 / 防重复提交
- [ ] 链路追踪可视化：接入 SkyWalking，把 traceId 串成调用链
- [ ] 慢 SQL 治理：开启慢查询日志，给高频查询补复合索引（已有 `数据库索引优化.sql` 可参考）

**P2（简历亮点，工作量大）**
- [ ] 分库分表落地（已有 `分库分表设计方案.md`，目前只是方案，未实施）
- [ ] 消息可靠性再加固：本地消息表 + 定时补偿
- [ ] 接口幂等框架化（自定义 `@Idempotent` 注解 + Redis 实现）

### 6.3 用户已做出的最终决策（2026-11-13 确认）

| 事项 | 决策 | 说明 |
|------|------|------|
| 项目改名（曾讨论 **FoodForge**） | ❌ **已否决，不改名** | 用户明确决定：仓库名保持 `sky` 不变，放弃 FoodForge 方案。**同时也不要改**代码包名 `com.sky`、模块名 `sky-common` / `sky-pojo` / `sky-server`、Maven 坐标 `com.sky:sky-take-out`。保持原样。 |
| GitHub 仓库去留 | ⏳ **等用户手动执行** | 用户 GitHub 账号 `gw34744` 下共 7 个仓库，**只保留 `sky`（本项目）**，其余 6 个全部删除：`Java` / `java-design-patterns` / `awesome-for-beginners` / `guava` / `OnJava8` / `student-java`。这 6 个里 5 个是 fork 的学习仓库，`student-java` 是用户早期原创项目（用户已确认本地有备份/不再需要）。**删除必须在 GitHub 网页操作**（Settings → 拉到底 Danger Zone → Delete this repository → 输入仓库名确认）。 |

> **两条硬约束，请严格遵守：**
> 1. **不要再提改名**。这个议题已经关闭，用户已明确否决。任何"建议改个更有辨识度的名字"都属于重复打扰。
> 2. **不要代为删除仓库**。删除不可逆，且发生在用户云端账号，本机也没有 `gh` CLI 可用。只能由用户本人在 GitHub 网页操作，你可以给步骤指引，但不能代做。

---

## 7. 关键文件索引（按用途）

| 用途 | 路径 |
|------|------|
| 启动类 | `sky-server/.../SkyApplication.java` |
| 事务 + 下单 + 状态流转 | `sky-server/.../service/impl/OrderServiceImpl.java` |
| 状态机定义 | `sky-common/.../enumeration/OrderStatus.java` |
| 雪花算法 | `sky-common/.../utils/SnowflakeIdUtil.java` |
| 链路追踪 | `sky-common/.../context/TraceIdUtil.java`、`sky-server/.../filter/TraceIdFilter.java` |
| 日志配置 | `sky-server/src/main/resources/logback-spring.xml` |
| 跨线程 MDC | `sky-server/.../config/MdcTaskDecorator.java`、`AsyncConfiguration.java` |
| 权限注解 | `sky-common/.../annotation/RequirePermission.java` |
| 权限服务 | `sky-server/.../service/PermissionService.java` |
| 登录鉴权 | `sky-server/.../interceptor/JwtTokenAdminInterceptor.java` |
| 缓存配置 | `sky-server/.../config/RedisConfiguration.java`、`CacheConfiguration.java` |
| MQ 配置 / 消费 | `sky-server/.../config/RabbitMQConfiguration.java`、`.../listener/OrderNotifyListener.java` |
| 限流 | `sky-server/.../controller/user/OrderController.java`（69、101 行） |
| WebSocket | `sky-server/.../websocket/WebSocketServer.java`、`.../task/WebSocketTask.java` |
| 前端消息处理 | `project-rjwm-admin-vue-ts/src/layout/components/Navbar/index.vue` |
| 测试 | `sky-server/src/test/java/com/sky/**` |
| 技术笔记 | `workbuddy/*.md` |
| 教学方案 | `workbuddy/新手教学方案/` |

---

## 8. 给你的开场指令（可直接复制的 brief）

如果你（GPT-6.1）需要一段自述来开始工作，可以用这段：

> 我在接手一个 Java 后端项目的教学与改进工作。项目是基于黑马《苍穹外卖》改造的增强版（Spring Boot 2.7.3 + MyBatis + MySQL + Redis + RabbitMQ），已经落地了事务、雪花算法、jasypt 配置加密、MQ 异步幂等、订单状态机、MDC 链路追踪、RBAC 权限、缓存三防、Resilience4j 限流、BCrypt 和 63 个测试用例，代码在 `E:/sky-take-out-main/sky-take-out-main`，远程 `github.com/gw34744/sky`。
>
> 我的用户是转行求职的 Java 学习者，之前是"许愿式开发"——AI 直接把功能实现了，但他没真正学会原理。所以我的首要任务**不是加功能，而是教学**：把已有功能用"生活化比喻 → 概念 → 可运行 Demo → 逐行注释 → 对照项目真实代码 → 自测题"的方式讲透。
>
> 目前已完成三章教学（Redis 缓存 / 事务与并发 / 多线程），带 6 个零依赖可运行 Demo。接下来请继续补：消息队列、JWT 与 RBAC、状态机与幂等、可观测性与限流，沿用同样模板。每章都要能让我亲手把 Demo 跑起来看到效果。
>
> 详细背景见交接报告：用户画像、14 项增强的问题-方案-文件-面试价值、63 个测试清单、以及 7 个已踩过的坑（jasypt 环境变量、IDEA 模块冲突、前端 Node 22、RabbitMQ 队列参数等）。动手前请先读第 5 节"已知问题与坑"。

---

## 附录 · 本报告的事实来源

| 结论 | 验证方式 |
|------|---------|
| git 状态、提交历史、文件数 | `git log` / `git ls-files` / `git status`（387 文件，12 提交，工作区干净） |
| 本地 = 远程 | 提交哈希比对：本地 `4dd442c1fe44...` = 远程 `4dd442c1fe44...` |
| 63 个测试用例 | 逐文件 `grep -c "@Test"` 统计（11 类，63 用例） |
| 增强项存在 | 逐个文件 `ls` + `wc -l` 校验（9 个核心文件全部存在） |
| 限流 / BCrypt / 序列化实现 | 源码 `grep`：`@RateLimiter`、`BCryptPasswordEncoder`、`GenericJackson2JsonRedisSerializer` |
| 敏感文件已隔离 | `git check-ignore -v application-dev.yml` → 命中 `.gitignore:33` |
| 前端未构建 | `node_modules` 存在但无 `dist`，Node 版本 v22.22.2 |

> **报告中未包含任何真实密钥、密码或 ENC 密文的明文对应关系。** jasypt 主密码仅存在于用户本机环境变量中，需要时请向用户本人索取。
