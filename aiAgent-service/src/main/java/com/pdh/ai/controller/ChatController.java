package com.pdh.ai.controller;

import com.pdh.ai.model.dto.ChatConversationSummaryDto;
import com.pdh.ai.model.dto.ChatHistoryResponse;
import com.pdh.ai.model.dto.ChatMessageRequest;
import com.pdh.ai.model.dto.StructuredChatPayload;
import com.pdh.ai.service.AiService;
import com.pdh.ai.service.LLMAiService;
import com.pdh.common.utils.AuthenticationUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


/**
 * REST Controller for AI Chat using Spring MVC.
 * Provides both synchronous and streaming chat endpoints.
 * 
 * <p>This controller uses AgenticWorkflowService which automatically:
 * <ul>
 * <li>Routes queries to specialized handlers (flight, hotel, destination, etc.)</li>
 * <li>Applies parallelization for multi-item queries</li>
 * <li>Optimizes responses through evaluation cycles</li>
 * </ul>
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    
    private final LLMAiService llmAiService;
    public ChatController( LLMAiService llmAiService) {
        this.llmAiService = llmAiService;
    }

    @GetMapping("/history/{conversationId}")
    public ResponseEntity<ChatHistoryResponse> getChatHistory(@PathVariable String conversationId) {
        try {
            // Extract username from OAuth2 principal
            String username = AuthenticationUtils.extractUsername();
            ChatHistoryResponse history = llmAiService.getChatHistory(conversationId, username);
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(
                ChatHistoryResponse.builder()
                    .conversationId(conversationId)
                    .messages(java.util.List.of())
                    .createdAt(LocalDateTime.now())
                    .lastUpdated(LocalDateTime.now())
                    .build()
            );
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                ChatHistoryResponse.builder()
                    .conversationId(conversationId)
                    .messages(java.util.List.of())
                    .createdAt(LocalDateTime.now())
                    .lastUpdated(LocalDateTime.now())
                    .build()
            );
        }
    }

    @DeleteMapping("/history/{conversationId}")
    public ResponseEntity<Void> clearChatHistory(@PathVariable String conversationId) {
        try {
            // Extract username from OAuth2 principal
            String username = AuthenticationUtils.extractUsername();
            llmAiService.clearChatHistory(conversationId, username);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ChatConversationSummaryDto>> getUserConversations() {
        try {
            // Extract username from OAuth2 principal
            String username = AuthenticationUtils.extractUsername();
            java.util.List<ChatConversationSummaryDto> conversations = llmAiService.getUserConversations(username);
            return ResponseEntity.ok(conversations);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.List.<ChatConversationSummaryDto>of());
        }
    }
}
