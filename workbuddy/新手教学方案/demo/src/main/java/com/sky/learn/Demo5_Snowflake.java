package com.sky.learn;

/**
 * 【演示 5】雪花算法 —— 并发下生成不重复的 ID
 *
 * <p>对应教学方案第二章 2.5 节。这里用一个「简化版雪花算法」演示核心思想。</p>
 *
 * <p>运行方法：直接运行本类的 main 方法。</p>
 *
 * <p>观察要点：</p>
 * <ol>
 *   <li>用 {@code System.currentTimeMillis()} 生成"订单号"，同一毫秒内会撞号（重复）；</li>
 *   <li>用雪花算法（时间戳 + 序列号），即使同一毫秒内也能靠序列号区分，不重复；</li>
 *   <li>雪花算法生成的 ID 是趋势递增的，利于数据库索引。</li>
 * </ol>
 */
public class Demo5_Snowflake {

    public static void main(String[] args) {
        // ============ 对比一：用时间戳当订单号（会撞号） ============
        System.out.println("===== 对比一：System.currentTimeMillis() 当订单号 =====");
        // 快速连续生成 10 个"订单号"，观察是否重复
        java.util.Set<Long> timestampIds = new java.util.HashSet<>();
        int timestampRepeat = 0;
        for (int i = 0; i < 10; i++) {
            long id = System.currentTimeMillis();   // 同一毫秒内会得到相同的值！
            if (!timestampIds.add(id)) {
                timestampRepeat++;   // 统计撞号次数
            }
        }
        System.out.println("10 次生成中，撞号 " + timestampRepeat + " 次");
        System.out.println("→ 高并发下同一毫秒多个请求会拿到相同订单号，这就是原项目的 bug。");

        // ============ 对比二：简化版雪花算法（时间戳 + 序列号） ============
        System.out.println();
        System.out.println("===== 对比二：简化版雪花算法 =====");
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        java.util.Set<Long> snowflakeIds = new java.util.HashSet<>();
        int snowflakeRepeat = 0;
        for (int i = 0; i < 100000; i++) {
            long id = generator.nextId();
            if (!snowflakeIds.add(id)) {
                snowflakeRepeat++;   // 统计撞号次数
            }
        }
        System.out.println("10 万次生成中，撞号 " + snowflakeRepeat + " 次");
        System.out.println("→ 雪花算法靠「时间戳 + 序列号」保证唯一，即使同一毫秒也能区分。");

        // 打印前 5 个 ID，观察趋势递增
        System.out.println();
        System.out.println("前 5 个雪花 ID（观察趋势递增）：");
        for (int i = 0; i < 5; i++) {
            System.out.println("  " + generator.nextId());
        }
        System.out.println("→ 趋势递增，利于数据库 B+ 树索引（对比 UUID 的随机无序）。");
    }

    /**
     * 简化版雪花算法：只保留「时间戳 + 序列号」两段，去掉机器标识，
     * 便于新手理解核心思想（你项目里的 SnowflakeIdUtil 是完整版，多了机器标识段）。
     */
    static class SnowflakeIdGenerator {
        // 序列号占 12 位，最大值 4095
        private static final long MAX_SEQUENCE = 4095L;

        private long sequence = 0L;          // 当前毫秒内的序列号
        private long lastTimestamp = -1L;    // 上次生成的时间戳

        synchronized long nextId() {
            long timestamp = System.currentTimeMillis();

            // 同一毫秒内：序列号 +1
            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                // 序列号用完了（这一毫秒生成了 4096 个），自旋等到下一毫秒
                if (sequence == 0) {
                    timestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                // 不同毫秒：序列号归零
                sequence = 0L;
            }

            lastTimestamp = timestamp;

            // 拼接：时间戳左移 12 位（给序列号腾位置）+ 序列号
            // 这样 ID 整体趋势递增，且同一毫秒内靠序列号区分
            return (timestamp << 12) | sequence;
        }

        // 自旋等待到下一毫秒
        private long waitNextMillis(long lastTimestamp) {
            long timestamp = System.currentTimeMillis();
            while (timestamp <= lastTimestamp) {
                timestamp = System.currentTimeMillis();
            }
            return timestamp;
        }
    }
}
