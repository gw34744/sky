-- ============================================================
-- 苍穹外卖 分库分表 SQL 设计脚本
-- 作用：提供订单库垂直拆分 + 订单/明细水平分表的建表参考
--
-- 说明：
-- 1. 本脚本是「设计参考」，演示如何建分库、建分表；
-- 2. 分片键 user_id，分片算法 user_id % 16，共 16 张表；
-- 3. 主键 id 不再用 AUTO_INCREMENT，改用雪花算法生成的全局唯一 ID
--    （项目已有 SnowflakeIdUtil，分表后主键和订单号都由它生成）；
-- 4. order_detail 用 order_id（即订单主键）作分片键，保证订单与明细同分片，
--    从而单库内可 join、可用单库事务。
-- ============================================================

-- ------------------------------------------------------------
-- 一、垂直分库：建订单库（sky_order）
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `sky_order` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `sky_order`;

-- ------------------------------------------------------------
-- 二、水平分表：订单表 orders_0 ~ orders_15
--     分片键：user_id，算法 user_id % 16
-- ------------------------------------------------------------
-- 以 orders_0 为例，其余 orders_1 ~ orders_15 结构完全相同，
-- 仅表名后缀不同（循环建 16 张）

CREATE TABLE `orders_0` (
  `id` bigint NOT NULL COMMENT '主键（雪花算法全局唯一，不再自增）',
  `number` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订单号（雪花算法）',
  `status` int NOT NULL DEFAULT '1' COMMENT '订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款',
  `user_id` bigint NOT NULL COMMENT '下单用户（分片键）',
  `address_book_id` bigint NOT NULL COMMENT '地址id',
  `order_time` datetime NOT NULL COMMENT '下单时间',
  `checkout_time` datetime DEFAULT NULL COMMENT '结账时间',
  `pay_method` int NOT NULL DEFAULT '1' COMMENT '支付方式 1微信,2支付宝',
  `pay_status` tinyint NOT NULL DEFAULT '0' COMMENT '支付状态 0未支付 1已支付 2退款',
  `transaction_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '微信支付交易号（幂等用）',
  `amount` decimal(10,2) NOT NULL COMMENT '实收金额',
  `remark` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `phone` varchar(11) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号',
  `address` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '地址',
  `user_name` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名称',
  `consignee` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '收货人',
  `cancel_reason` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订单取消原因',
  `rejection_reason` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订单拒绝原因',
  `cancel_time` datetime DEFAULT NULL COMMENT '订单取消时间',
  `estimated_delivery_time` datetime DEFAULT NULL COMMENT '预计送达时间',
  `delivery_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '配送状态 1立即送出 0选择具体时间',
  `delivery_time` datetime DEFAULT NULL COMMENT '送达时间',
  `pack_amount` int DEFAULT NULL COMMENT '打包费',
  `tableware_number` int DEFAULT NULL COMMENT '餐具数量',
  `tableware_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '餐具数量状态 1按餐量提供 0选择具体数量',
  PRIMARY KEY (`id`),
  KEY `idx_orders_user_id` (`user_id`),
  KEY `idx_orders_status` (`status`),
  KEY `idx_orders_number` (`number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单表-分片0';

-- ------------------------------------------------------------
-- 三、水平分表：订单明细表 order_detail_0 ~ order_detail_15
--     分片键：order_id（订单主键），与 orders 同分片，保证同库 join + 事务
-- ------------------------------------------------------------
CREATE TABLE `order_detail_0` (
  `id` bigint NOT NULL COMMENT '主键（雪花算法全局唯一）',
  `name` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '名字',
  `image` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图片',
  `order_id` bigint NOT NULL COMMENT '订单id（分片键）',
  `dish_id` bigint DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '口味',
  `number` int NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  PRIMARY KEY (`id`),
  KEY `idx_order_detail_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单明细表-分片0';

-- ============================================================
-- 建表说明（为什么这样设计）
-- ============================================================
-- 1. 主键 id 去掉 AUTO_INCREMENT：分表后每张表都自增会导致主键全局冲突，
--    改用雪花算法（SnowflakeIdUtil）生成全局唯一、趋势递增的 ID。
--
-- 2. order_detail 用 order_id 作分片键：让「订单」和「它的明细」落在同一分片，
--    这样：
--      - 查订单详情时 join 不需要跨分片；
--      - 插入订单+明细可以用单库事务保证原子性。
--
-- 3. 每个分片表内保留索引（user_id/status/number/order_id）：
--    分表后单表变小，索引效率更高，但索引仍不可少（对应此前索引优化）。
--
-- 4. 新增 transaction_id 字段：支付回调幂等判断用（此前支付回调幂等改造已加）。

-- ============================================================
-- 四、生成 orders_1 ~ orders_15 的循环建表（可选，用存储过程）
-- ============================================================
-- 以下存储过程可一次性生成 orders_1 ~ orders_15、order_detail_1 ~ order_detail_15
-- （结构与 _0 完全相同，仅表名不同）

DELIMITER $$

DROP PROCEDURE IF EXISTS create_shard_tables$$

CREATE PROCEDURE create_shard_tables()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 15 DO
        SET @sql_orders = CONCAT(
            'CREATE TABLE IF NOT EXISTS `orders_', i, '` LIKE `orders_0`;');
        PREPARE stmt FROM @sql_orders;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @sql_detail = CONCAT(
            'CREATE TABLE IF NOT EXISTS `order_detail_', i, '` LIKE `order_detail_0`;');
        PREPARE stmt2 FROM @sql_detail;
        EXECUTE stmt2;
        DEALLOCATE PREPARE stmt2;

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

-- 执行存储过程生成其余 15 张表
CALL create_shard_tables();

-- ============================================================
-- 五、分片路由公式（应用层 ShardingSphere-JDBC 配置）
-- ============================================================
-- 分片键：user_id（orders）/ order_id（order_detail）
-- 分片算法：table_name + "_" + (分片键 % 16)
-- 例如：
--   user_id = 1001 → 1001 % 16 = 9 → orders_9
--   user_id = 1002 → 1002 % 16 = 10 → orders_10
--
-- 示例查询（应用自动路由，无需手写分片逻辑）：
--   SELECT * FROM orders WHERE user_id = 1001;   -- 自动路由到 orders_9
--   SELECT * FROM orders WHERE id = 订单id;       -- 若 id 编码了 user_id 则可路由

-- ============================================================
-- 六、非分片键查询的处理（重点！）
-- ============================================================
-- 支付回调/来单提醒按「订单号 number」查订单，但分片键是 user_id，
-- number 查不到分片位置。三种方案：
--   1) 订单号编码 user_id（雪花 ID 高位塞入 user_id，查询时反解）—— 推荐；
--   2) 建 number → (user_id, 分片) 映射（Redis 缓存）；
--   3) 广播查询所有分片（简单但性能差，仅兜底）。
