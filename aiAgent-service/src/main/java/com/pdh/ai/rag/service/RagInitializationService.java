// package com.pdh.ai.rag.service;

// import com.pdh.common.dto.ApiResponse;
// import lombok.extern.slf4j.Slf4j;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.boot.ApplicationArguments;
// import org.springframework.boot.ApplicationRunner;
// import org.springframework.boot.context.event.ApplicationReadyEvent;
// import org.springframework.context.event.EventListener;
// import org.springframework.core.ParameterizedTypeReference;
// import org.springframework.http.ResponseEntity;
// import org.springframework.scheduling.annotation.Async;
// import org.springframework.stereotype.Service;
// import org.springframework.util.LinkedMultiValueMap;
// import org.springframework.util.MultiValueMap;
// import org.springframework.web.client.RestClient;

// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.CompletableFuture;
// import java.util.ArrayList;

// import com.pdh.ai.client.StorefrontClientService;

// /**
//  * Service for initializing the RAG vector store with existing data from microservices
//  * This service fetches all current data from flight and hotel services and populates the vector store
//  * For development/testing purposes, this can be triggered on application startup
//  * In production, consider using a scheduled job or manual trigger to avoid long startup times
//  */
// @Slf4j
// @Service

// public class RagInitializationService implements ApplicationRunner{
//     private static final Logger logger = LoggerFactory.getLogger(RagInitializationService.class);

//     // Static field to store the access token
//     private static String accessToken = null;
    
//     // Static field to store the token expiration time
//     private static long tokenExpirationTime = 0;

//     @Autowired
//     private RestClient restClient;

//     @Autowired
//     private RagDataService ragDataService;

//     @Autowired
//     private StorefrontClientService storefrontClientService;

//     @Value("${app.flight-service.url:http://localhost:8081}")
//     private String flightServiceUrl;

//     @Value("${app.hotel-service.url:http://localhost:8082}")
//     private String hotelServiceUrl;

//     // Batch size for processing
//     private static final int BATCH_SIZE = 100;
    
//     // Maximum number of pages to prevent infinite loops
//     private static final int MAX_PAGES = 10000;

//     // Keycloak configuration
//     @Value("${keycloak.token-url:https://identity-bookingsmart.huypd.dev/realms/BookingSmart/protocol/openid-connect/token}")
//     private String tokenUrl;
    
//     @Value("${keycloak.client-id:}")
//     private String clientId;
    
//     @Value("${keycloak.client-secret:}")
//     private String clientSecret;
    
//     @Value("${keycloak.username:}")
//     private String username;
    
//     @Value("${keycloak.password:}")
//     private String password;

//     /**
//      * Initialize the RAG vector store with all existing data
//      * This method is called on startup to populate the vector store with current data
//      */
//     public void initializeRagData() {
//         log.info("Starting RAG data initialization...");
//         try {
//             // Get total counts first using statistics endpoints
//             long totalFlights = getFlightCount();
//             long totalHotels = getHotelCount();
            
//             log.info("Total entities to process - Flights: {}, Hotels: {}", totalFlights, totalHotels);
            
//             // Process flights in batches
//             processFlightsInBatches(totalFlights);
            
//             // Process hotels in batches
//             processHotelsInBatches(totalHotels);
            
//             log.info("RAG data initialization completed successfully");
//         } catch (Exception e) {
//             log.error("Error during RAG data initialization: {}", e.getMessage(), e);
//         }
//     }

//     @Override
//     public void run(ApplicationArguments args) throws Exception {
//         // logger.info("🚀 [RAG-INIT] Starting RAG data initialization...");
        
//         // // First authenticate with Keycloak to get access token
        
      
//         // CompletableFuture
//         // .runAsync(() -> {
//         //     authenticateWithKeycloak();
//         //         initializeRagData();})
//         //     .whenComplete((result, throwable) -> {
//         //         if (throwable != null) {
//         //             logger.error("❌ [RAG-INIT] Error during RAG data initialization: {}", throwable.getMessage(), throwable);
//         //         } else {
//         //             logger.info("✅ [RAG-INIT] RAG data initialization completed successfully");
//         //         }
//         //     });
//     }

