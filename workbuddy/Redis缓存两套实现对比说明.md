# Redis 缓存两套实现对比：RedisTemplate 手动 vs Spring Cache 注解

> 本项目里「菜品」和「套餐」都做了 Redis 缓存，但用了两种不同的方式：
> - **菜品** → RedisTemplate 手动读写缓存
> - **套餐** → Spring Cache 注解（`@Cacheable` / `@CacheEvict`）
>
> 这是面试官最爱追问的点——能讲清楚两者的区别和各自优劣，是「真正理解了缓存」而非「照抄代码」的关键证据。

## 一、代码对照（本项目的真实实现）

### 菜品（RedisTemplate 手动）

`user/DishController#list` 查询缓存：

```java
String key = "dish_" + categoryId;              // 手动拼接 key
List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);

if (list != null && list.size() > 0) {
    return Result.success(list);                 // 命中，直接返回
}

Dish dish = new Dish();
dish.setCategoryId(categoryId);
dish.setStatus(StatusConstant.ENABLE);
list = dishService.listWithFlavor(dish);         // 未命中，查库
redisTemplate.opsForValue().set(key, list);      // 手动写入缓存
return Result.success(list);
```

`admin/DishController` 手动清理缓存：

```java
private void clearCache(String pattern) {
    Set keys = redisTemplate.keys(pattern);      // 按通配符找 key
    redisTemplate.delete(keys);                  // 批量删除
}
// 新增/修改/删除/上下架菜品时，调用 clearCache("dish_*")
```

### 套餐（Spring Cache 注解）

`user/SetmealController#list` 自动缓存：

```java
@Cacheable(cacheNames = "setmealCache", key = "#categoryId")
public Result<List<Setmeal>> list(Long categoryId) { ... }
```

`admin/SetmealController` 自动失效：

```java
@CacheEvict(cacheNames = "setmealCache", key = "#setmealDTO.categoryId")  // 精准失效
@CacheEvict(cacheNames = "setmealCache", allEntries = true)               // 清空整个缓存
```

## 二、核心区别

| 维度 | RedisTemplate 手动缓存 | Spring Cache 注解 |
|------|------------------------|-------------------|
| 实现方式 | 自己写 `get` / `set` / `delete` 逻辑 | 注解声明式，AOP 自动代理 |
| 代码侵入性 | 缓存逻辑混在业务代码里，Controller 变臃肿 | 业务代码干净，缓存是横切关注点 |
| key 管理 | 手动拼接（如 `"dish_" + categoryId`） | 用 SpEL 表达式（`#categoryId`） |
| 失效策略 | 手动 `keys(pattern)` + `delete`，容易漏 | `@CacheEvict` 自动，支持精准 / 全量 |
| 灵活性 | 高，可精确控制何时读写、序列化方式、过期时间 | 相对受限，受注解能力约束 |
| 可控性 / 可读性 | 每一步可见，但样板代码多 | 简洁，但底层被 AOP 隐藏，排查需理解代理 |
| 序列化 | 需自行配置（本项目只配了 key 用 String，value 默认 JDK 序列化） | 同样依赖 RedisCacheManager 的序列化配置 |
| 缓存穿透等高级问题 | 需自己处理（手动判空、空值缓存等） | 注解层面不解决，需额外配置 |

## 三、各自的优缺点

### RedisTemplate 手动缓存
**优点**
- 灵活度最高：能精确控制缓存时机、过期时间、数据结构（String/Hash/List/Set/ZSet）、序列化方式；
- 数据操作能力全：除了缓存，还能做排行榜、计数器、分布式锁、消息队列等。

**缺点**
- 样板代码多，每个查询接口都要重复「查缓存 → 判空 → 查库 → 写缓存」；
- 缓存逻辑与业务逻辑耦合，Controller 职责不清（本项目的 DishController 就因此变重）；
- 失效逻辑分散，容易漏删导致脏数据（本项目用 `dish_*` 通配删除是简单粗暴的兜底）。

### Spring Cache 注解
**优点**
- 声明式、零侵入，业务方法只关注业务本身；
- 失效自动化（`@CacheEvict` 精准/全量），不易漏；
- 统一由 `CacheManager` 管理，切换底层缓存（Redis / Caffeine / EhCache）成本低。

**缺点**
- 灵活性受限：过期时间、序列化等需在全局 `CacheManager` 统一配置，难以按 key 差异化；
- 底层被 AOP 隐藏，出问题时排查链路更绕；
- 无法天然解决缓存穿透/击穿/雪崩，需要额外手段（如配置 TTL、空值缓存、互斥锁等）。

## 四、面试常见追问与答题思路

1. **"为什么菜品用 RedisTemplate、套餐用 Spring Cache？"**
   这是教学视频里刻意设计的对比：菜品想展示手动缓存的完整控制流程（读、写、清理），套餐想展示注解式缓存的简洁。答出「两者都是缓存，只是封装层级不同」即可加分。

2. **"缓存穿透、击穿、雪崩分别是什么？怎么解决？"**
   - 穿透：查不存在的 key，每次都打到 DB → 空值缓存 / 布隆过滤器；
   - 击穿：某个热点 key 过期瞬间大量并发 → 互斥锁 / 逻辑过期；
   - 雪崩：大量 key 同时过期 → 过期时间加随机值 / 集群高可用。

3. **"Redis 缓存和数据库一致性怎么保证？"**
   本项目采用「先更新 DB，再删缓存」的策略（`Cache Evict` 或 `clearCache`），即旁路缓存（Cache-Aside）模式。可补充：删缓存而非更新缓存，配合过期兜底。

4. **"为什么缓存要设置过期时间？"**
   防止内存无限增长、以及过期是保证最终一致性的兜底手段。

## 五、一句话总结

> RedisTemplate 是「手动挡」，控制力强但繁琐；Spring Cache 是「自动挡」，简洁但灵活度受限。两者本质都是把热点数据从 DB 搬到 Redis，只是封装层次不同——能讲清这一点，就说明你对缓存有整体理解，而不是只会背注解。
