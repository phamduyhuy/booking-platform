package com.pdh.ai.service;

import com.pdh.ai.model.ChatRole;
import com.pdh.ai.model.entity.ChatMessage;
import com.pdh.ai.repository.ChatMessageRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * Persisted ChatMemory store backed by the chat_message table.
 * <p>
 * Each LangChain4j ChatMessage is stored twice:
 * <ul>
 *     <li>as a JSON blob (json_content) for exact reconstruction by the agent</li>
 *     <li>as a human readable excerpt (content) for history/conversation APIs</li>
 * </ul>
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class JpaChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository chatMessageRepository;

    @PostConstruct
    void logStartup() {
        log.info("JpaChatMemoryStore initialised");
    }

    @Override
    @Transactional(readOnly = true)
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
        String conversationId = normalize(memoryId);
        return chatMessageRepository.findByConversationIdOrderByTimestampAsc(conversationId)
                .stream()
                .map(this::toChatMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<dev.langchain4j.data.message.ChatMessage> messages) {
        String conversationId = normalize(memoryId);
        chatMessageRepository.deleteByConversationId(conversationId);

        if (messages == null || messages.isEmpty()) {
            return;
        }

        List<ChatMessage> entities = new ArrayList<>();
        ChatMessage root = null;
        Instant baseTimestamp = Instant.now();
        int offset = 0;

        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            ChatRole role = mapRole(message.type());
            if (role == null) {
                continue;
            }

            String jsonPayload = serializeMessage(message);
            String humanReadable = extractHumanReadableContent(message);

            ChatMessage entity = ChatMessage.builder()
                    .conversationId(conversationId)
                    .role(role)
                    .content(humanReadable)
                    .jsonContent(jsonPayload)
                    .timestamp(baseTimestamp.plusMillis(offset++))
                    .build();

            if (root == null && role == ChatRole.USER) {
                entity.setTitle(defaultTitleFromText(humanReadable));
                root = entity;
            }

            entities.add(entity);
        }

        if (!entities.isEmpty()) {
            if (root == null) {
                root = entities.get(0);
                root.setTitle(defaultTitleFromText(root.getContent()));
            }

            for (ChatMessage entity : entities) {
                if (entity != root) {
                    entity.setParentMessage(root);
                }
            }

            chatMessageRepository.saveAll(entities);
        }
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        chatMessageRepository.deleteByConversationId(normalize(memoryId));
    }

    private dev.langchain4j.data.message.ChatMessage toChatMessage(ChatMessage entity) {
        if (entity == null) {
            return null;
        }
        String jsonContent = entity.getJsonContent();
        if (jsonContent != null && !jsonContent.isBlank()) {
            try {
                List<dev.langchain4j.data.message.ChatMessage> parsed = messagesFromJson(jsonContent);
                if (!parsed.isEmpty()) {
                    return parsed.get(0);
                }
            } catch (Exception ex) {
                log.warn("Failed to deserialize stored chat message JSON, falling back to legacy content", ex);
            }
        }

        return switch (entity.getRole()) {
            case SYSTEM -> SystemMessage.from(entity.getContent());
            case USER -> UserMessage.from(entity.getContent());
            case ASSISTANT -> AiMessage.from(entity.getContent());
            case TOOL -> readLegacyToolExecution(entity.getContent());
        };
    }

    private ChatRole mapRole(ChatMessageType type) {
        return switch (type) {
            case SYSTEM -> ChatRole.SYSTEM;
            case USER -> ChatRole.USER;
            case AI -> ChatRole.ASSISTANT;
            case TOOL_EXECUTION_RESULT -> ChatRole.TOOL;
            default -> null;
        };
    }

    private String serializeMessage(dev.langchain4j.data.message.ChatMessage message) {
        try {
            return messagesToJson(List.of(message));
        } catch (Exception ex) {
            log.warn("Failed to serialize chat message, storing fallback text only", ex);
            return null;
        }
    }

    private String extractHumanReadableContent(dev.langchain4j.data.message.ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            if (userMessage.hasSingleText()) {
                return userMessage.singleText();
            }
            return userMessage.contents().toString();
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text() != null ? aiMessage.text() : "[tool-invocation]";
        }
        if (message instanceof ToolExecutionResultMessage toolMessage) {
            return toolMessage.text();
        }
        return null;
    }

    private ToolExecutionResultMessage readLegacyToolExecution(String payload) {
        if (payload == null || payload.isBlank()) {
            return ToolExecutionResultMessage.from("tool", "tool", "");
        }
        return ToolExecutionResultMessage.from("tool", "tool", payload);
    }

    private String defaultTitleFromText(String text) {
        if (text == null || text.isBlank()) {
            return "New Conversation";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        int maxLen = 60;
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen) + "...";
    }

    private String normalize(Object memoryId) {
        return memoryId == null ? "anonymous" : memoryId.toString();
    }
}
