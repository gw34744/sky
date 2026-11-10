# 新手教学演示代码

配合《新手教学方案》使用的**可运行演示项目**。纯 Java，无任何外部依赖，直接编译运行即可。

## 怎么运行

### 方式一：IDEA 直接运行（推荐）

1. 用 IDEA 打开本目录（`demo/`）；
2. 等待 Maven 导入完成（或直接当普通 Java 项目用）；
3. 找到 `src/main/java/com/sky/learn/` 下的任意 `Demo*.java`；
4. 右键 → Run，即可看到输出。

### 方式二：命令行编译运行

```bash
# 编译所有演示
javac -encoding UTF-8 src/main/java/com/sky/learn/*.java -d out

# 运行某个演示（以 Demo1 为例）
# 注意：Windows 命令行默认 GBK 编码，加 -Dfile.encoding=UTF-8 避免中文乱码
java -Dfile.encoding=UTF-8 -cp out com.sky.learn.Demo1_CounterBug
```

> ⚠️ Windows 命令行运行务必加 `-Dfile.encoding=UTF-8`，否则中文输出会乱码。
> 用 IDEA 直接运行则不需要（IDEA 默认 UTF-8）。

### 方式三：Maven 编译

```bash
mvn compile          # 编译
mvn exec:java -Dexec.mainClass=com.sky.learn.Demo1_CounterBug   # 运行（需 exec 插件）
```

## 演示清单（建议按顺序跑）

| 演示 | 对应章节 | 讲什么 | 关键现象 |
|------|---------|--------|---------|
| `Demo1_CounterBug` | 第三章 3.2 | 多线程竞态条件 | count 结果 < 100000，且每次不同 |
| `Demo2_FixCounter` | 第三章 3.3 | 三种并发修复方案 | 只有 synchronized 和原子类正确 |
| `Demo3_ThreadLocal` | 第三章 3.4 | ThreadLocal 线程隔离 | 子线程 get 到 null（跨线程丢失） |
| `Demo4_CacheBreakdown` | 第一章 1.4.2 | 缓存击穿防护 | 10 个并发请求，查库只 1 次 |
| `Demo5_Snowflake` | 第二章 2.5 | 雪花算法 vs 时间戳 | 时间戳撞号，雪花不撞 |
| `Demo6_Transaction` | 第二章 2.3 | 事务提交与回滚 | 异常时操作被撤销 |

## 学习建议

1. **先跑 Demo1**，亲眼看到并发 bug，再学修复方案；
2. 每个 Demo 的代码里都有**大量中文注释**，请逐行读，不要跳；
3. 跑完之后，回到苍穹外卖项目，用学到的知识重新读 `DishController`、`OrderServiceImpl`、`BaseContext` 等文件，你会发现它们不再"神秘"。

> 这些 Demo 是「简化版」，目的是让你**理解原理**。项目里的真实代码（如完整版 `SnowflakeIdUtil`、Redis 的 `setIfAbsent`）是「工程版」，加了更多健壮性处理，但核心思想完全一致。
