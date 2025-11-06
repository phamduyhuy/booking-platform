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