//     /**
//      * Authenticates with Keycloak to get an access token
//      * Stores the token in a static field for later use
//      */
//     private void authenticateWithKeycloak() {
//         try {
//             logger.info("🔑 [KEYCLOAK-AUTH] Authenticating with Keycloak...");
            
//             // Prepare form parameters for the token request
//             MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();

//             formData.add("grant_type", "password");
//             formData.add("client_id", clientId);
//             formData.add("client_secret", clientSecret);
//             formData.add("username", username);
//             formData.add("password", password);
//             // Make the POST request to Keycloak token endpoint with proper content type
//             ResponseEntity<Map> response = restClient.post()
//                 .uri(tokenUrl)
//                 .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)    
//                 .body(formData)
//                 .retrieve()
//                 .toEntity(Map.class);

//             if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
//                 Map<String, Object> responseBody = response.getBody();
                
//                 if (responseBody.containsKey("access_token")) {
//                     accessToken = (String) responseBody.get("access_token");
//                     logger.info("Access Token: {}", accessToken);
//                     logger.info("✅ [KEYCLOAK-AUTH] Successfully obtained access token");
                    
//                     // If refresh token is available, store it too
//                     if (responseBody.containsKey("refresh_token")) {
//                         // We might want to store this as well for longer sessions
//                     }
                    
//                     // Store token expiration time if available
//                     if (responseBody.containsKey("expires_in")) {
//                         int expiresIn = (Integer) responseBody.get("expires_in");
//                         tokenExpirationTime = System.currentTimeMillis() + (expiresIn * 1000L);
//                         logger.debug("⏰ Token will expire in {} seconds", expiresIn);
//                     }
//                 } else {
//                     logger.error("❌ [KEYCLOAK-AUTH] Response does not contain access_token: {}", responseBody);
//                 }
//             } else {
//                 logger.error("❌ [KEYCLOAK-AUTH] Failed to authenticate with Keycloak. HTTP Status: {}", response.getStatusCode());
//             }
//         } catch (Exception e) {
//             logger.error("❌ [KEYCLOAK-AUTH] Error during Keycloak authentication: {}", e.getMessage(), e);
//         }
//     }
    
//     /**
//      * Provides access to the stored access token
//      */
//     public static String getAccessToken() {
//         return accessToken;
//     }
    
//     /**
//      * Checks if the current token is still valid (not expired)
//      */
//     public static boolean isTokenValid() {
//         return accessToken != null && System.currentTimeMillis() < tokenExpirationTime;
//     }

//     /**
//      * Get total flight count from flight service statistics
//      */
//     private long getFlightCount() {
//         log.info("Fetching flight count from statistics...");
//         try {
//             String url = String.format("%s/flights/backoffice/flights/statistics", flightServiceUrl);
//             ResponseEntity<ApiResponse> response = restClient.get()
//                     .uri(url)
//                     .headers(h -> h.setBearerAuth(getAccessToken()))
//                     .retrieve()
//                     .toEntity(new ParameterizedTypeReference<ApiResponse>() {});

//             if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
//                 ApiResponse apiResponse = response.getBody();
//                 if (apiResponse.isSuccess() && apiResponse.getData() != null) {
//                     Map<String, Object> data = (Map<String, Object>) apiResponse.getData();
//                     Object totalFlightsObj = data.get("totalFlights");
//                     if (totalFlightsObj instanceof Number) {
//                         long totalFlights = ((Number) totalFlightsObj).longValue();
//                         log.info("Total flights retrieved from statistics: {}", totalFlights);
//                         return totalFlights;
//                     } else {
//                         log.warn("Could not extract totalFlights from statistics response, defaulting to 0");
//                         return 0;
//                     }
//                 } else {
//                     log.error("API returned error: {}", apiResponse.getMessage());
//                 }
//             } else {
//                 log.error("Failed to fetch flight statistics: HTTP {}", response.getStatusCode());
//             }
//         } catch (Exception e) {
//             log.error("Error fetching flight count: {}", e.getMessage(), e);
//         }
//         return 0;
//     }

