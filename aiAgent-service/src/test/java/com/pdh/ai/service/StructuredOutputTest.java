package com.pdh.ai.service;

import com.pdh.ai.model.dto.StructuredChatPayload;
import com.pdh.ai.model.dto.StructuredResultItem;
import org.springframework.ai.converter.BeanOutputConverter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;

/**
 * Test class demonstrating structured output conversion
 */
public class StructuredOutputTest {

    @Test
    public void testBeanOutputConverter() {
        // Test the BeanOutputConverter for StructuredChatPayload
        BeanOutputConverter<StructuredChatPayload> converter = 
            new BeanOutputConverter<>(StructuredChatPayload.class);
        
        // Test the format string generation
        String format = converter.getFormat();
        assertNotNull(format);
        
        System.out.println("=== Generated format instruction ===");
        System.out.println(format);
        System.out.println("=== End format instruction ===");
        
        // Just verify the format is not empty and contains basic expected words
        assertFalse(format.trim().isEmpty(), "Format should not be empty");
        assertTrue(format.toLowerCase().contains("json"), "Format should mention JSON");
        
        System.out.println("Format length: " + format.length());
        System.out.println("Contains 'json': " + format.toLowerCase().contains("json"));
        System.out.println("Contains 'JSON': " + format.contains("JSON"));
    }

    @Test
    public void testStructuredChatPayloadSerialization() {
        // Create a sample StructuredChatPayload
        StructuredResultItem resultItem = StructuredResultItem.builder()
            .type("flight")
            .title("Flight to Paris")
            .subtitle("Air France - CDG")
            .description("Direct flight from New York to Paris")
            .imageUrl("https://example.com/flight.jpg")
            .metadata(Map.of("price", "$850", "duration", "7h 30m"))
            .build();

        StructuredChatPayload payload = StructuredChatPayload.builder()
            .message("I found some flights for you!")
            .results(List.of(resultItem))
            .build();

        // Verify the payload structure
        assertNotNull(payload.getMessage());
        assertEquals("I found some flights for you!", payload.getMessage());
        assertEquals(1, payload.getResults().size());
        
        StructuredResultItem item = payload.getResults().get(0);
        assertEquals("flight", item.getType());
        assertEquals("Flight to Paris", item.getTitle());
        assertEquals("$850", item.getMetadata().get("price"));
        
        System.out.println("Structured payload created successfully:");
        System.out.println("Message: " + payload.getMessage());
        System.out.println("Results count: " + payload.getResults().size());
        System.out.println("First result type: " + item.getType());
    }
}