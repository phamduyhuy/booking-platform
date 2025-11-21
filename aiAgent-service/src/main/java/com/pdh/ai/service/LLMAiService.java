package com.pdh.ai.service;

import com.pdh.ai.agent.BookingSmartAssistant;
import com.pdh.ai.model.dto.ChatConversationSummaryDto;
import com.pdh.ai.model.dto.ChatHistoryResponse;
import com.pdh.ai.model.dto.StructuredChatPayload;
import com.pdh.ai.model.entity.ChatMessage;
import com.pdh.ai.repository.ChatMessageRepository;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import com.pdh.ai.agent.workflow.BookingIntent;
import com.pdh.ai.agent.workflow.ConversationRouterAgent;
import com.pdh.ai.agent.workflow.SmallTalkAgent;
import com.pdh.ai.agent.workflow.TravelFulfillmentAgent;
import com.pdh.ai.model.dto.StructuredChatPayload;
import com.pdh.ai.service.JpaChatMemoryStore;
import com.pdh.ai.agent.BookingSmartAssistant;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.core.internal.Function;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class LLMAiService implements AiService {

   
    private final ChatMessageRepository chatMessageRepository;
    private final McpToolProvider mcpToolProvider;
    private final GoogleAiGeminiChatModel chatModel;
    private final JpaChatMemoryStore chatMemoryStore;
    public LLMAiService(
                        ChatMessageRepository chatMessageRepository,
                        McpToolProvider mcpToolProvider,
                        GoogleAiGeminiChatModel chatModel,
                        JpaChatMemoryStore chatMemoryStore
                        ) {
    
        this.chatMessageRepository = chatMessageRepository;
        this.mcpToolProvider = mcpToolProvider;
        this.chatModel = chatModel;
        this.chatMemoryStore = chatMemoryStore;
    }

    @Override
    public StructuredChatPayload processStructured(String message, String conversationId, String username, String userId) {
        String actualUserId = resolveAuthenticatedUserId(userId);
<<<<<<< HEAD
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
=======
        String conversationKey = formatConversationKey(actualUserId, conversationId);
        try {
            // var agenticScopeResult = assistant.chat(conversationKey, message);
            // System.out.println("Agentic Scope Trace: " + agenticScopeResult.agenticScope());
            return StructuredChatPayload.builder().build();
        } catch (Exception ex) {
            log.error("[AI] Error while processing structured request", ex);
            return buildErrorResponse();
        }
    }

    @Override
    public Flux<StructuredChatPayload> streamStructured(String message, String conversationId, String userId) {
        String actualUserId = resolveAuthenticatedUserId(userId);
        String conversationKey = formatConversationKey(actualUserId, conversationId);
      ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(15)
                .chatMemoryStore(chatMemoryStore)
                .build();

        ConversationRouterAgent conversationRouter = AgenticServices.agentBuilder(ConversationRouterAgent.class)
                .chatModel(chatModel)
                .outputKey("intent")
                .beforeAgentInvocation(req -> {
                    // Should include "request" -> "...user text..."
                    System.out.println("Router inputs: " + req.inputs());
                })
                .build();

        var fulfillmentBuilder = AgenticServices.agentBuilder(TravelFulfillmentAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .outputKey("structuredResponse");

   

        TravelFulfillmentAgent fulfillmentAgent = fulfillmentBuilder.build();

        SmallTalkAgent smallTalkAgent = AgenticServices.agentBuilder(SmallTalkAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .outputKey("structuredResponse")
                .build();

        UntypedAgent routedWorkflow = AgenticServices
                .conditionalBuilder()
                .subAgents(scope -> scope.readState("intent", BookingIntent.UNKNOWN) == BookingIntent.SMALL_TALK,
                        smallTalkAgent)
                .subAgents(scope -> true, fulfillmentAgent)
                .outputKey("structuredResponse")
                .build();

        BookingSmartAssistant assistant=AgenticServices
                .sequenceBuilder(BookingSmartAssistant.class)
                .subAgents(conversationRouter, routedWorkflow)
                // .errorHandler(ctx -> {
                //     var ex = ctx.exception();
                //     if (ex instanceof MissingArgumentException m && "request".equals(m.argumentName())) {
                //         // Try to bridge from a legacy key or fail gracefully
                //         String legacy = ctx.agenticScope().readState("userMessage", (String) null);
                //         if (legacy != null && !legacy.isBlank()) {
                //             ctx.agenticScope().writeState("request", legacy);
                //             return ErrorRecoveryResult.retry();
                //         }
                //         // You can also inject a safe default to keep the flow moving
                //         ctx.agenticScope().writeState("request", "");
                //         return ErrorRecoveryResult.retry();
                //     }
                //     return ErrorRecoveryResult.throwException();
                // })
                .outputKey("structuredResponse")
                .output(scope -> {
                    StructuredChatPayload payload = (StructuredChatPayload) scope.readState("structuredResponse");
                    return payload != null ? payload
                            : StructuredChatPayload.builder()
                                    .message("Xin lỗi, tôi không thể xử lý yêu cầu của bạn ngay bây giờ.")
                                    .results(Collections.emptyList())
                                    .nextRequestSuggestions(new String[0])
                                    .requiresConfirmation(false)
                                    .build();
                })
                .build();
        return Mono.fromCallable(() -> assistant.chat(conversationKey, message))
                .subscribeOn(Schedulers.boundedElastic())
                .map(agenticScope->ensureValidPayload(agenticScope.result()))
                .flatMapMany(Flux::just)
                .onErrorResume(ex -> {
                    log.error("[AI] Error while streaming structured request", ex);
                    return Flux.just(buildErrorResponse());
                });
>>>>>>> origin/dev
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
<<<<<<< HEAD
    public void clearChatHistory(String conversationId, String username, String userId) {
        // For clearing history, we need username for conversationKey format
        String actualUsername = resolveAuthenticatedUsername(username);
        String conversationKey = formatConversationKey(actualUsername, conversationId);

        // Simply delete all messages with this conversation key
=======
    public void clearChatHistory(String conversationId, String userId) {
        String actualUserId = resolveAuthenticatedUserId(userId);
        String conversationKey = formatConversationKey(actualUserId, conversationId);
>>>>>>> origin/dev
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

<<<<<<< HEAD
    /**
     * Validates and resolves the authenticated user ID (UUID from JWT sub claim).
     * This is used for MCP tool authorization.
     */
=======
>>>>>>> origin/dev
    private String resolveAuthenticatedUserId(String requestUserId) {
        if (requestUserId != null && !requestUserId.isBlank()) {
            return requestUserId;
        }
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

    private String normalizeTitle(String title) {
        if (title != null) {
            String trimmed = title.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return defaultTitle();
    }

<<<<<<< HEAD
    /**
     * Format conversation key as {username}:{conversationId}
     * Note: Uses username (from JWT preferred_username), NOT userId (UUID from sub)
     */
    private String formatConversationKey(String username, String conversationId) {
        return String.format("%s:%s", username, conversationId);
=======
    private String formatConversationKey(String userId, String conversationId) {
        return String.format("%s:%s", userId, conversationId);
>>>>>>> origin/dev
    }

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

        if (payload.getNextRequestSuggestions() == null) {
            payload.setNextRequestSuggestions(new String[0]);
        }

        return payload;
    }

    private StructuredChatPayload buildErrorResponse() {
        return StructuredChatPayload.builder()
                .message("Xin lỗi, đã xảy ra lỗi khi xử lý yêu cầu của bạn. Vui lòng thử lại.")
                .nextRequestSuggestions(new String[0])
                .results(Collections.emptyList())
                .requiresConfirmation(false)
                .build();
    }
}
