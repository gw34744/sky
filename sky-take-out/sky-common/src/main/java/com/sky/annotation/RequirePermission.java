package com.sky.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解：标注在 Controller 方法上，声明该方法需要的权限编码。
 *
 * <p>配合 {@code JwtTokenAdminInterceptor} 使用：拦截器解析到该方法带本注解时，
 * 校验当前登录员工是否拥有注解声明的权限编码（多值满足其一即可）。</p>
 *
 * <p>用法示例：</p>
 * <pre>
 * &#64;PostMapping
 * &#64;RequirePermission("dish:add")
 * public Result save(...) { ... }
 *
 * &#64;RequirePermission({"order:confirm", "order:delivery"})  // 满足任一即可
 * public Result confirm(...) { ... }
 * </pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 需要的权限编码（与 permission.perm_code 对应），多个值时满足任一即可。
     */
    String[] value();
}
