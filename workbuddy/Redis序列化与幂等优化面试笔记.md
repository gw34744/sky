# Redis 序列化与幂等优化面试笔记

> 对应代码改动：`RedisConfiguration`、`CacheConfiguration`、`OrderController.submit`
> 主题：解决黑马默认教学状态遗留的 Redis 缺陷

---

## 一、改了什么、为什么

黑马原版的 Redis 用法有两个「默认状态」遗留问题，本笔记对应修复。

### 问题 1：RedisTemplate 的 value 序列化器缺失（JDK 默认序列化）

**原代码**：
```java
@Bean
public RedisTemplate redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate redisTemplate = new RedisTemplate();
    redisTemplate.setConnectionFactory(factory);
    redisTemplate.setKeySerializer(new StringRedisSerializer()); // 只配了 key
    return redisTemplate; // value 没配 → 默认 JDK 序列化
}
```

**问题**：
- value 以 JDK 二进制流（`\xAC\xED\x00\x05...`）存进 Redis，redis-cli 肉眼不可读，出问题无法排查；
- 实体类必须实现 `Serializable` 接口，否则存值时抛 `NotSerializableException`；
- 跨语言不可用：其他语言服务（Go/Python/Node）读不了 JDK 序列化的数据；
- 序列化后的字节长度偏大，占用更多内存。

**修复**：key 用 `StringRedisSerializer`，value 用 `GenericJackson2JsonRedisSerializer`（JSON + 类型信息）。与 `CacheConfiguration`（Spring Cache 套餐缓存）的序列化策略对齐。

```java
redisTemplate.setKeySerializer(new StringRedisSerializer());
redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
```

**面试追问点**：
- `GenericJackson2JsonRedisSerializer` 会写入 `@class` 类型信息（`{"@class":"com.sky.vo.DishVO", ...}`），反序列化时能还原成原类型，所以 `(List<DishVO>) redisTemplate.get(key)` 强转没问题。
- 相比 `Jackson2JsonRedisSerializer`（不带类型信息，反序列化时要手动指定 Class），`Generic` 版更省心，但也更占空间、且有反序列化安全风险（需限制反序列化白名单）。

---

### 问题 2：submitToken 幂等逻辑不严谨

**原代码**：
```java
Boolean removed = redisTemplate.delete(tokenKey);
if (submitToken == null || !Boolean.TRUE.equals(removed)) {
    return Result.error("请勿重复提交订单");
}
```

**问题**：
1. 用 `delete` 做「占用」不比对 token 值——用户传任意 token，只要 key 存在就删掉并放行，**没校验 token 是否一致**；
2. 注释里写的「setIfAbsent 原子删除」，实际代码却是 `delete`，`delete` 本身是原子的，但「先删后判」的语义不对：两个并发请求同时 `delete`，可能都返回 true。

**修复**：先 `get` 比对 Redis 中的 token 与请求携带的 token，一致才删除放行。

```java
Object cached = redisTemplate.opsForValue().get(tokenKey);
if (submitToken == null || submitToken.isEmpty() || !submitToken.equals(cached)) {
    return Result.error("请勿重复提交订单");
}
redisTemplate.delete(tokenKey); // 一次性，用完即删
```

**面试追问点**：
- 为什么「先 get 比对再 delete」而非直接 delete？答：token 是用户下单的「一次性凭证」，必须校验请求携带的 token 与后端签发的一致，否则任何持有 userId 的人都能伪造提交。
- 更严谨的原子方案：用 `setIfAbsent` 在提交时原子「抢占」一个处理标记（`SET NX`），或者用 Lua 脚本把「比对 + 删除」两步合成一个原子操作，避免并发窗口。
- 这里 get + delete 之间有微小窗口，但 token 一次性 + 10 分钟过期，实际影响有限；面试可主动点出「若要极致严谨用 Lua 脚本原子化」。

---

### 问题 3：CacheConfiguration 随机 TTL 的误导写法

**原代码**：
```java
private static final Duration CACHE_TTL = Duration.ofMinutes(30 + (long) (Math.random() * 10));
```

**问题**：
- `static final` 字段在**类加载时只执行一次**，`Math.random()` 算一次就固定了，所有套餐缓存共用一个 TTL，**并没有真正随机**，防雪崩效果打折扣；
- `Math.random()` 全局锁、不可控、不可测试。

**修复**：在 `cacheManager()` 方法内用 `ThreadLocalRandom.current().nextInt(10)` 叠加随机值。

```java
Duration ttl = BASE_TTL.plus(Duration.ofMinutes(ThreadLocalRandom.current().nextInt(10)));
```

**面试追问点**：
- `ThreadLocalRandom` vs `Math.random()`：`Math.random()` 底层是全局共享的 `Random` 实例（有 CAS 竞争），`ThreadLocalRandom` 每个线程独立随机源，无锁竞争，高并发下性能更好。
- 随机 TTL 的意义：防止缓存雪崩（大量 key 同一时刻过期，请求同时打到 DB）。

---

## 二、Redis 三大问题回顾（与本次改动关联）

| 问题 | 场景 | 本项目方案 |
|------|------|-----------|
| 缓存穿透 | 查不存在的数据 | 空值缓存（空值短 TTL 5 分钟） |
| 缓存击穿 | 热点 key 过期瞬间 | setIfAbsent 互斥锁 + 自旋 |
| 缓存雪崩 | 大量 key 同时过期 | 随机 TTL（本次修正写法） |

---

## 三、Redis 序列化选型对比

| 序列化器 | key 是否可读 | value 是否可读 | 需 Serializable | 跨语言 | 适用 |
|---------|------------|--------------|----------------|--------|------|
| JDK 默认 | ❌ 乱码 | ❌ 二进制 | ✅ 需要 | ❌ | 教学演示（不推荐） |
| StringRedisSerializer | ✅ | 仅字符串 | ❌ | ✅ | 纯字符串/数字场景 |
| GenericJackson2JsonRedisSerializer | ✅ | ✅ JSON | ❌ | ✅ | 对象、List、复杂结构 |
| Jackson2JsonRedisSerializer | ✅ | ✅ JSON | ❌ | ✅ | 需要指定反序列化 Class |

---

## 四、高频面试问答

**Q1：为什么 Redis 的 key 要加冒号前缀，如 `dish_{id}`？**
A：命名空间隔离 + 便于 `keys dish_*` 批量清理 + 语义清晰。前缀能避免不同类型数据 key 冲突。

**Q2：RedisTemplate 和 StringRedisTemplate 区别？**
A：StringRedisTemplate 是 RedisTemplate<String,String> 的特化，key/value 都用 String 序列化，适合纯文本；RedisTemplate 更通用，可自定义序列化器，适合存对象。

**Q3：序列化器改成 JSON 后，老数据（JDK 序列化）还能读吗？**
A：不能。序列化格式变了，老数据是 JDK 二进制，新序列化器读不出来。生产迁移要 flushdb 或双写过渡。本项目 Redis 目前为空，无兼容问题。

**Q4：为什么 token 幂等要「比对值」而不是「只判断 key 是否存在」？**
A：key 存在只能说明「发过 token」，不能证明「请求携带的 token 是对的」。必须比对值，防止伪造/篡改。

---

## 五、改动清单速记

| 文件 | 改动 |
|------|------|
| `RedisConfiguration.java` | value 序列化器改为 JSON，RedisTemplate 泛型化 |
| `CacheConfiguration.java` | 随机 TTL 用 ThreadLocalRandom 修正 |
| `OrderController.java` | submitToken 幂等改为「get 比对 + 删除」 |
