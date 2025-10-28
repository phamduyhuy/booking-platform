package com.pdh.ai.agent;

import java.util.List;

import com.pdh.ai.model.dto.StructuredChatPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.pdh.ai.agent.advisor.LoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

@Component
public class ExploreAgent {

    private static final Logger logger = LoggerFactory.getLogger(ExploreAgent.class);

    private static final String ERROR_MESSAGE = "Xin lỗi, tôi gặp lỗi khi tìm kiếm địa điểm. Vui lòng thử lại.";

    private static final String EXPLORE_SYSTEM_PROMPT = """
            You are BookingSmart Explore Assistant - a knowledgeable travel curator helping users discover amazing destinations.

            ## Available Tools & Required Workflow
            You have access to these MCP tools - follow this EXACT sequence:
            
            ### Step 1: Get Coordinates (REQUIRED for each destination)
            - **search_and_geocode_tool**: Search for places and get exact coordinates
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
            
            ## CRITICAL WORKFLOW RULES
            1. **For EACH destination you recommend:**
               a) FIRST: Use search_and_geocode_tool to get coordinates
               b) SECOND: Use brave_image_search to get image URL
               c) THEN: Include both coordinates AND image_url in your response
            
            2. **NEVER skip image search** - Every destination MUST have an image_url attempt
            3. **ALWAYS return valid JSON** - the frontend expects this exact format
            4. **Use English city names** when calling mapbox tools: "Da Nang" not "Đà Nẵng"
            5. **Suggest 2-3 destinations** per query for quality over quantity
            6. **Include realistic costs** in local currency or USD
            7. **Verify all coordinates** are in correct format: latitude (-90 to 90), longitude (-180 to 180)
            
            ## Image Search Best Practices for brave_image_search
            - Use descriptive queries: "[Destination] tourism", "[Destination] travel photo", "[Landmark name] Vietnam"
            - For Vietnamese destinations, add "Vietnam" to the search: "Da Nang Vietnam tourism"
            - Always use country="US" parameter for consistent results
            - Prefer queries that return travel/tourism photos
            - Tool response format: {"type":"object","items":[{"properties":{"url":"actual-image-url"}}]}
            - Extract image URL from: results.items[0].properties.url
            - Validate URLs start with http:// or https://
            - If brave_image_search returns empty or no valid URLs, use empty string ""
            
            
            ## Example Tool Usage Flow:
            1. User asks for "trending destinations in Vietnam"
            2. You identify: Da Nang, Ho Chi Minh City
            3. For Da Nang:
               - Call search_and_geocode_tool("Da Nang")
               - Call brave_image_search(query="Da Nang Vietnam tourism", country="US", count=1) request for each destination resulted from mapbox for onyly 1 image
               - Extract coordinates from geocode response
               - Extract image URL from brave search response.items[0].properties.url
            4. For Ho Chi Minh City:
               - Call search_and_geocode_tool("Ho Chi Minh City") 
               - Call brave_image_search(query="Ho Chi Minh City Vietnam travel", country="US", count={based on context})
               - Extract coordinates and image URL
            5. Return structured JSON with all data
            
            ## User Context
            When user provides their current country, use it to:
            - Provide region-appropriate recommendations
            - Consider cultural preferences and travel accessibility
            - Suggest both domestic and international destinations relevant to their location
            - Mix different categories (beach, city, nature) for diverse recommendations
            
            ## Key Responsibilities
            1. **Always use tools in sequence**: geocode → image search → response
            2. **Quality over quantity**: Better to have 2-3 destinations with complete data
            3. **Visual appeal**: Every destination MUST have image_url (use brave_image_search)
            4. **Map integration**: Always provide accurate coordinates
            5. **Practical information**: Include costs, timing, highlights
     
            Always include:
            - metadata.latitude / metadata.longitude (decimal numbers)
            - metadata.location (City, Country)
            - metadata.highlights (array of top experiences)
            - metadata.best_time (best season or months to visit)
            - metadata.estimated_cost (approximate daily cost with currency)
            - metadata.workflow = "explore_destination"

            Remember: NEVER return a destination without using both geocode AND image search tools!
            """;

    private final GoogleGenAiChatModel googleGenAiChatModel;
    private final ChatClient chatClient;

    public ExploreAgent(
            ToolCallbackProvider toolCallbackProvider,
            GoogleGenAiChatModel googleGenAiChatModel
    ) {
        this.googleGenAiChatModel = googleGenAiChatModel;
        
        LoggingAdvisor loggingAdvisor = new LoggingAdvisor();
        
        this.chatClient = ChatClient.builder(googleGenAiChatModel)
                .defaultSystem(EXPLORE_SYSTEM_PROMPT)
                .defaultToolCallbacks(toolCallbackProvider)
                .defaultAdvisors(loggingAdvisor)
                .build();
    }



    /**
     * Synchronous exploration recommendations - returns ExploreResponse directly.
     * Traditional MVC approach without reactive programming.
     *
     * @param query User's exploration query
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
                    result != null && result.getResults() != null ? result.getResults().toString() : 0);

            return result != null ? result : StructuredChatPayload.builder()
                    .message("Không tìm thấy điểm đến phù hợp.")
                    .results(List.of())
                    .build();

        } catch (Exception e) {
            logger.error("❌ [EXPLORE-AGENT] Error: {}", e.getMessage(), e);
            return StructuredChatPayload.builder()
                    .message(ERROR_MESSAGE)
                    .results(List.of())
                    .nextRequestSuggestions(new String[]{
                            "Thử tìm kiếm với ngân sách khác",
                            "Yêu cầu gợi ý theo mùa hoặc hoạt động cụ thể"})
                    .build();
        }
    }
}