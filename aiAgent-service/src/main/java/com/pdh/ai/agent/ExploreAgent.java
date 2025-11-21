package com.pdh.ai.agent;

<<<<<<< HEAD
import java.util.List;
import java.util.Map;

import com.pdh.ai.model.dto.StructuredChatPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdh.ai.agent.advisor.LoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

@Component
public class ExploreAgent {

        private static final Logger logger = LoggerFactory.getLogger(ExploreAgent.class);

        private static final String ERROR_MESSAGE = "Xin lỗi, tôi gặp lỗi khi tìm kiếm địa điểm. Vui lòng thử lại.";

    private static final String EXPLORE_SYSTEM_PROMPT = """
            You are BookingSmart Explore Assistant - a knowledgeable travel curator helping users discover amazing destinations.
           
            DO NOT add any text before or after the JSON. Start directly with opening brace, include message, results array, and metadata.

            ## Available Tools & Required Workflow
            You have access to these MCP tools - follow this EXACT sequence:
            
            ### Step 1: Get Coordinates (REQUIRED for each destination)
            - **mapbox tools*: Search for places and get exact coordinates
              - Use English place names: "Da Nang", "Ho Chi Minh City", "Hanoi", "Phu Quoc"
              - Returns latitude, longitude, full address
              - ALWAYS use this FIRST to get accurate coordinates for each destination
            
            ### Step 2: Get Images (REQUIRED for each destination)
            - **brave_image_search**: Find high-quality destination images
              - Parameters: query (string), count (optional, default 5), country (optional, use "US" for best results)
              - IMMEDIATELY after getting coordinates, use this tool for EACH destination
              - Search query examples: "Da Nang Vietnam tourism", "Ho Chi Minh City Vietnam travel", "Phu Quoc beach resort"
              - The tool returns array of objects with properties.url field containing actual image URLs
              - Extract the FIRST valid image URL from results[0].properties.url
              - If no valid image found, use empty string "" for image_url
            
            ### Step 3: Build Complete Result
            For each destination, your result object must include:
            - title: Destination name
            - description: Brief compelling description (2-3 sentences)
            - imageUrl: URL from brave_image_search (or empty string if none)
            - complete metadata object with:
              * latitude: decimal number (-90 to 90)
              * longitude: decimal number (-180 to 180)
              * location: City, Country format
              * highlights: array of strings (top experiences)
              * best_time: string (best season/months)
              * estimated_cost: string with currency
              * workflow: always "explore_destination"
            
            ## MANDATORY RULES
            1. **ALWAYS return valid JSON** - no markdown, no code blocks, no extra text
            2. **For EACH destination**: Call mapbox mcp server → brave mcp server → include data
            3. **NEVER skip image search** - every destination needs imageUrl attempt
            4. **Use English city names** for mapbox tools: "Da Nang" not "Đà Nẵng"
            5. **Suggest 2-3 quality destinations** per query
            6. **Include ALL required metadata fields** for every result
            7. **Verify coordinates** are valid numbers in correct ranges
            
            ## EXECUTION CHECKLIST
            Before returning response, verify:
            ✓ Called search_and_geocode_tool for each destination
            ✓ Called brave_image_search for each destination
            ✓ All results have imageUrl (even if empty string)
            ✓ All results have complete metadata (latitude, longitude, location, highlights, best_time, estimated_cost, workflow)
            ✓ Response is valid JSON (no markdown, no extra text)
            ✓ Message field explains recommendations clearly
            
            ## User Context
            When user provides their current country, use it to:
            - Provide region-appropriate recommendations
            - Consider cultural preferences and travel accessibility
            - Suggest both domestic and international destinations relevant to their location
            - Mix different categories (beach, city, nature) for diverse recommendations
            
            Remember: NEVER return plain text. ALWAYS return structured JSON with complete tool-sourced data!
            """;

        private final GoogleGenAiChatModel googleGenAiChatModel;
        private final ChatClient chatClient;
        private final BeanOutputConverter<StructuredChatPayload> beanOutputConverter = new BeanOutputConverter<>(StructuredChatPayload.class);

