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
    public StructuredChatPayload processStructured(String message, String conversationId, String userId) {
        String actualUserId = resolveAuthenticatedUserId(userId);
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
    }

    @Override
    public ChatHistoryResponse getChatHistory(String conversationId, String userId) {
        String actualUserId = resolveAuthenticatedUserId(userId);
        String conversationKey = formatConversationKey(actualUserId, conversationId);

        List<ChatMessage> storedMessages = chatMessageRepository
                .findByConversationIdOrderByTimestampAsc(conversationKey);

        List<ChatHistoryResponse.ChatMessage> chatMessages = storedMessages.stream()
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
    public void clearChatHistory(String conversationId, String userId) {
        String actualUserId = resolveAuthenticatedUserId(userId);
        String conversationKey = formatConversationKey(actualUserId, conversationId);
        chatMessageRepository.deleteByConversationId(conversationKey);
    }

    @Override
    public List<ChatConversationSummaryDto> getUserConversations(String userId) {
        String actualUserId = resolveAuthenticatedUserId(userId);
        String userIdPrefix = actualUserId + ":";

        List<ChatMessageRepository.ConversationInfo> conversations = chatMessageRepository
                .findUserConversations(userIdPrefix);

        return conversations.stream().map(conv -> {
            String conversationKey = conv.getConversationId();
            String unprefixedId = conversationKey;
            if (conversationKey != null && conversationKey.startsWith(userIdPrefix)) {
                unprefixedId = conversationKey.substring(userIdPrefix.length());
            }

            return ChatConversationSummaryDto.builder()
                    .id(unprefixedId)
                    .title(normalizeTitle(conv.getTitle()))
                    .createdAt(conv.getCreatedAt())
                    .lastUpdated(conv.getLastUpdated())
                    .build();
        }).toList();
    }

    private String resolveAuthenticatedUserId(String requestUserId) {
        if (requestUserId != null && !requestUserId.isBlank()) {
            return requestUserId;
        }
        throw new IllegalStateException("User must be authenticated to perform this operation. Please log in.");
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

    private String formatConversationKey(String userId, String conversationId) {
        return String.format("%s:%s", userId, conversationId);
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
