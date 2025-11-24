package com.pdh.ai.service;

import com.pdh.ai.model.dto.ChatConversationSummaryDto;
import com.pdh.ai.model.dto.ChatHistoryResponse;
import com.pdh.ai.model.dto.StructuredChatPayload;

/**
 * AI Service interface for chat operations.
 * 
 * Key concepts:
 * - username: From JWT preferred_username claim, used for conversationKey format (username:conversationId)
 * - userId: From JWT sub claim (UUID), used for MCP tool authorization
 * - conversationId: UUID from client, combined with username to form conversationKey
 */
public interface AiService {
    // Synchronous methods for regular chat and history operations
    ChatHistoryResponse getChatHistory(String conversationId, String username, String userId);
    void clearChatHistory(String conversationId, String username, String userId);
    java.util.List<ChatConversationSummaryDto> getUserConversations(String username, String userId);

    // Synchronous structured method (without streaming)
    StructuredChatPayload processStructured(String message, String conversationId, String username, String userId);

    reactor.core.publisher.Flux<StructuredChatPayload> streamStructured(String message, String conversationId, String username, String userId);
}
