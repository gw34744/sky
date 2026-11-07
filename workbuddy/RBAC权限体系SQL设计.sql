-- =============================================================
-- 苍穹外卖 RBAC 权限体系 —— 数据库表设计
-- 模型：RBAC0（用户-角色-权限 多对多）
-- 关系：employee(用户) --N:M--> role(角色) --N:M--> permission(权限)
-- =============================================================

-- 1. 角色表
CREATE TABLE IF NOT EXISTS `role` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_name`   varchar(32)  NOT NULL COMMENT '角色名称（如：超级管理员、店长、运营、财务）',
    `role_code`   varchar(32)  NOT NULL COMMENT '角色编码（唯一，如：ADMIN、MANAGER、OPERATOR）',
    `description` varchar(255) DEFAULT NULL COMMENT '角色描述',
    `status`      int          NOT NULL DEFAULT '1' COMMENT '状态 0:禁用 1:启用',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

-- 2. 权限表（细粒度到「接口/操作」，而非页面）
CREATE TABLE IF NOT EXISTS `permission` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `perm_name`     varchar(32)  NOT NULL COMMENT '权限名称（如：新增菜品）',
    `perm_code`     varchar(64)  NOT NULL COMMENT '权限编码（唯一，如：dish:add），与 @RequirePermission 注解值对应',
    `perm_type`     varchar(16)  NOT NULL DEFAULT 'MENU' COMMENT '权限类型 MENU菜单/BUTTON按钮/API接口',
    `parent_id`     bigint       NOT NULL DEFAULT '0' COMMENT '父权限ID（树形结构，0为根）',
    `status`        int          NOT NULL DEFAULT '1' COMMENT '状态 0:禁用 1:启用',
    `create_time`   datetime     DEFAULT NULL COMMENT '创建时间',
    `update_time`   datetime     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限表';

-- 3. 用户-角色关联表（一个员工可拥有多个角色）
CREATE TABLE IF NOT EXISTS `employee_role` (
    `id`          bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `employee_id` bigint NOT NULL COMMENT '员工ID（关联 employee.id）',
    `role_id`     bigint NOT NULL COMMENT '角色ID（关联 role.id）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_emp_role` (`employee_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '员工-角色关联表';

-- 4. 角色-权限关联表（一个角色拥有多个权限）
CREATE TABLE IF NOT EXISTS `role_permission` (
    `id`            bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       bigint NOT NULL COMMENT '角色ID（关联 role.id）',
    `permission_id` bigint NOT NULL COMMENT '权限ID（关联 permission.id）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
    KEY `idx_perm_id` (`permission_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-权限关联表';

-- =============================================================
-- 初始化数据（种子数据）
-- =============================================================

-- 角色
INSERT INTO `role` (`id`, `role_name`, `role_code`, `description`, `status`) VALUES
(1, '超级管理员', 'ADMIN', '拥有全部权限，管理员工与角色', 1),
(2, '店长', 'MANAGER', '管理菜品、套餐、分类', 1),
(3, '运营', 'OPERATOR', '处理订单、查看报表', 1),
(4, '财务', 'FINANCE', '查看报表与营业数据', 1);

-- 权限（编码与 @RequirePermission 注解值一一对应）
INSERT INTO `permission` (`id`, `perm_name`, `perm_code`, `perm_type`, `parent_id`, `status`) VALUES
(1,  '菜品管理', 'dish',            'MENU',   0, 1),
(11, '新增菜品', 'dish:add',        'BUTTON', 1, 1),
(12, '修改菜品', 'dish:update',     'BUTTON', 1, 1),
(13, '删除菜品', 'dish:delete',     'BUTTON', 1, 1),
(2,  '套餐管理', 'setmeal',         'MENU',   0, 1),
(21, '新增套餐', 'setmeal:add',     'BUTTON', 2, 1),
(22, '删除套餐', 'setmeal:delete',  'BUTTON', 2, 1),
(3,  '分类管理', 'category',        'MENU',   0, 1),
(31, '新增分类', 'category:add',    'BUTTON', 3, 1),
(4,  '订单管理', 'order',           'MENU',   0, 1),
(41, '接单',     'order:confirm',   'BUTTON', 4, 1),
(42, '派送',     'order:delivery',  'BUTTON', 4, 1),
(5,  '报表统计', 'report',          'MENU',   0, 1),
(51, '营业额统计', 'report:turnover', 'API',  5, 1);

-- 超级管理员拥有全部权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `permission`;

-- 店长：菜品+套餐+分类
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
(2, 1), (2, 11), (2, 12), (2, 13),
(2, 2), (2, 21), (2, 22),
(2, 3), (2, 31);

-- 运营：订单管理
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
(3, 4), (3, 41), (3, 42), (3, 5), (3, 51);

-- 财务：报表
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
(4, 5), (4, 51);

-- 给现有 admin 员工（id=1）绑定超级管理员角色
INSERT INTO `employee_role` (`employee_id`, `role_id`) VALUES (1, 1)
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;

-- =============================================================
-- 权限查询核心 SQL（拦截器校验用，一次 JOIN 查出员工所有权限编码）
-- =============================================================
-- SELECT DISTINCT p.perm_code
-- FROM employee_role er
-- JOIN role_permission rp ON er.role_id = rp.role_id
-- JOIN permission p ON rp.permission_id = p.id
-- JOIN role r ON r.id = er.role_id AND r.status = 1
-- WHERE er.employee_id = #{employeeId} AND p.status = 1;
