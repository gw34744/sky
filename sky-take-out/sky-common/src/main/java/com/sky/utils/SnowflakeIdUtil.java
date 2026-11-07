package com.sky.utils;

/**
 * 雪花算法（Snowflake）分布式唯一ID生成器
 *
 * <p>用于生成全局唯一、趋势递增的长整型ID，解决高并发下
 * {@code System.currentTimeMillis()} 作订单号时的撞号问题。
 *
 * <p>64位结构（标准雪花算法）：
 * <pre>
 *   1 位符号位（恒为0，表示正数）
 *  41 位时间戳（毫秒级，可用约69年）
 *  10 位机器标识（5位数据中心 + 5位机器ID，最多支持1024个节点）
 *  12 位序列号（同一毫秒内最多生成4096个ID）
 * </pre>
 *
 * <p>特性：整体趋势递增（便于数据库索引）、分布式唯一、无第三方依赖。
 */
public class SnowflakeIdUtil {

    /** 起始时间戳（2020-01-01 00:00:00），减小时间戳位数，延长可用年限 */
    private static final long START_TIMESTAMP = 1577808000000L;

    /** 机器ID占用的位数 */
    private static final long WORKER_ID_BITS = 5L;

    /** 数据中心ID占用的位数 */
    private static final long DATA_CENTER_ID_BITS = 5L;

    /** 序列号占用的位数 */
    private static final long SEQUENCE_BITS = 12L;

    /** 机器ID最大值（31） */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /** 数据中心ID最大值（31） */
    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);

    /** 序列号最大值（4095） */
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    /** 机器ID左移位数 = 序列号位数 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 数据中心ID左移位数 = 序列号位数 + 机器ID位数 */
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 时间戳左移位数 = 序列号位数 + 机器ID位数 + 数据中心ID位数 */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    /** 机器ID（默认0，单机部署足够） */
    private static final long WORKER_ID = 0L;

    /** 数据中心ID（默认0） */
    private static final long DATA_CENTER_ID = 0L;

    /** 当前序列号 */
    private static long sequence = 0L;

    /** 上次生成ID的时间戳 */
    private static long lastTimestamp = -1L;

    /**
     * 生成下一个唯一ID
     *
     * @return 雪花算法生成的唯一ID
     */
    public static synchronized long nextId() {
        long timestamp = currentTimeMillis();

        // 时钟回拨检测：若当前时间小于上次时间，说明系统时钟被回拨，抛异常避免ID重复
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(
                    "时钟回拨，拒绝生成ID，回拨毫秒数：" + (lastTimestamp - timestamp)
            );
        }

        // 同一毫秒内，序列号自增
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 同一毫秒内序列号用完，自旋等待到下一毫秒
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号归零
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 拼接：时间戳 + 数据中心ID + 机器ID + 序列号
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (DATA_CENTER_ID << DATA_CENTER_ID_SHIFT)
                | (WORKER_ID << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 生成订单号（字符串形式，供订单表使用）
     *
     * @return 雪花算法ID的字符串
     */
    public static String nextIdStr() {
        return String.valueOf(nextId());
    }

    /**
     * 自旋等待到下一毫秒
     */
    private static long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 获取当前毫秒时间戳
     */
    private static long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
