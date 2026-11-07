package com.sky.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置 + 开启 @Async 支持。
 *
 * <p>目的：为项目提供统一的异步执行能力，并解决「异步线程丢失 traceId」的链路追踪问题。</p>
 *
 * <p>关键点：通过 {@link MdcTaskDecorator} 给线程池任务做 MDC 上下文复制，使 @Async 方法内部
 * 打印的日志自动携带主线程的 traceId，实现同步/异步日志链路统一串联。</p>
 *
 * <p>使用方式：在需要异步的方法上加 {@code @Async("asyncExecutor")} 即可（注意 @Async 方法
 * 不能是 private，且调用方与目标方法需在不同 Bean，否则不走代理不生效）。</p>
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Bean("asyncExecutor")
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：常驻线程，避免频繁创建销毁
        executor.setCorePoolSize(5);
        // 最大线程数：高峰时扩容上限
        executor.setMaxPoolSize(10);
        // 队列容量：核心线程忙时任务先入队，超过再扩容到最大线程数
        executor.setQueueCapacity(200);
        // 线程名前缀，便于日志排查
        executor.setThreadNamePrefix("sky-async-");
        // 拒绝策略：队列满且线程达上限时，由调用线程执行（避免丢任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 关键：跨线程传递 traceId（MDC 上下文复制），异步日志链路不中断
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
