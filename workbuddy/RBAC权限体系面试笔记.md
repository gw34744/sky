# RBAC 权限体系 面试笔记

> 对应简历技术点：**RBAC 权限模型、细粒度权限控制、认证与授权分离**。
> 本文档配合代码落地：`@RequirePermission` 注解、`PermissionMapper`、`PermissionService`、
> `JwtTokenAdminInterceptor`（叠加权限校验）、`RBAC权限体系SQL设计.sql`。

---

## 一、问题背景：项目原来怎么做的，有什么缺陷

黑马教学默认的权限方案：**两个角色（admin 管理员 / user 用户），靠两套独立拦截器 + 两把 JWT 密钥硬隔离**。

```java
// 原来：管理员接口统一走 JwtTokenAdminInterceptor，只验证"是不是管理员"，不区分"是哪个管理员"
registry.addInterceptor(jwtTokenAdminInterceptor).addPathPatterns("/admin/**")...
registry.addInterceptor(jwtTokenUserInterceptor).addPathPatterns("/user/**")...
```

**缺陷：**

1. **粒度太粗**：只要登录成功，所有管理员能调所有 admin 接口，无法区分「店长」和「运营」的职责；
2. **无角色概念**：新增一个「财务」角色只能改代码，不能动态配置；
3. **权限写死在代码里**：权限判断散落在各处 if，无法统一管理、无法可视化配置；
4. **不符合真实企业需求**：真实系统一定有「员工 A 能看报表但不能改菜品」这种细粒度控制。

---

## 二、RBAC 是什么（核心概念）

**RBAC = Role-Based Access Control（基于角色的访问控制）**，核心思想：**不直接给用户授权，而是给「角色」授权，再把「角色」分配给「用户」**。

```
用户(User) --多对多--> 角色(Role) --多对多--> 权限(Permission)
 员工张三  ──拥有──▶  店长     ──拥有──▶   dish:add / dish:delete / order:confirm
 员工李四  ──拥有──▶  运营     ──拥有──▶   order:confirm / order:delivery
```

**为什么用角色中间层？** 如果直接「用户→权限」，每加一个用户都要重复配权限；有了角色，只需「把用户塞进某个角色」即可，权限变更只需改角色一处。

**RBAC 演进（面试可展开）：**

| 级别 | 内容 |
|------|------|
| RBAC0 | 用户-角色-权限 基础模型（本项目落地这层） |
| RBAC1 | 角色继承（角色分层，子角色继承父角色权限） |
| RBAC2 | 约束（职责分离：互斥角色、基数约束、先决条件） |
| RBAC3 | RBAC1 + RBAC2 组合 |

---

## 三、本项目的落地实现

### 1. 数据模型（4 张表）

| 表 | 作用 | 关键字段 |
|----|------|----------|
| `role` | 角色 | role_code（唯一编码，如 ADMIN/MANAGER） |
| `permission` | 权限 | perm_code（唯一编码，如 dish:add），perm_type（MENU/BUTTON/API），parent_id（树形） |
| `employee_role` | 员工-角色关联 | employee_id + role_id（联合唯一） |
| `role_permission` | 角色-权限关联 | role_id + permission_id（联合唯一） |

**权限编码规范**：`模块:操作`（如 `dish:add`、`order:confirm`），与注解值一一对应，见名知义。

### 2. 自定义注解 @RequirePermission

```java
@PostMapping
@RequirePermission("dish:add")          // 需要 dish:add 权限
public Result save(@RequestBody DishDTO dishDTO) { ... }

@RequirePermission({"order:confirm", "order:delivery"})  // 满足任一即可
public Result confirm(...) { ... }
```

### 3. 拦截器叠加授权（认证 + 授权分离）

`JwtTokenAdminInterceptor` 两步：

```java
// 第一步：认证 —— 解析 JWT 拿 empId
Claims claims = JwtUtil.parseJWT(secretKey, token);
Long empId = ...;
BaseContext.setCurrentId(empId);

// 第二步：授权 —— 仅当方法带 @RequirePermission 时才校验权限
RequirePermission rp = handlerMethod.getMethodAnnotation(RequirePermission.class);
if (rp != null) {
    Set<String> codes = permissionService.listPermCodes(empId);
    boolean has = Arrays.stream(rp.value()).anyMatch(codes::contains);
    if (!has) { response.setStatus(403); return false; }  // 无权限 403
}
```

