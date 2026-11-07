package com.sky.context;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * 链路追踪 ID 工具类
 *
 * <p>为每个请求生成一个全局唯一的 traceId，配合 SLF4J 的 MDC（Mapped Diagnostic Context）
 * 实现「统一日志收集 + 链路追踪」：同一次请求（含其触发的异步 MQ 消费、定时任务）打出的
 * 所有日志都携带同一个 traceId，便于在海量日志中按 traceId 聚合检索、定位慢请求与异常链路。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>traceId 用 UUID 去掉横线 + 截断，保证唯一且足够短（32 位），不泄露敏感信息；</li>
 *   <li>请求入口统一由 {@code TraceIdFilter} 生成并写入 MDC；若上游（Nginx / 网关 / 调用方）
 *       已通过请求头传入 traceId，则优先透传上游，实现跨服务链路串联；</li>
 *   <li>跨线程场景（MQ 监听器、@Async、线程池）需用
 *       {@link org.slf4j.MDC#put(String, String)} 手动把 MDC 上下文传递给子线程。</li>
 * </ul>
 */
public class TraceIdUtil {

    /** MDC 中的 key 名，需与 logback-spring.xml 中 %X{traceId} 保持一致 */
    public static final String TRACE_ID = "traceId";

    /** 上游透传 traceId 的请求头名称（约定俗成，也可与网关对齐） */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private TraceIdUtil() {
    }

    /**
     * 生成一个新的 traceId。
     *
     * @return 32 位小写十六进制字符串
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 获取当前线程 MDC 中的 traceId（跨线程传递场景使用：主线程先取出，再传给子线程重新 put）。
     *
     * @return 当前线程的 traceId；若尚未注入则返回 null
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }
}
