package com.pdh.ai.model.dto;

import java.util.List;
import java.util.Map;
import java.util.Collections;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredChatPayload {
    @JsonProperty(required = true, value = "message")
    @JsonPropertyDescription("Natural language response message to the user")
    private String message;

    @JsonProperty(required = false, value = "next_request_suggestions")
    @JsonPropertyDescription("Set of suggested user requests for follow-up interactions")
    @Builder.Default
    private String[] nextRequestSuggestions= new String[0];
    
    @JsonProperty(required = false, value = "results")
    @JsonPropertyDescription("Array of structured result items like flights, hotels, or information cards")
    @Builder.Default
    private List<StructuredResultItem> results = Collections.emptyList();
    
    @JsonProperty(required = false, value = "requiresConfirmation")
    @JsonPropertyDescription("Whether this response requires explicit user confirmation before proceeding (for booking/payment operations)")
    @Builder.Default
    private Boolean requiresConfirmation = false;
    
    @JsonProperty(value = "confirmationContext")
    @JsonPropertyDescription("Optional context data needed to execute the operation after user confirms. Contains operation type and pending data. Can be null when no confirmation is needed.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ConfirmationContext confirmationContext;
    
    /**
     * Confirmation context for operations requiring user approval.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmationContext {
        @JsonProperty(required = true, value = "operation")
        @JsonPropertyDescription("Type of operation pending confirmation: create_booking, process_payment, cancel_booking")
        private String operation;
        
        @JsonProperty(required = true, value = "summary")
        @JsonPropertyDescription("Human-readable summary of what will happen if user confirms")
        private String summary;
        
        @JsonProperty(required = true, value = "pendingData")
        @JsonPropertyDescription("Data needed to execute the operation after confirmation (bookingDetails, paymentDetails, etc.)")
        private Map<String, Object> pendingData;
        
        @JsonProperty(required = false, value = "conversationId")
        @JsonPropertyDescription("Conversation ID to resume after confirmation")
        private String conversationId;
    }
}