//     /**
//      * Get total hotel count from hotel service statistics
//      */
//     private long getHotelCount() {
//         log.info("Fetching hotel count from statistics...");
//         try {
//             String url = String.format("%s/hotels/backoffice/hotels/statistics", hotelServiceUrl);
//             ResponseEntity<ApiResponse> response = restClient.get()
//                     .uri(url)
//                     .headers(h -> h.setBearerAuth(getAccessToken()))
//                     .retrieve()
//                     .toEntity(new ParameterizedTypeReference<ApiResponse>() {});

//             if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
//                 ApiResponse apiResponse = response.getBody();
//                 if (apiResponse.isSuccess() && apiResponse.getData() != null) {
//                     Map<String, Object> data = (Map<String, Object>) apiResponse.getData();
//                     Object totalHotelsObj = data.get("totalHotels");
//                     if (totalHotelsObj instanceof Number) {
//                         long totalHotels = ((Number) totalHotelsObj).longValue();
//                         log.info("Total hotels retrieved from statistics: {}", totalHotels);
//                         return totalHotels;
//                     } else {
//                         log.warn("Could not extract totalHotels from statistics response, defaulting to 0");
//                         return 0;
//                     }
//                 } else {
//                     log.error("API returned error: {}", apiResponse.getMessage());
//                 }
//             } else {
//                 log.error("Failed to fetch hotel statistics: HTTP {}", response.getStatusCode());
//             }
//         } catch (Exception e) {
//             log.error("Error fetching hotel count: {}", e.getMessage(), e);
//         }
//         return 0;
//     }

//     /**
//      * Process all flights in batches based on total count
//      */
//     private void processFlightsInBatches(long totalFlights) {
//         if (totalFlights <= 0) {
//             log.info("No flights to process");
//             return;
//         }

//         log.info("Processing {} flights in batches...", totalFlights);
//         int processedCount = 0;

//         // Process flight IDs in batches
//         int pageCount = 0;
//         boolean hasMore = true;

//         while (hasMore && pageCount < MAX_PAGES) {
//             List<Long> flightIds = getFlightIdsForPage(pageCount);
//             if (flightIds.isEmpty()) {
//                 hasMore = false;
//                 break;
//             }

//             for (Long flightId : flightIds) {
//                 try {
//                     Map<String, Object> flightDetails = storefrontClientService.getFlightDetails(flightId,accessToken);
//                     if (flightDetails != null) {
//                         ragDataService.processFlightDetails(flightDetails);
//                         processedCount++;
//                         if (processedCount % 100 == 0) {
//                             log.info("Processed {} flight details out of {} total flights", processedCount, totalFlights);
//                         }
//                     } else {
//                         log.warn("Failed to fetch flight details for flight ID: {}", flightId);
//                     }
//                 } catch (Exception e) {
//                     log.error("Error processing flight details for flight ID {}: {}", flightId, e.getMessage(), e);
//                 }
//             }

//             log.info("Processed batch of {} flight details (page {})", flightIds.size(), pageCount);
//             pageCount++;

//             if (processedCount >= totalFlights) {
//                 break; // Safety check in case total count changed during processing
//             }
//         }

//         log.info("Completed processing flights. Total processed: {}", processedCount);
//     }

//     /**
//      * Process all hotels in batches based on total count
//      */
//     private void processHotelsInBatches(long totalHotels) {
//         if (totalHotels <= 0) {
//             log.info("No hotels to process");
//             return;
//         }

//         log.info("Processing {} hotels in batches...", totalHotels);
//         int processedCount = 0;

//         // Process hotel IDs in batches
//         int pageCount = 0;
//         boolean hasMore = true;

//         while (hasMore && pageCount < MAX_PAGES) {
//             List<Long> hotelIds = getHotelIdsForPage(pageCount);
//             if (hotelIds.isEmpty()) {
//                 hasMore = false;
//                 break;
//             }

//             for (Long hotelId : hotelIds) {
//                 try {
//                     Map<String, Object> hotelDetails = storefrontClientService.getHotelDetails(hotelId,accessToken);
//                     if (hotelDetails != null) {
//                         ragDataService.processHotelDetails(hotelDetails);
//                         processedCount++;
//                         if (processedCount % 100 == 0) {
//                             log.info("Processed {} hotel details out of {} total hotels", processedCount, totalHotels);
//                         }
//                     } else {
//                         log.warn("Failed to fetch hotel details for hotel ID: {}", hotelId);
//                     }
//                 } catch (Exception e) {
//                     log.error("Error processing hotel details for hotel ID {}: {}", hotelId, e.getMessage(), e);
//                 }
//             }

