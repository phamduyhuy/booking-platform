package com.pdh.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdh.ai.model.dto.StructuredChatPayload;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String EXPLORE_CACHE = "explore";

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Qualifier("cacheObjectMapper") ObjectMapper cacheObjectMapper) {
        
        // Configure Redis cache with 30 days TTL
        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofDays(30))  // TTL 30 days
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new Jackson2JsonRedisSerializer<>(cacheObjectMapper,StructuredChatPayload.class)
                )
            )
            .disableCachingNullValues();  // Don't cache null values
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(cacheConfiguration)
            .transactionAware()
            .build();
    }
}