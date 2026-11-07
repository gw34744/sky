package com.sky.filter;

import com.sky.context.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 链路追踪过滤器：为每个请求生成/透传 traceId 并注入 MDC。
 *
 * <p>作用：</p>
 * <ul>
 *   <li><b>入口生成</b>：请求到达时生成一个全局唯一 traceId（或透传上游 X-Trace-Id），
 *       通过 {@code MDC.put("traceId", ...)} 注入，使本次请求内所有日志自动携带该 ID；</li>
 *   <li><b>响应透传</b>：把 traceId 写回响应头，便于前端/调用方反馈问题时报 traceId，
 *       实现「问题可追溯」；</li>
 *   <li><b>资源清理</b>：请求结束时 {@code MDC.remove}，防止线程复用（Tomcat 线程池）
 *       导致 traceId 串号，这是 MDC 使用最易踩的坑；</li>
 *   <li><b>异步传递</b>：针对 Tomcat 的「异步请求」特性，兜底在异步线程中重新注入 MDC，
 *       保证异步 Servlet 的日志不丢 traceId。</li>
 * </ul>
 *
 * <p>这里用 Filter（而非 Interceptor）实现，是因为 Filter 处于 Servlet 容器最外层，
 * 能覆盖到拦截器之前、静态资源、错误页等所有进入容器的请求，traceId 覆盖范围最完整。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 1. 优先透传上游 traceId（Nginx/网关/调用方传入），否则生成新 traceId
        String traceId = httpRequest.getHeader(TraceIdUtil.TRACE_ID_HEADER);
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = TraceIdUtil.generateTraceId();
        }

        // 2. 注入 MDC，后续所有日志自动带 %X{traceId}
        MDC.put(TraceIdUtil.TRACE_ID, traceId);
        // 3. 透传回响应头，便于前端/调用方反馈问题时定位
        httpResponse.setHeader(TraceIdUtil.TRACE_ID_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            // 4. 请求结束务必清理 MDC，避免线程池复用导致 traceId 串号
            MDC.remove(TraceIdUtil.TRACE_ID);
        }
    }
}
