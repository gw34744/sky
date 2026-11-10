package com.sky.learn;

/**
 * 【演示 6】事务的提交与回滚 —— 理解 @Transactional 在背后做什么
 *
 * <p>对应教学方案第二章 2.3 节。</p>
 *
 * <p>运行方法：直接运行本类的 main 方法。</p>
 *
 * <p>这个 Demo 用「模拟账户」演示事务的核心思想：
 * 一组操作要么全部成功（提交），要么出现异常时全部撤销（回滚）。
 * Spring 的 @Transactional 就是把这个逻辑自动化了——你不需要手写 try/catch。</p>
 */
public class Demo6_Transaction {

    public static void main(String[] args) {
        // 场景一：转账成功（提交）
        System.out.println("===== 场景一：转账成功（事务提交） =====");
        Account a = new Account("张三", 1000);
        Account b = new Account("李四", 500);
        transfer(a, b, 100, false);   // 不模拟异常

        // 场景二：转账失败（回滚）
        System.out.println();
        System.out.println("===== 场景二：转账第二步失败（事务回滚） =====");
        Account c = new Account("王五", 1000);
        Account d = new Account("赵六", 500);
        transfer(c, d, 100, true);    // 模拟第二步异常

        System.out.println();
        System.out.println("结论：Spring 的 @Transactional 就是自动帮你做这件事——");
        System.out.println("方法正常结束就「提交」，抛异常就「回滚」。");
        System.out.println("如果没有事务，场景二就会变成：王五的钱被扣了，但赵六没收到钱（钱凭空消失）。");
    }

    /**
     * 模拟一次转账（两个账户的余额变动）。
     *
     * @param from    转出账户
     * @param to      转入账户
     * @param amount  金额
     * @param fail    是否模拟第二步失败（用于演示回滚）
     */
    static void transfer(Account from, Account to, int amount, boolean fail) {
        System.out.println("--- 事务开始 ---");

        // 先备份 from 的余额，用于回滚时恢复
        int fromBackup = from.balance;
        int toBackup = to.balance;

        try {
            // 操作 1：转出账户扣钱
            from.balance -= amount;
            System.out.println("  操作1 完成：" + from.name + " 扣 " + amount + "，余额 " + from.balance);

            // 模拟操作 2 失败
            if (fail) {
                throw new RuntimeException("操作2失败：转账到账出错！");
            }

            // 操作 2：转入账户加钱
            to.balance += amount;
            System.out.println("  操作2 完成：" + to.name + " 加 " + amount + "，余额 " + to.balance);

            System.out.println("--- 事务提交：两步都成功 ---");
        } catch (Exception e) {
            // 关键：回滚，把操作 1 的改动撤销掉
            from.balance = fromBackup;
            to.balance = toBackup;
            System.out.println("  ✗ 发生异常：" + e.getMessage());
            System.out.println("--- 事务回滚：撤销操作1，恢复到 " + from.name + "=" + from.balance + ", " + to.name + "=" + to.balance + " ---");
        }
    }

    /** 简单的账户类 */
    static class Account {
        String name;
        int balance;

        Account(String name, int balance) {
            this.name = name;
            this.balance = balance;
        }
    }
}
