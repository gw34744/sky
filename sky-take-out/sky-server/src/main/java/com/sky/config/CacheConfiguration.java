package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Spring Cache（注解式缓存）配置类
 *
 * <p>解决两个问题：
 * <ul>
 *     <li>默认 Spring Cache 使用 JDK 序列化、且缓存不设过期时间，会导致缓存永久驻留、内存只增不减；</li>
 *     <li>本类为 {@code setmealCache} 统一配置 JSON 序列化 + TTL（过期时间），
 *         过期时间既控制 Redis 内存容量，又作为「缓存与数据库一致性」的兜底手段。</li>
 * </ul>
 */
@Configuration
@Slf4j
public class CacheConfiguration extends CachingConfigurerSupport {

    /**
     * 缓存统一过期时间：基础 30 分钟 + 随机 0~10 分钟。
     * 随机值避免大量 key 在同一时刻同时过期（缓存雪崩）。
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(30 + (long) (Math.random() * 10));

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        log.info("创建 RedisCacheManager，缓存 TTL = {} 分钟", CACHE_TTL.toMinutes());

        // key 使用 String 序列化，value 使用 JSON 序列化（可读、跨语言、无需实现 Serializable）
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .entryTtl(CACHE_TTL);

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * 自定义 key 生成策略（兜底）：当注解未显式指定 key 时，
     * 使用「类名:方法名:参数值」作为 key，避免参数对象默认 toString 的歧义。
     */
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getSimpleName()).append(":")
              .append(method.getName());
            for (Object param : params) {
                sb.append(":").append(param == null ? "null" : param.toString());
            }
            return sb.toString();
        };
    }
}
