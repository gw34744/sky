package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfiguration {

    /**
     * 自定义 RedisTemplate，统一序列化器。
     *
     * <p>黑马原版只配置了 key 的 String 序列化器，value 使用 JDK 默认序列化，
     * 导致的问题：
     * <ul>
     *     <li>value 以 JDK 二进制流存储（\xAC\xED...），redis-cli 肉眼不可读、无法排查；</li>
     *     <li>实体类必须实现 {@code Serializable}，否则存值抛异常；</li>
     *     <li>跨语言不可用（其他语言/服务无法读取）。</li>
     * </ul>
     *
     * <p>这里 key 用 String、value 用 JSON（GenericJackson2JsonRedisSerializer），
     * 与 {@link CacheConfiguration} 的 Spring Cache 序列化策略保持一致，
     * 使 value 可读、跨语言、且无需实现 Serializable。
     *
     * @param redisConnectionFactory 连接工厂
     * @return 配置好的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("开始创建 RedisTemplate，key=String、value=JSON 序列化...");

        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        // 设置 redis 的连接工厂对象
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // key 使用 String 序列化，避免出现乱码前缀
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(keySerializer);
        redisTemplate.setHashKeySerializer(keySerializer);

        // value 使用 JSON 序列化（带类型信息，反序列化时可还原为原类型），
        // 解决默认 JDK 序列化的「不可读 + 需 Serializable」问题
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashValueSerializer(valueSerializer);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
