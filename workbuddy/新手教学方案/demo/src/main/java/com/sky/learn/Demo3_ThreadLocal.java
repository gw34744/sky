package com.sky.learn;

/**
 * 【演示 3】ThreadLocal —— 每个线程自己的"私人物品柜"
 *
 * <p>对应教学方案第三章 3.4 节，也是理解项目里 {@code BaseContext} 的基础。</p>
 *
 * <p>运行方法：直接运行本类的 main 方法。</p>
 *
 * <p>观察要点：</p>
 * <ol>
 *   <li>每个线程往里 set 的值，只有自己 get 得到，别的线程 get 到的是 null；</li>
 *   <li>主线程 set 的值，子线程【读不到】（这就是"跨线程丢失"，是支付回调 bug 和
 *       MDC traceId 丢失的根源）；</li>
 *   <li>用完要 remove，否则线程池复用线程时会读到残留数据。</li>
 * </ol>
 */
public class Demo3_ThreadLocal {

    // 定义一个 ThreadLocal：每个线程都有自己独立的一份 String
    static ThreadLocal<String> threadLocal = new ThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        // 主线程 set 一个值
        threadLocal.set("我是主线程的值");
        System.out.println("[主线程] get = " + threadLocal.get());

        // 起一个子线程
        Thread child = new Thread(() -> {
            // 子线程里 get，拿到的是 null（ThreadLocal 不跨线程！）
            System.out.println("[子线程] get = " + threadLocal.get() + "  ← 注意是 null，跨线程丢失！");

            // 子线程自己 set 一个值
            threadLocal.set("我是子线程的值");
            System.out.println("[子线程] set 后再 get = " + threadLocal.get());
        });
        child.start();
        child.join();   // 等子线程跑完

        // 主线程再次 get，还是自己的值，没被子线程污染
        System.out.println("[主线程] 子线程跑完后 get = " + threadLocal.get());

        // 用完清理（重要！否则线程池复用会内存泄漏 / 串数据）
        threadLocal.remove();
        System.out.println("[主线程] remove 后 get = " + threadLocal.get());
    }
}
