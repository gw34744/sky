package com.sky.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务线程池配置
 *
 * <p>默认情况下 {@code @EnableScheduling} 使用单线程调度器，多个定时任务
 * （支付超时处理、派送完成处理、WebSocket 心跳）会串行执行，一个任务阻塞会拖累其余任务。
 *
 * <p>此处配置独立线程池，让多个定时任务并行执行，提升高负载下的调度能力。
 */
@Configuration
public class SchedulingConfiguration {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // 核心线程数：至少覆盖当前定时任务数量，留有余量
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("sky-schedule-");
        // 关闭时等待任务执行完，避免丢任务
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
