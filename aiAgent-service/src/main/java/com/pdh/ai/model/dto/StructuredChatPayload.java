package com.pdh.ai.model.dto;

import java.util.List;
import java.util.Map;
import java.util.Collections;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.langchain4j.model.output.structured.Description;
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
    @Description("Primary natural language response explaining the assistant's current action and guidance.")
    private String message;

    @JsonProperty(required = true, value = "next_request_suggestions")
    @JsonPropertyDescription("Set of suggested user requests for follow-up interactions")
    @Description("1-3 concise follow-up prompts the user can pick from. Leave empty when no clear follow up exists.")
    private String[] nextRequestSuggestions;
    
    @JsonProperty(required = true, value = "results")
    @JsonPropertyDescription("Array of structured result items like flights, hotels, or information cards")
    @Description("Structured cards representing flights, hotels, itineraries, weather or destination insights relevant to the latest request.")
    @Builder.Default
    private List<StructuredResultItem> results = Collections.emptyList();
    
    @JsonProperty(required = false, value = "requiresConfirmation")
    @JsonPropertyDescription("Whether this response requires explicit user confirmation before proceeding (for booking/payment operations)")
    @Description("Set to true when the assistant must obtain explicit user confirmation before invoking booking or payment tools.")
    @Builder.Default
    private Boolean requiresConfirmation = false;
    
    @JsonProperty(required = false, value = "confirmationContext")
    @JsonPropertyDescription("Context data needed to execute the operation after user confirms. Contains operation type and pending data.")
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
        @Description("Machine readable identifier of the pending operation, e.g. create_booking, cancel_booking, process_payment.")
        private String operation;
        
        @JsonProperty(required = true, value = "summary")
        @JsonPropertyDescription("Human-readable summary of what will happen if user confirms")
        @Description("Short confirmation message that reiterates the action, key dates, routes, passengers, and total cost.")
        private String summary;
        
        @JsonProperty(required = true, value = "pendingData")
        @JsonPropertyDescription("Data needed to execute the operation after confirmation (bookingDetails, paymentDetails, etc.)")
        @Description("Key-value payload required for backend services to execute the pending booking, such as fare ids or guest data.")
        private Map<String, Object> pendingData;
        
        @JsonProperty(required = false, value = "conversationId")
        @JsonPropertyDescription("Conversation ID to resume after confirmation")
        @Description("Conversation identifier associated with the confirmation request. Use it for resuming flows after approval.")
        private String conversationId;
    }
}
