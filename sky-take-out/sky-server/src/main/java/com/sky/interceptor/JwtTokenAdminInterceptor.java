package com.sky.interceptor;

import com.sky.annotation.RequirePermission;
import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.context.TraceIdUtil;
import com.sky.properties.JwtProperties;
import com.sky.service.PermissionService;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;

/**
 * 管理员 JWT 令牌校验拦截器（叠加 RBAC 权限校验）
 *
 * <p>两步校验：</p>
 * <ol>
 *   <li><b>认证</b>：解析 JWT 拿到 empId，放入 ThreadLocal（BaseContext）；</li>
 *   <li><b>授权</b>：若目标方法带 {@link RequirePermission} 注解，则校验当前员工是否拥有
 *       注解声明的权限编码，无权限返回 403。</li>
 * </ol>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>「认证」与「授权」分离：登录态校验在前，细粒度权限校验在后；</li>
 *   <li>权限校验只对标注了 @RequirePermission 的方法生效，未标注的方法只做登录态校验，
 *       保持对存量接口的向后兼容，可渐进式给接口加权限；</li>
 *   <li>权限查询走 {@link PermissionService}（带 Redis 缓存），避免每请求查库。</li>
 * </ul>
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private PermissionService permissionService;

    /**
     * 校验 jwt + 权限
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断当前拦截到的是 Controller 的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // ===== 第一步：认证（JWT 解析） =====
        String token = request.getHeader(jwtProperties.getAdminTokenName());
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            // 日志脱敏：不打印完整 token
            log.info("管理员 jwt 校验通过，empId={}", empId);
            // 将用户 id 存储到 ThreadLocal
            BaseContext.setCurrentId(empId);

            // ===== 第二步：授权（RBAC 权限校验） =====
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            RequirePermission requirePermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
            if (requirePermission != null) {
                Set<String> permCodes = permissionService.listPermCodes(empId);
                boolean hasPermission = false;
                for (String required : requirePermission.value()) {
                    if (permCodes.contains(required)) {
                        hasPermission = true;
                        break;
                    }
                }
                if (!hasPermission) {
                    log.warn("[权限拦截] empId={} 无权限访问，traceId={}", empId, TraceIdUtil.getTraceId());
                    response.setStatus(403);
                    return false;
                }
            }

            return true;
        } catch (Exception ex) {
            // 认证失败，响应 401
            response.setStatus(401);
            return false;
        }
    }
}
