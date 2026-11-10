package com.sky.learn;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【演示 4】缓存击穿的并发模拟 —— 用互斥锁保证"查库只发生一次"
 *
 * <p>对应教学方案第一章 1.4.2 节「缓存击穿」，也演示了第三章的 CountDownLatch 和原子类。</p>
 *
 * <p>这个 Demo 用纯 Java 模拟了项目里 {@code DishController.list} 的缓存击穿防护逻辑：</p>
 * <ol>
 *   <li>用一个 AtomicInteger 模拟「数据库查询次数」；</li>
 *   <li>用一个 AtomicInteger 模拟「互斥锁」（用 CAS 的 compareAndSet 实现，等价于 Redis 的 setIfAbsent）；</li>
 *   <li>用 CountDownLatch 让 10 个线程"同时起跑"，制造真正的并发；</li>
 *   <li>最后断言：10 个并发请求，数据库只被查询了 1 次（其余 9 个都等到了缓存）。</li>
 * </ol>
 *
 * <p>运行方法：直接运行本类的 main 方法。</p>
 *
 * <p>关键结论：这就是「缓存击穿防护」的本质——用互斥锁让并发请求中只有一个去查库，
 * 其他线程等待缓存重建后直接读缓存。</p>
 */
public class Demo4_CacheBreakdown {

    // 模拟"数据库查询次数"
    static AtomicInteger dbQueryCount = new AtomicInteger(0);

    // 模拟"缓存"（用一个简单的变量，null 表示缓存为空）
    static volatile String cache = null;

    // 模拟"互斥锁"：0 表示未加锁，1 表示已加锁
    // compareAndSet(0, 1) 就是 Redis 里 setIfAbsent 的等价实现
    static AtomicInteger lock = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 10;   // 模拟 10 个并发请求

        // 三个 CountDownLatch：ready=就位，start=发令枪，done=跑完
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();   // 通知主线程：我已就位
                    start.await();       // 等发令枪，保证 10 个线程同时起跑

                    queryDish();         // 模拟一次"查菜品"请求

                    done.countDown();    // 通知主线程：我跑完了
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();      // 等 10 个线程都就位
        start.countDown();  // 发令：所有线程同时开始查

        done.await(5, TimeUnit.SECONDS);  // 等所有线程跑完
        pool.shutdown();

        System.out.println("========== Demo4 结果 ==========");
        System.out.println("并发请求数：" + threadCount);
        System.out.println("数据库实际查询次数：" + dbQueryCount.get());
        System.out.println(dbQueryCount.get() == 1
                ? "✅ 击穿防护生效：10 个并发请求，数据库只被查了 1 次！"
                : "❌ 击穿防护失效：数据库被查了多次，缓存没拦住并发。");
    }

    /**
     * 模拟一次「查菜品」请求，完整复刻项目里缓存击穿防护的逻辑。
     */
    static void queryDish() throws InterruptedException {
        // 第 1 步：先查缓存
        if (cache != null) {
            // 缓存命中，直接返回，不查库
            return;
        }

        // 第 2 步：缓存未命中，尝试抢互斥锁（等价 Redis 的 setIfAbsent）
        // compareAndSet(0, 1)：只有当锁是 0（没人抢到）时，才把它改成 1（抢到锁），返回 true
        boolean gotLock = lock.compareAndSet(0, 1);

        if (gotLock) {
            // 抢到锁：只有这个线程去查库重建缓存
            try {
                // 双重校验：抢到锁的瞬间，可能别的线程已经重建了缓存
                if (cache != null) {
                    return;
                }
                // 模拟"查数据库"（耗时操作）
                Thread.sleep(100);
                dbQueryCount.incrementAndGet();   // 记录：查了一次库
                cache = "菜品列表数据";            // 查完回填缓存
            } finally {
                lock.set(0);   // 释放锁，让其他线程能抢
            }
        } else {
            // 没抢到锁：说明有别的线程正在重建缓存，稍等片刻后重试
            Thread.sleep(50);
            queryDish();   // 递归重试：此时缓存应该已经被重建了
        }
    }
}
