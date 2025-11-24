package com.pdh.ai.controller;

import com.pdh.ai.agent.ExploreAgent;
import com.pdh.ai.model.dto.StructuredChatPayload;
import com.pdh.ai.service.ExploreCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/explore")
public class ExploreController {

    private static final Logger logger = LoggerFactory.getLogger(ExploreController.class);

    private final ExploreAgent exploreAgent;
    private final ExploreCacheService exploreCacheService;

    public ExploreController(ExploreAgent exploreAgent, ExploreCacheService exploreCacheService) {
        this.exploreAgent = exploreAgent;
        this.exploreCacheService = exploreCacheService;
    }

    /**
     * Get default explore recommendations (cached)
     * This endpoint is called when user first loads the page
     * Always returns recommendations for Vietnam
     * 
     * @return ResponseEntity with cached StructuredChatPayload containing default
     *         recommendations for Vietnam
     */
    @GetMapping("/default")
    public ResponseEntity<StructuredChatPayload> getDefaultRecommendations() {
        logger.info("📥 [API] Received request for default explore recommendations");
        try {
            StructuredChatPayload result = exploreCacheService.getDefaultExploreRecommendations();

            logger.info("📦 [API] Got result from cache service: result={}, message={}, resultsCount={}",
                    result != null ? "not null" : "NULL",
                    result != null ? result.getMessage() : "N/A",
                    result != null && result.getResults() != null ? result.getResults().size() : "N/A");

            if (result == null) {
                logger.error("❌ [API] Result is NULL from cache service!");
                return ResponseEntity.status(500)
                        .body(buildErrorPayload("Xin lỗi, có lỗi xảy ra khi tải gợi ý du lịch."));
            }

            if (result.getResults() == null || result.getResults().isEmpty()) {
                logger.warn("⚠️ [API] Result has empty results list");
            }

            logger.info("✅ [API] Returning successful response with {} results",
                    result.getResults() != null ? result.getResults().size() : 0);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("❌ [API] Exception caught: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildErrorPayload("Xin lỗi, có lỗi xảy ra khi tải gợi ý du lịch."));
        }
    }

    /**
     * Explore destinations and travel recommendations
     * This endpoint is stateless and provides curated destination suggestions
     * 
     * Examples:
     * - GET /explore?query=popular beaches in Vietnam
     * - GET /explore?query=best summer destinations in Asia&userCountry=Vietnam
     * - GET /explore?query=romantic getaways under $1000
     * 
     * @param query       The exploration query describing desired
     *                    destinations/experiences
     * @param userCountry Optional user's current country (for region-based
     *                    suggestions)
     * @return ResponseEntity with StructuredChatPayload containing destination
     *         recommendations
     */
    @GetMapping()
    public ResponseEntity<StructuredChatPayload> explore(
            @RequestParam String query,
            @RequestParam(required = false) String userCountry) {
        try {
            StructuredChatPayload result = exploreAgent.explore(query, userCountry);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(buildErrorPayload("Xin lỗi, có lỗi xảy ra khi tìm kiếm địa điểm."));
        }
    }

    /**
     * Get trending destinations (not cached - fresh results)
     * 
     * @param userCountry Optional user's current country
     * @return ResponseEntity with trending destination recommendations
     */
    @GetMapping("/trending")
    public ResponseEntity<StructuredChatPayload> getTrending(
            @RequestParam(required = false, defaultValue = "Việt Nam") String userCountry) {
        try {
            String trendingQuery = "Giúp tôi liệt kê 3 điểm đến du lịch đang thịnh hành hiện nay tại " + userCountry +
                    ". Bao gồm các điểm đến biển, thành phố, và thiên nhiên với hình ảnh đẹp";
            StructuredChatPayload result = exploreAgent.explore(trendingQuery, userCountry);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(buildErrorPayload("Xin lỗi, không thể tải điểm đến phổ biến."));
        }
    }

    /**
     * Get seasonal recommendations based on current time of year (not cached -
     * fresh results)
     * 
     * @param season      Required season parameter (spring, summer, fall, winter)
     * @param userCountry Optional user's current country
     * @return ResponseEntity with seasonal destination recommendations
     */
    @GetMapping(value = "/seasonal")
    public ResponseEntity<StructuredChatPayload> getSeasonalRecommendations(
            @RequestParam(required = true) String season,
            @RequestParam(required = false, defaultValue = "Việt Nam") String userCountry) {
        try {
            String query = String.format("Gợi ý 3 điểm đến du lịch phù hợp với mùa %s tại %s. " +
                    "Bao gồm lý do tại sao phù hợp với mùa này và hình ảnh đẹp", season, userCountry);
            StructuredChatPayload result = exploreAgent.explore(query, userCountry);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(buildErrorPayload("Xin lỗi, không thể tải gợi ý theo mùa."));
        }
    }

    /**
     * Clear explore cache (Admin endpoint for troubleshooting)
     * Use this to clear old cache data when serialization format changes
     */
    @DeleteMapping("/cache/clear")
    public ResponseEntity<String> clearCache() {
        try {
            exploreCacheService.clearDefaultCache();
            logger.info("🗑️ [API] Cache cleared successfully");
            return ResponseEntity.ok("Cache cleared successfully");
        } catch (Exception e) {
            logger.error("❌ [API] Failed to clear cache: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Failed to clear cache: " + e.getMessage());
        }
    }

    private StructuredChatPayload buildErrorPayload(String message) {
        return StructuredChatPayload.builder()
                .message(message)
                .results(java.util.List.of())
                .nextRequestSuggestions(new String[] {
                        "Hãy thử một từ khóa khác",
                        "Bạn có muốn tìm theo ngân sách cụ thể không?"
                })
                .build();
    }
}