**关键设计**：
- **认证失败返回 401**（未登录），**授权失败返回 403**（已登录但无权限）——语义区分；
- **只对标注了注解的方法做权限校验**，未标注的方法保持「仅登录校验」，向后兼容存量接口，可渐进式给接口加权限。

### 4. 权限查询 + 缓存（性能优化）

权限查询是「读多写少 + 每请求必查」，直接查库会拖慢鉴权。用 **Spring Cache（Redis）** 缓存：

```java
@Cacheable(cacheNames = "permCodes", key = "#employeeId")
public Set<String> listPermCodes(Long employeeId) { ... }
```

一次 JOIN 完成「员工→角色→权限」三级关联（SQL 见 `RBAC权限体系SQL设计.sql`）。

**缓存一致性**：角色/权限变更时需 `@CacheEvict(cacheNames = "permCodes", key = "#employeeId")` 主动失效；或依赖 TTL 兜底（本项目 CacheManager 已配 30~40 分钟随机 TTL）。

---

## 四、面试高频问答

**Q1：什么是 RBAC？为什么需要角色这一层？**
RBAC 是基于角色的访问控制，用户通过角色间接获得权限。角色层的作用是「权限配置的复用」：直接用户-权限会导致每加一个用户都要重复配权限；引入角色后只需把用户分配到一个角色，权限变更改角色一处即可。

**Q2：认证（Authentication）和授权（Authorization）的区别？**
认证是「你是谁」（验证身份，如登录），授权是「你能干什么」（验证权限）。本项目 JWT 解析是认证，@RequirePermission 校验是授权，两步分离、职责清晰。

**Q3：权限校验为什么用自定义注解 + 拦截器，而不是在 Service 里写 if？**
注解声明式配置，权限规则集中、可读性强、与业务逻辑解耦；拦截器统一在请求入口校验，避免每个方法里重复写权限判断，也避免漏写。

**Q4：401 和 403 的区别？**
401 Unauthorized = 未认证（没登录/凭证无效）；403 Forbidden = 已认证但无权限（登录了但没资格）。面试常考，语义要分清。

**Q5：权限查询每次请求都查库吗？性能怎么优化？**
不能每次都查库。用 Redis 缓存员工权限集合（key=员工ID），读多写少场景命中率高；角色/权限变更时主动 `@CacheEvict` 失效 + TTL 兜底。

**Q6：如何实现「菜单级 + 按钮级 + 接口级」三级权限？**
权限表用 `perm_type`（MENU/BUTTON/API）+ `parent_id`（树形）区分：
- 菜单级：控制前端路由/菜单显示；
- 按钮级：控制页面内「新增/删除」按钮的显示与可点；
- 接口级：后端真正的安全兜底（前端隐藏按钮可被绕过，后端必须校验）。

**Q7：超级管理员怎么处理？**
两种方式：① 特殊角色拥有全部权限（本项目：ADMIN 角色关联所有 permission）；② 代码里对超级管理员短路放行。推荐①，权限体系统一走查表逻辑。

---

## 五、落地文件清单

| 文件 | 作用 |
|------|------|
| `workbuddy/RBAC权限体系SQL设计.sql` | 4 张表建表 + 种子数据 + 核心查询 SQL |
| `sky-common/.../annotation/RequirePermission.java` | 权限校验注解 |
| `sky-pojo/.../entity/Role.java` | 角色实体 |
| `sky-pojo/.../entity/Permission.java` | 权限实体 |
| `sky-server/.../mapper/PermissionMapper.java` | 权限编码查询（一次 JOIN） |
| `sky-server/.../service/PermissionService.java` | 权限查询服务（带 Redis 缓存） |
| `sky-server/.../interceptor/JwtTokenAdminInterceptor.java` | 叠加权限校验（认证+授权） |
| `admin/DishController.java` | 新增/删除菜品加 @RequirePermission 示范 |
| `admin/OrderController.java` | 接单/派送加 @RequirePermission 示范 |

**使用说明**：建表 + 导入种子数据后，给现有员工（employee.id=1）绑定 ADMIN 角色即拥有全部权限；后续新增角色/权限只需改表数据，无需改代码。

---

## 六、可扩展方向（面试加分）

- **RBAC1 角色继承**：店长继承运营的权限，角色树结构；
- **数据权限**：同样是「店长」，A 店长只能看 A 店数据（行级权限，超出 RBAC 范畴，需单独设计）；
- **权限变更实时生效**：改角色权限后通过 MQ/广播让各实例失效缓存，而非等 TTL。
