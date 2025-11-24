package com.pdh.ai.config;

import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    @Bean
    @Qualifier("cacheObjectMapper")
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Enable Java 8 time module
        objectMapper.registerModule(new JavaTimeModule());
        
        // Configure serialization
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Note: Removed global polymorphic type handling to avoid conflicts
        // with Map<String, Object> deserialization in StructuredChatPayload
        // The concrete StructuredChatPayload class handles its own type information
        
        return objectMapper;
    }
    
}
