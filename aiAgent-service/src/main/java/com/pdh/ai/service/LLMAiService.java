package com.pdh.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdh.ai.agent.CoreAgent;
import com.pdh.ai.model.dto.ChatConversationSummaryDto;
import com.pdh.ai.model.dto.ChatHistoryResponse;
import com.pdh.ai.model.dto.StructuredChatPayload;
import com.pdh.ai.model.entity.ChatMessage;
import com.pdh.ai.repository.ChatMessageRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class LLMAiService implements AiService {

    private final CoreAgent coreAgent;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    public LLMAiService(CoreAgent coreAgent,
            ChatMessageRepository chatMessageRepository,
            ObjectMapper objectMapper) {
        this.coreAgent = coreAgent;
        this.chatMessageRepository = chatMessageRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public StructuredChatPayload processStructured(String message, String conversationId, String username, String userId) {
        String actualUserId = resolveAuthenticatedUserId(userId);
        String actualUsername = resolveAuthenticatedUsername(username);
        String conversationKey = formatConversationKey(actualUsername, conversationId);
        Instant now = Instant.now();

        ChatMessage conversationRoot = chatMessageRepository
                .findFirstByConversationIdAndParentMessageIsNullOrderByTimestampAsc(conversationKey)
                .orElse(null);

        ChatMessage userMessage = ChatMessage.builder()
                .conversationId(conversationKey)
                .role(MessageType.USER)
                .content(message)
                .timestamp(now)
                .build();

        ChatMessage savedUserMessage;
        if (conversationRoot == null) {
            userMessage.setTitle(defaultTitle(message));
            savedUserMessage = chatMessageRepository.save(userMessage);
            conversationRoot = savedUserMessage;
        } else {
            userMessage.setParentMessage(conversationRoot);
            savedUserMessage = chatMessageRepository.save(userMessage);
        }

        // Pass userId (UUID) to CoreAgent for MCP tools
        StructuredChatPayload payload = coreAgent.processSyncStructured(message, conversationKey, actualUserId)
                .map(this::ensureValidPayload)
                .doOnError(error -> log.error("[CHAT-MESSAGE] Error processing structured response", error))
                .onErrorReturn(buildErrorResponse())
                .block();

        if (payload == null) {
            payload = buildErrorResponse();
        }

        persistAssistantMessage(conversationKey, conversationRoot, payload);
        return payload;
    }

    @Override
    public Flux<StructuredChatPayload> streamStructured(String message, String conversationId, String username, String userId) {
        return Flux.defer(() -> {
            StructuredChatPayload payload = processStructured(message, conversationId, username, userId);
            return payload != null ? Flux.just(payload) : Flux.empty();
        });
    }

    @Override
    public ChatHistoryResponse getChatHistory(String conversationId, String username, String userId) {
        // For chat history, we need username for conversationKey format
        String actualUsername = resolveAuthenticatedUsername(username);
        String conversationKey = formatConversationKey(actualUsername, conversationId);

        List<ChatMessage> storedMessages = chatMessageRepository
                .findByConversationIdOrderByTimestampAsc(conversationKey);

        // Filter to show only USER and ASSISTANT messages in UI
        // TOOL and SYSTEM messages are kept in DB for debugging but hidden from users
        List<ChatHistoryResponse.ChatMessage> chatMessages = storedMessages.stream()
                .filter(entity -> entity.getRole() == MessageType.USER || entity.getRole() == MessageType.ASSISTANT)
                .map(entity -> ChatHistoryResponse.ChatMessage.builder()
                        .content(entity.getContent())
                        .role(entity.getRole().name().toLowerCase())
                        .timestamp(LocalDateTime.ofInstant(entity.getTimestamp(), ZoneOffset.UTC))
                        .build())
                .toList();

        // For this implementation, we'll use the timestamp of the first message as
        // createdAt
        // and the last message as lastUpdated, or current time if no messages exist
        Instant createdAt = storedMessages.isEmpty() ? Instant.now()
                : storedMessages.get(0).getTimestamp();
        Instant lastUpdatedInstant = storedMessages.isEmpty()
                ? createdAt
                : storedMessages.get(storedMessages.size() - 1).getTimestamp();

        return ChatHistoryResponse.builder()
                .conversationId(conversationId)
                .messages(chatMessages)
                .createdAt(LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .lastUpdated(LocalDateTime.ofInstant(lastUpdatedInstant, ZoneOffset.UTC))
                .build();
    }

    @Override
    public void clearChatHistory(String conversationId, String username, String userId) {
        // For clearing history, we need username for conversationKey format
        String actualUsername = resolveAuthenticatedUsername(username);
        String conversationKey = formatConversationKey(actualUsername, conversationId);

        // Simply delete all messages with this conversation key
        chatMessageRepository.deleteByConversationId(conversationKey);
    }

    @Override
    public List<ChatConversationSummaryDto> getUserConversations(String username, String userId) {
        // For listing conversations, we need username prefix (not userId UUID)
        String actualUsername = resolveAuthenticatedUsername(username);
        String usernamePrefix = actualUsername + ":";

        List<ChatMessageRepository.ConversationInfo> conversations = chatMessageRepository
                .findUserConversations(usernamePrefix);

        return conversations.stream().map(conv -> {
            String conversationKey = conv.getConversationId();
            String unprefixedId = conversationKey;
            if (conversationKey != null && conversationKey.startsWith(usernamePrefix)) {
                unprefixedId = conversationKey.substring(usernamePrefix.length());
            }

            return ChatConversationSummaryDto.builder()
                    .id(unprefixedId)
                    .title(normalizeTitle(conv.getTitle()))
                    .createdAt(conv.getCreatedAt())
                    .lastUpdated(conv.getLastUpdated())
                    .build();
        }).toList();
    }

    /**
     * Validates and resolves the authenticated user ID (UUID from JWT sub claim).
     * This is used for MCP tool authorization.
     */
    private String resolveAuthenticatedUserId(String requestUserId) {
        // If a specific user ID is provided (from auth context), use it
        if (requestUserId != null && !requestUserId.isBlank()) {
            return requestUserId;
        }

        // For WebSocket scenarios, require user ID to be provided
        throw new IllegalStateException("User must be authenticated to perform this operation. Please log in.");
    }

    /**
     * Validates and resolves the authenticated username (from JWT preferred_username claim).
     * This is used for conversationKey formatting (username:conversationId).
     */
    private String resolveAuthenticatedUsername(String requestUsername) {
        // If a specific username is provided (from auth context), use it
        if (requestUsername != null && !requestUsername.isBlank()) {
            return requestUsername;
        }

        // For WebSocket scenarios, require username to be provided
        throw new IllegalStateException("Username must be provided for conversation tracking. Please log in.");
    }

    private String defaultTitle() {
        return "Conversation " + LocalDateTime.now(ZoneOffset.UTC);
    }

    private String defaultTitle(String message) {
        if (message == null || message.isBlank())
            return defaultTitle();
        String sanitized = message.replaceAll("\\\\s+", " ").trim(); // chú ý Java escaping
        int maxLength = 60;
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength) + "...";
    }

    private String normalizeTitle(String title) {
        if (title != null) {
            String trimmed = title.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return defaultTitle();
    }

    /**
     * Format conversation key as {username}:{conversationId}
     * Note: Uses username (from JWT preferred_username), NOT userId (UUID from sub)
     */
    private String formatConversationKey(String username, String conversationId) {
        return String.format("%s:%s", username, conversationId);
    }

    /**
     * Extract conversation ID from the full key
     */
    private String extractConversationIdFromKey(String conversationKey) {
        int separatorIndex = conversationKey.lastIndexOf(':');
        if (separatorIndex >= 0) {
            return conversationKey.substring(separatorIndex + 1);
        }
        return conversationKey; // If no separator, return the whole string
    }

    private void persistAssistantMessage(String conversationKey, ChatMessage parentMessage, StructuredChatPayload payload) {
        StructuredChatPayload safePayload = ensureValidPayload(payload);
        try {
            String content = objectMapper.writeValueAsString(safePayload);
            ChatMessage assistantMessage = ChatMessage.builder()
                    .conversationId(conversationKey)
                    .role(MessageType.ASSISTANT)
                    .content(content)
                    .timestamp(Instant.now())
                    .parentMessage(parentMessage)
                    .build();
            chatMessageRepository.save(assistantMessage);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize assistant response for persistence", e);
        }
    }


    /**
     * Ensures payload has valid fields.
     */
    private StructuredChatPayload ensureValidPayload(StructuredChatPayload payload) {
        if (payload == null) {
            return buildErrorResponse();
        }

        if (payload.getMessage() == null || payload.getMessage().isBlank()) {
            payload.setMessage("Tôi đã xử lý yêu cầu của bạn nhưng không thể tạo phản hồi phù hợp.");
        }

        if (payload.getResults() == null) {
            payload.setResults(Collections.emptyList());
        }

        return payload;
    }

    /**
     * Builds error response for reactive error handling.
     */
    private StructuredChatPayload buildErrorResponse() {
        return StructuredChatPayload.builder()
                .message("Xin lỗi, đã xảy ra lỗi khi xử lý yêu cầu của bạn. Vui lòng thử lại.")
                .results(Collections.emptyList())
                .build();
    }

}