        public ExploreAgent(
                        ToolCallbackProvider toolCallbackProvider,
                        GoogleGenAiChatModel googleGenAiChatModel,
                        ToolCallingManager toolCallingManager) {
                this.googleGenAiChatModel = googleGenAiChatModel;

                LoggingAdvisor loggingAdvisor = new LoggingAdvisor();
                var toolCallAdvisor = ToolCallAdvisor.builder()
                                .toolCallingManager(toolCallingManager)
                                
                                .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                                .build();
                // StructuredOutputValidationAdvisor: Validates JSON schema and retries on
                // validation failures
                // Configure ObjectMapper to ignore null values for optional fields
                ObjectMapper objectMapper = new ObjectMapper()
                                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

                StructuredOutputValidationAdvisor validationAdvisor = StructuredOutputValidationAdvisor.builder()
                                .objectMapper(objectMapper)
                                .outputType(StructuredChatPayload.class)
                                .maxRepeatAttempts(3)
                                .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 1000)
                                .build();
                
                // Get format from BeanOutputConverter
                String format = this.beanOutputConverter.getFormat();
                
                this.chatClient = ChatClient.builder(googleGenAiChatModel)
                                .defaultSystem(EXPLORE_SYSTEM_PROMPT)
                                .defaultToolCallbacks(toolCallbackProvider)
                                .defaultAdvisors(toolCallAdvisor, validationAdvisor, loggingAdvisor)
                                .build();
        }

        /**
         * Synchronous exploration recommendations - returns ExploreResponse directly.
         * Traditional MVC approach without reactive programming.
         *
         * @param query       User's exploration query
         * @param userCountry Optional user's current country
         * @return StructuredChatPayload with destination-focused recommendations
         */
        public StructuredChatPayload explore(String query, String userCountry) {
                logger.info("🌍 [EXPLORE-AGENT] Starting exploration query: {} (userCountry: {})",
                                query, userCountry);

                try {
                       
                        
                        // Add user country context to the query if provided
                        String enhancedQuery = query;
                        if (userCountry != null && !userCountry.trim().isEmpty()) {
                                enhancedQuery = String.format("User is from %s. %s", userCountry, query);
                        }

                        StructuredChatPayload result = chatClient.prompt()
                                        .user(enhancedQuery)
                                        .call()
                                        .entity(StructuredChatPayload.class);

                        logger.info("✅ [EXPLORE-AGENT] Successfully got structured response: message={}, results={}",
                                        result != null ? result.getMessage() : "null",
                                        result != null && result.getResults() != null ? result.getResults().toString()
                                                        : 0);

                        return result != null ? result
                                        : StructuredChatPayload.builder()
                                                        .message("Không tìm thấy điểm đến phù hợp.")
                                                        .results(List.of())
                                                        .build();

                } catch (Exception e) {
                        logger.error("❌ [EXPLORE-AGENT] Error: {}", e.getMessage(), e);
                        return StructuredChatPayload.builder()
                                        .message(ERROR_MESSAGE)
                                        .results(List.of())
                                        .nextRequestSuggestions(new String[] {
                                                        "Thử tìm kiếm với ngân sách khác",
                                                        "Yêu cầu gợi ý theo mùa hoặc hoạt động cụ thể" })
                                        .build();
                }
        }
}
=======
import com.pdh.ai.model.dto.StructuredChatPayload;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ExploreAgent {

    @Agent("Produces curated travel suggestions for the Explore experience")
    @SystemMessage("""
            You are BookingSmart's travel inspiration concierge. Generate engaging destination recommendations in Vietnamese,
            enriched with weather highlights, unique experiences, and suggested durations. Use MCP tools when helpful
            (mapbox for location data, openweather for climate, brave-search for imagery).
            Always output JSON matching StructuredChatPayload with fields:
            {
              "message": string summary,
              "results": array of destination cards,
              "next_request_suggestions": array of strings,
              "requiresConfirmation": false,
              "confirmationContext": null
            }
            """)
    @UserMessage("""
            User query: {{query}}
            Traveller country context: {{userCountry}}
            """)
    StructuredChatPayload explore(@V("query") String query, @V("userCountry") String userCountry);
}
>>>>>>> origin/dev
