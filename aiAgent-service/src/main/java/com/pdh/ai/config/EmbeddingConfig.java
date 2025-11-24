package com.pdh.ai.config;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.knuddels.jtokkit.api.EncodingType;

@Configuration
public class EmbeddingConfig {
    
    @Bean
    public BatchingStrategy customTokenCountBatchingStrategy() {
        return new TokenCountBatchingStrategy(
            EncodingType.CL100K_BASE,
            8192,  // Maximum tokens for nomic-embed-text:v1.5
            0.1    // 10% reserve
        );
    }
    
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(
            1500,   // defaultChunkSize: tokens per chunk
            200,    // minChunkSizeChars: minimum characters in a chunk
            10,     // minChunkLengthToEmbed: minimum chunk size to embed
            7000,   // maxNumChunks: maximum tokens per chunk (stay under 7373 effective limit)
            true    // keepSeparator: preserve separators in chunks
        );
    }
}