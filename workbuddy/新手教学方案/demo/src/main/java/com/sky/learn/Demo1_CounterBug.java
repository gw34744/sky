package com.sky.learn;

/**
 * 【演示 1】多线程共享变量的经典 bug —— 竞态条件
 *
 * <p>对应教学方案第三章 3.2 节。</p>
 *
 * <p>运行方法：直接运行本类的 main 方法。</p>
 *
 * <p>现象：期望结果是 100000，但几乎每次运行都会得到一个更小的数（比如 97843），
 * 而且每次结果都不一样。</p>
 *
 * <p>原因：{@code count++} 表面是一行代码，底层实际是三步操作——
 * ① 读取 count 当前值 → ② 加 1 → ③ 写回 count。
 * 多个线程可能同时"读到"相同的旧值，再各自加 1 写回，导致其中一次加法被"覆盖丢失"。
 * 这就是「竞态条件（Race Condition）」。</p>
 *
 * <p>请先跑出这个 bug，再去看 Demo2 的三种修复方案，理解会更深刻。</p>
 */
public class Demo1_CounterBug {

    // 共享变量：100 个线程会同时操作它
    static int count = 0;

    public static void main(String[] args) throws InterruptedException {
        // 起 100 个线程，每个线程给 count 加 1000 次
        Thread[] threads = new Thread[100];
        for (int i = 0; i < 100; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    count++;   // ❌ 这一步不是原子操作，多线程下会出错
                }
            });
            threads[i].start();   // 启动线程
        }

        // join()：让主线程等待所有子线程执行完，再往下走
        for (Thread t : threads) {
            t.join();
        }

        // 打印结果：期望 100 * 1000 = 100000
        System.out.println("========== Demo1 结果 ==========");
        System.out.println("期望值：100000");
        System.out.println("实际值：" + count + "  （几乎总是小于 100000，且每次不同）");
        System.out.println("原因：count++ 不是原子操作，发生了竞态条件。");
    }
}
