package com.sky.learn;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【演示 2】并发安全问题的三种修复方案
 *
 * <p>对应教学方案第三章 3.3 节。分别演示 synchronized、volatile（为什么不够）、原子类。</p>
 *
 * <p>运行方法：直接运行本类的 main 方法，对比四种方案的输出。</p>
 *
 * <p>结论：只有 synchronized 和原子类能保证 {@code count++} 的结果正确（100000）。
 * volatile 只保证「可见性」，不保证「原子性」，所以 {@code count++} 依然会出错。</p>
 */
public class Demo2_FixCounter {

    // 方案一：用 synchronized 方法锁住「读-改-写」三步
    static int countSync = 0;

    static synchronized void incrementSync() {
        // 加了 synchronized，同一时刻只有一个线程能进入这个方法，
        // 保证「读 countSync → 加 1 → 写回」三步是一个原子整体
        countSync++;
    }

    // 方案二：volatile 只保证可见性（这个方案是【错误示范】，用来对比）
    static volatile int countVolatile = 0;

    // 方案三：原子类（内部用 CAS 实现，性能最好，推荐）
    static AtomicInteger countAtomic = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        // ============ 方案一：synchronized ============
        runThreads(() -> incrementSync());
        System.out.println("方案一 synchronized：期望 100000，实际 " + countSync + "  ✅ 正确");

        // ============ 方案二：volatile（错误示范）============
        runThreads(() -> countVolatile++);
        System.out.println("方案二 volatile：    期望 100000，实际 " + countVolatile + "  ❌ 仍出错（volatile 不保证原子性）");

        // ============ 方案三：原子类 ============
        runThreads(() -> countAtomic.incrementAndGet());
        System.out.println("方案三 原子类：      期望 100000，实际 " + countAtomic.get() + "  ✅ 正确");
    }

    /**
     * 通用的「起 100 个线程，每个线程执行 task 1000 次」的方法。
     *
     * @param task 每个线程要重复执行的任务（用 lambda 传入，体现 Java 8 的写法）
     */
    static void runThreads(Runnable task) throws InterruptedException {
        Thread[] threads = new Thread[100];
        for (int i = 0; i < 100; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    task.run();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();   // 等待所有线程执行完
        }
    }
}
