package com.pdh.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import com.pdh.ai.model.dto.StructuredChatPayload.ConfirmationContext;

/**
 * DTO for chat responses sent via WebSocket.
 * 
 * <p>Response Stages:</p>
 * <ol>
 * <li>PROCESSING: AI is processing the message</li>
 * <li>RESPONSE: Final answer ready</li>
 * <li>ERROR: Something went wrong</li>
 * </ol>
 * 
 * @author PDH
 * @since 2025-01-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    
    /**
     * Response type indicating the stage of processing.
     */
    public enum ResponseType {
        PROCESSING,  // AI is processing the message
        STREAM_UPDATE, // Partial streaming update
        RESPONSE,    // Final response ready
        ERROR        // Error occurred
    }
    
    /**
     * The type of this response.
     */
    private ResponseType type;
    
    /**
     * User ID who sent the message.
     */
    private String userId;
    
    /**
     * Client generated identifier carried through the stream.
     */
    private String requestId;

    /**
     * Conversation ID for context tracking.
     */
    private String conversationId;
    
    /**
     * The original user message (echoed back).
     */
    private String userMessage;
    
    /**
     * AI response message.
     */
    private String aiResponse;
    
    /**
     * Structured results from the AI (flights, hotels, etc.).
     */
    private List<StructuredResultItem> results;

    /**
     * Suggested follow-up prompts (if available).
     */
    private List<String> nextRequestSuggestions;
    
    /**
     * Current status message (e.g., "Processing...", "Complete").
     */
    private String status;

    /**
     * Whether the response requires explicit confirmation.
     */
    private Boolean requiresConfirmation;

    /**
     * Context payload needed when confirmation is required.
     */
    private ConfirmationContext confirmationContext;
    
    /**
     * Error message if type is ERROR.
     */
    private String error;
    
    /**
     * Response timestamp.
     */
    private LocalDateTime timestamp;
    
    /**
     * Total processing time in milliseconds.
     */
    private Long processingTimeMs;
}
