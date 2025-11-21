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
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    private final LLMAiService llmAiService;
    public ChatController( LLMAiService llmAiService) {
        this.llmAiService = llmAiService;
    }



    /**
     * Synchronous chat endpoint - Returns a complete structured response.
     * 
     * <p>Example Request:</p>
     * <pre>
     * POST /chat/message?conversationId={conversationId}
     * {
     *   "message": "Compare flights from Hanoi to Da Nang, Phu Quoc, and Nha Trang"
     * }
     * </pre>
     * 
     * @param request Chat message request
     * @param conversationId The conversation ID for this chat
     * @return Complete structured response
     */
    
    // @PostMapping(path = "/message")
    // public ResponseEntity<StructuredChatPayload> sendMessage(@RequestBody ChatMessageRequest request) {
    //     try {
    //         // Extract username and userId from OAuth2 principal
    //         String username = AuthenticationUtils.extractUsername();
    //         String userId = AuthenticationUtils.extractUserId();  // UUID from JWT sub claim
    //         String conversationId = request.getConversationId(); // Extract from request body
    //         String effectiveConversationId = conversationId != null ? conversationId : generateConversationId();
    //         logger.info("💬 [CHAT-MESSAGE] Received message from user: {}, userId: {}, conversation: {}", 
    //                    username, userId, effectiveConversationId);

    //         // Validate request
    //         if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
    //             return ResponseEntity.badRequest().body(StructuredChatPayload.builder()
    //                 .message("Message cannot be empty")
    //                 .results(java.util.List.of())
    //                 .build());
    //         }

    //         // Process and return complete response
    //         StructuredChatPayload response = llmAiService.processStructured(
    //             request.getMessage(),
    //             effectiveConversationId,
    //             username,  // Username for conversationKey format
    //             userId     // UUID for MCP tools
    //         );
    //         return ResponseEntity.ok(response);

    //     } catch (Exception e) {
    //         logger.error("❌ [CHAT-MESSAGE] Error processing message: {}", e.getMessage(), e);
    //         return ResponseEntity.status(500).body(StructuredChatPayload.builder()
    //             .message("Xin lỗi, đã xảy ra lỗi khi xử lý yêu cầu của bạn.")
    //             .results(java.util.List.of())
    //             .build());
    //     }
    // }
    
    private String generateConversationId() {
        return java.util.UUID.randomUUID().toString();
    }
    
    /**
     * Health check endpoint.
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chat service is running with Agentic Workflow Orchestration (Sync Only)");
    }



    @GetMapping("/history/{conversationId}")
    public ResponseEntity<ChatHistoryResponse> getChatHistory(@PathVariable String conversationId) {
        try {
            // Extract username and userId from OAuth2 principal
            String username = AuthenticationUtils.extractUsername();
            String userId = AuthenticationUtils.extractUserId();
            ChatHistoryResponse history = llmAiService.getChatHistory(conversationId, username, userId);
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
            // Extract username and userId from OAuth2 principal
            String username = AuthenticationUtils.extractUsername();
            String userId = AuthenticationUtils.extractUserId();
            llmAiService.clearChatHistory(conversationId, username, userId);
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
            // Extract username and userId from OAuth2 principal
            String username = AuthenticationUtils.extractUsername();
            String userId = AuthenticationUtils.extractUserId();
            java.util.List<ChatConversationSummaryDto> conversations = llmAiService.getUserConversations(username, userId);
            return ResponseEntity.ok(conversations);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.List.<ChatConversationSummaryDto>of());
        }
    }
}
