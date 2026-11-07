package com.sky.config;

import com.sky.context.TraceIdUtil;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.slf4j.MDC;

/**
 * 异步任务装饰器：跨线程传递 MDC 上下文（traceId）。
 *
 * <p>MDC 基于 ThreadLocal，{@code @Async} / 自定义线程池执行的子线程拿不到主线程的 traceId，
 * 导致异步日志缺失追踪 ID。Spring 的 {@link TaskDecorator} 在「任务提交给线程池执行前」对任务
 * 做一层包装，是官方推荐的跨线程上下文传递方案——<b>一处配置，全局生效</b>，业务代码无需改动。</p>
 *
 * <p>工作方式：主线程把 Runnable 提交给线程池时，先取出主线程的 MDC 快照；子线程真正执行前
 * 恢复该快照；执行完清理，避免线程复用导致上下文残留。</p>
 */
public class MdcTaskDecorator implements TaskDecorator {

    @NonNull
    @Override
    public Runnable decorate(@NonNull Runnable runnable) {
        // 提交任务的主线程里的 traceId（若无则说明不在请求链路里，保持为空即可）
        String traceId = TraceIdUtil.getTraceId();

        return () -> {
            try {
                // 子线程执行前恢复 traceId
                if (traceId != null) {
                    MDC.put(TraceIdUtil.TRACE_ID, traceId);
                }
                runnable.run();
            } finally {
                // 执行完清理，防止线程池复用导致串号
                MDC.remove(TraceIdUtil.TRACE_ID);
            }
        };
    }
}