//             log.info("Processed batch of {} hotel details (page {})", hotelIds.size(), pageCount);
//             pageCount++;

//             if (processedCount >= totalHotels) {
//                 break; // Safety check in case total count changed during processing
//             }
//         }

//         log.info("Completed processing hotels. Total processed: {}", processedCount);
//     }

//     /**
//      * Get flight IDs for a specific page
//      */
//     private List<Long> getFlightIdsForPage(int page) {
//         List<Long> flightIds = new ArrayList<>();
//         try {
//             String url = String.format("%s/flights/backoffice/flights/ids?page=%d&size=%d", flightServiceUrl, page, BATCH_SIZE);
//             ResponseEntity<ApiResponse> response = restClient.get()
//                     .uri(url)
//                     .headers(h -> h.setBearerAuth(getAccessToken()))
//                     .retrieve()
//                     .toEntity(new ParameterizedTypeReference<ApiResponse>() {});

//             if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
//                 ApiResponse apiResponse = response.getBody();
//                 if (apiResponse.isSuccess() && apiResponse.getData() != null) {
//                     Map<String, Object> data = (Map<String, Object>) apiResponse.getData();
//                     List<?> rawContent = (List<?>) data.get("content");

//                     if (rawContent != null && !rawContent.isEmpty()) {
//                         // Convert to Long to handle both Integer and Long values from the API
//                         for (Object id : rawContent) {
//                             if (id instanceof Long) {
//                                 flightIds.add((Long) id);
//                             } else if (id instanceof Integer) {
//                                 flightIds.add(((Integer) id).longValue());
//                             } else if (id instanceof Number) {
//                                 flightIds.add(((Number) id).longValue());
//                             }
//                         }
//                         log.debug("Fetched {} flight IDs from page {}", rawContent.size(), page);
//                     }
//                 } else {
//                     log.error("API returned error: {}", apiResponse.getMessage());
//                 }
//             } else {
//                 log.error("Failed to fetch flight IDs: HTTP {}", response.getStatusCode());
//             }
//         } catch (Exception e) {
//             log.error("Error fetching flight IDs for page {}: {}", page, e.getMessage(), e);
//         }
//         return flightIds;
//     }

//     /**
//      * Get hotel IDs for a specific page
//      */
//     private List<Long> getHotelIdsForPage(int page) {
//         List<Long> hotelIds = new ArrayList<>();
//         try {
//             String url = String.format("%s/hotels/backoffice/hotels/ids?page=%d&size=%d", hotelServiceUrl, page, BATCH_SIZE);
//             ResponseEntity<ApiResponse> response = restClient.get()
//                     .uri(url)
//                     .headers(h -> h.setBearerAuth(getAccessToken()))
//                     .retrieve()
//                     .toEntity(new ParameterizedTypeReference<ApiResponse>() {});

//             if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
//                 ApiResponse apiResponse = response.getBody();
//                 if (apiResponse.isSuccess() && apiResponse.getData() != null) {
//                     Map<String, Object> data = (Map<String, Object>) apiResponse.getData();
//                     List<?> rawContent = (List<?>) data.get("content");

//                     if (rawContent != null && !rawContent.isEmpty()) {
//                         // Convert to Long to handle both Integer and Long values from the API
//                         for (Object id : rawContent) {
//                             if (id instanceof Long) {
//                                 hotelIds.add((Long) id);
//                             } else if (id instanceof Integer) {
//                                 hotelIds.add(((Integer) id).longValue());
//                             } else if (id instanceof Number) {
//                                 hotelIds.add(((Number) id).longValue());
//                             }
//                         }
//                         log.debug("Fetched {} hotel IDs from page {}", rawContent.size(), page);
//                     }
//                 } else {
//                     log.error("API returned error: {}", apiResponse.getMessage());
//                 }
//             } else {
//                 log.error("Failed to fetch hotel IDs: HTTP {}", response.getStatusCode());
//             }
//         } catch (Exception e) {
//             log.error("Error fetching hotel IDs for page {}: {}", page, e.getMessage(), e);
//         }
//         return hotelIds;
//     }
// }