package com.pdh.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ExploreCacheWarmer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ExploreCacheWarmer.class);

    private final ExploreCacheService exploreCacheService;

    public ExploreCacheWarmer(ExploreCacheService exploreCacheService) {
        this.exploreCacheService = exploreCacheService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("🚀 [CACHE-WARMER] Starting explore cache warming...");

        // Warm up caches asynchronously to not block application startup
        CompletableFuture
                .runAsync(this::warmupCaches)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("❌ [CACHE-WARMER] Error during cache warming: {}", throwable.getMessage(),
                                throwable);
                    } else {
                        logger.info("✅ [CACHE-WARMER] Cache warming completed successfully");
                    }
                });
    }

    private void warmupCaches() {
        try {
            logger.info("🔥 [CACHE-WARMER] Warming up default explore cache for Vietnam");

            // IMPORTANT: Clear old cache first to prevent ClassCastException
            // This handles cases where serialization format has changed
            // try {
            //     logger.info("🗑️ [CACHE-WARMER] Clearing old cache before warming up...");
            //     exploreCacheService.clearDefaultCache();
            //     logger.info("✅ [CACHE-WARMER] Old cache cleared successfully");
            // } catch (Exception clearEx) {
            //     logger.warn("⚠️ [CACHE-WARMER] Failed to clear old cache (might not exist): {}", clearEx.getMessage());
            // }

            // Now generate fresh cache
            exploreCacheService.getDefaultExploreRecommendations();
            logger.info("✅ [CACHE-WARMER] Default explore recommendations cached for Vietnam");

        } catch (Exception e) {
            logger.error("❌ [CACHE-WARMER] Error warming up caches: {}", e.getMessage(), e);
            // Don't throw exception to prevent application startup failure
            // Just log the error and continue
        }
    }
}