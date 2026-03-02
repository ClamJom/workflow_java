package com.example.demoworkflow.config;

import com.alibaba.fastjson2.support.spring.data.redis.FastJsonRedisSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory){
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        // 这里用FastJSON的序列化方法存储泛型，这个Template主要用于Workflow的变量池，需要确保变量可序列化
        template.setHashValueSerializer(new FastJsonRedisSerializer<>(Object.class));
        template.setValueSerializer(new FastJsonRedisSerializer<>(Object.class));

        template.afterPropertiesSet();
        return template;
    }
}
