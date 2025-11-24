package com.pdh.ai.config;

import com.pdh.ai.websocket.ChatWebSocketHandler;
import com.pdh.ai.websocket.JwtAuthHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final JwtAuthHandshakeInterceptor jwtAuthHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(jwtAuthHandshakeInterceptor)
                .setAllowedOrigins("*"); // TODO: tighten for production domains
    }

    /**
     * Configure WebSocket container settings for long-running AI processing.
     * These settings prevent timeout during lengthy AI operations (up to 10 minutes).
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        
        // Session timeout: 10 minutes (in milliseconds)
        container.setMaxSessionIdleTimeout(600000L);
        
        // Max text message size: 1MB
        container.setMaxTextMessageBufferSize(1024 * 1024);
        
        // Max binary message size: 1MB
        container.setMaxBinaryMessageBufferSize(1024 * 1024);
        
        // Async send timeout: 5 seconds
        container.setAsyncSendTimeout(5000L);
        
        return container;
    }
}
