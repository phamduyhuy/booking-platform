<<<<<<< HEAD
package com.pdh.ai.service;

import com.pdh.ai.agent.ExploreAgent;
import com.pdh.ai.config.CacheConfig;
import com.pdh.ai.model.dto.StructuredChatPayload;
import com.pdh.ai.model.dto.StructuredResultItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ExploreCacheService {

    private static final Logger logger = LoggerFactory.getLogger(ExploreCacheService.class);

    private final ExploreAgent exploreAgent;

    public ExploreCacheService(ExploreAgent exploreAgent) {
        this.exploreAgent = exploreAgent;
    }

    /**
     * Get general explore recommendations from cache or generate if not cached
     * This is the only cached method for default page load
     * Always returns recommendations for Vietnam
     * 
     * IMPORTANT: Only caches if validation passes (see unless condition)
     */
    @Cacheable(value = CacheConfig.EXPLORE_CACHE, key = "'vietnam_default'", 
               unless = "!@exploreCacheService.isCompleteAndValid(#result)")
    public StructuredChatPayload getDefaultExploreRecommendations() {
        String country = "Việt Nam";
        logger.info("🗄️ [CACHE] Generating default explore recommendations for Vietnam");
        String query = "Gợi ý 4 điểm đến du lịch nổi tiếng và hấp dẫn nhất tại " + country + 
                      ". Bao gồm đa dạng các loại: biển, thành phố, thiên nhiên, văn hóa với hình ảnh đẹp";
        
        StructuredChatPayload result = exploreAgent.explore(query, country);
        
        // Log validation result
        if (isCompleteAndValid(result)) {
            logger.info("✅ [CACHE] Result passed validation, will be cached");
        } else {
            logger.warn("⚠️ [CACHE] Result failed validation, will NOT be cached - returning incomplete data to user");
        }
        
        return result;
    }

    /**
     * Force update cache with new result (only if validation passes)
     */
    @CachePut(value = CacheConfig.EXPLORE_CACHE, key = "'vietnam_default'",
              unless = "!@exploreCacheService.isCompleteAndValid(#result)")
    public StructuredChatPayload updateDefaultCache() {
        logger.info("🔄 [CACHE] Force updating default explore cache for Vietnam");
        String country = "Việt Nam";
        String query = "Gợi ý 4 điểm đến du lịch nổi tiếng và hấp dẫn nhất tại " + country + 
                      ". Bao gồm đa dạng các loại: biển, thành phố, thiên nhiên, văn hóa với hình ảnh đẹp";
        return exploreAgent.explore(query, country);
    }

    /**
     * Comprehensive validation for StructuredChatPayload
     * Validates ALL critical fields to ensure complete data before caching
     * Public method for SpEL condition evaluation
     * 
     * @param payload The payload to validate
     * @return true if all validations pass, false otherwise
     */
    public boolean isCompleteAndValid(StructuredChatPayload payload) {
        if (payload == null) {
            logger.warn("⚠️ [CACHE-VALIDATION] Payload is null");
            return false;
        }

        // Check message is not null or empty
        if (payload.getMessage() == null || payload.getMessage().trim().isEmpty()) {
            logger.warn("⚠️ [CACHE-VALIDATION] Message is null or empty");
            return false;
        }

        // Check results list exists and not empty
        if (payload.getResults() == null || payload.getResults().isEmpty()) {
            logger.warn("⚠️ [CACHE-VALIDATION] Results list is null or empty");
            return false;
        }

        // Validate each result item
        for (int i = 0; i < payload.getResults().size(); i++) {
            StructuredResultItem result = payload.getResults().get(i);
            if (!isResultItemComplete(result, i)) {
                return false;
            }
        }

        logger.info("✅ [CACHE-VALIDATION] All validations passed for {} results", payload.getResults().size());
        return true;
    }

    /**
     * Validate individual StructuredResultItem for completeness
     */
    private boolean isResultItemComplete(StructuredResultItem result, int index) {
        if (result == null) {
            logger.warn("⚠️ [CACHE-VALIDATION] Result[{}] is null", index);
            return false;
        }

        // Check required string fields
        if (isNullOrEmpty(result.getTitle())) {
            logger.warn("⚠️ [CACHE-VALIDATION] Result[{}] missing title", index);
            return false;
        }

        if (isNullOrEmpty(result.getSubtitle())) {
            logger.warn("⚠️ [CACHE-VALIDATION] Result[{}] missing subtitle", index);
            return false;
        }

        // Check imageUrl - CRITICAL for explore results
        if (isNullOrEmpty(result.getImageUrl())) {
            logger.warn("⚠️ [CACHE-VALIDATION] Result[{}] '{}' missing imageUrl", index, result.getTitle());
            return false;
        }

        // Validate metadata exists and has required fields
        Map<String, Object> metadata = result.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            logger.warn("⚠️ [CACHE-VALIDATION] Result[{}] '{}' missing metadata", index, result.getTitle());
            return false;
        }

        // Check critical metadata fields for explore destinations
        if (!hasRequiredMetadataFields(metadata, index, result.getTitle())) {
            return false;
        }

        return true;
    }

    /**
     * Validate required metadata fields for explore destinations
     */
    private boolean hasRequiredMetadataFields(Map<String, Object> metadata, int index, String title) {
        // Required fields for explore destinations
        String[] requiredFields = {"latitude", "longitude", "location", "highlights", "best_time", "estimated_cost", "workflow"};
        
        for (String field : requiredFields) {
            if (!metadata.containsKey(field)) {
                logger.warn("⚠️ [CACHE-VALIDATION] Result[{}] '{}' missing metadata.{}", index, title, field);
                return false;
            }

            Object value = metadata.get(field);
            if (value == null) {
                logger.warn("⚠️ [CACHE-VALIDATION] Result[{}] '{}' has null metadata.{}", index, title, field);
                return false;
            }

            // Validate specific field types
            if (field.equals("latitude") || field.equals("longitude")) {
                if (!isValidCoordinate(value, field)) {
                    logger.warn("⚠️ [CACHE-VALIDATION] Result[{}] '{}' has invalid {}: {}", index, title, field, value);
                    return false;
                }
            } else if (value instanceof String && ((String) value).trim().isEmpty()) {
                logger.warn("⚠️ [CACHE-VALIDATION] Result[{}] '{}' has empty metadata.{}", index, title, field);
                return false;
            }
        }

        return true;
    }

    /**
     * Validate coordinate values (latitude/longitude)
     */
    private boolean isValidCoordinate(Object value, String fieldName) {
        if (!(value instanceof Number)) {
            return false;
        }

        double coord = ((Number) value).doubleValue();
        
        if (fieldName.equals("latitude")) {
            return coord >= -90 && coord <= 90;
        } else if (fieldName.equals("longitude")) {
            return coord >= -180 && coord <= 180;
        }
        
        return false;
    }

    /**
     * Helper to check if string is null or empty
     */
    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Clear default explore cache
     */
    @CacheEvict(value = CacheConfig.EXPLORE_CACHE, allEntries = true)
    public void clearDefaultCache() {
        logger.info("🗑️ [CACHE] Clearing default explore cache");
    }

    /**
     * Clear default cache for Vietnam
     */
    @CacheEvict(value = CacheConfig.EXPLORE_CACHE, key = "'vietnam_default'")
    public void clearVietnamCache() {
        logger.info("🗑️ [CACHE] Clearing default cache for Vietnam");
    }
}
=======
//package com.pdh.ai.service;
//
//import com.pdh.ai.agent.ExploreAgent;
//import com.pdh.ai.config.CacheConfig;
//import com.pdh.ai.model.dto.StructuredChatPayload;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.cache.annotation.Cacheable;
//import org.springframework.cache.annotation.CacheEvict;
//import org.springframework.stereotype.Service;
//
//@Service
//public class ExploreCacheService {
//
//    private static final Logger logger = LoggerFactory.getLogger(ExploreCacheService.class);
//
//    private final ExploreAgent exploreAgent;
//
//    public ExploreCacheService(ExploreAgent exploreAgent) {
//        this.exploreAgent = exploreAgent;
//    }
//
//    /**
//     * Get general explore recommendations from cache or generate if not cached
//     * This is the only cached method for default page load
//     * Always returns recommendations for Vietnam
//     */
//    @Cacheable(value = CacheConfig.EXPLORE_CACHE, key = "'vietnam_default'")
//    public StructuredChatPayload getDefaultExploreRecommendations() {
//        String country = "Việt Nam";
//        logger.info("🗄️ [CACHE] Generating default explore recommendations for Vietnam");
//        String query = "Gợi ý 4 điểm đến du lịch nổi tiếng và hấp dẫn nhất tại " + country +
//                      ". Bao gồm đa dạng các loại: biển, thành phố, thiên nhiên, văn hóa với hình ảnh đẹp";
//        return exploreAgent.explore(query, country);
//    }
//
//    /**
//     * Clear default explore cache
//     */
//    @CacheEvict(value = CacheConfig.EXPLORE_CACHE, allEntries = true)
//    public void clearDefaultCache() {
//        logger.info("🗑️ [CACHE] Clearing default explore cache");
//    }
//
//    /**
//     * Clear default cache for Vietnam
//     */
//    @CacheEvict(value = CacheConfig.EXPLORE_CACHE, key = "'vietnam_default'")
//    public void clearDefaultCache(String country) {
//        logger.info("🗑️ [CACHE] Clearing default cache for Vietnam");
//    }
//}
>>>>>>> origin/dev
