package com.pdh.ai.model.entity;

import com.pdh.ai.model.ChatRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "chat_message", indexes = {
    @Index(name = "idx_conversation_id", columnList = "conversation_id"),
    @Index(name = "idx_conversation_id_timestamp", columnList = "conversation_id, ts")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, length = 255)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private ChatRole role;

    /**
     * Human readable excerpt of the message (used by REST APIs and UI rendering).
     * For complex payloads this may contain a short summary while the original
     * LangChain4j message is stored inside {@link #jsonContent}.
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * Serialized LangChain4j ChatMessage (JSON). This allows the agent to
     * reconstruct the exact message including multimodal content and tool calls.
     */
    @Column(name = "json_content", columnDefinition = "TEXT")
    private String jsonContent;

    @Column(name = "ts", nullable = false)
    private Instant timestamp;
    //For parent message only
    @Column(name = "title", length = 120)
    private String title;
    @ManyToOne
    @JoinColumn(name = "parent_message_id")
    private ChatMessage parentMessage;
   

}
