package com.pdh.hotel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdh.common.config.OpenApiResponses;
import com.pdh.hotel.dto.HotelBookingDetailsDto;
import com.pdh.hotel.dto.response.RoomResponseDto;
import com.pdh.hotel.model.Hotel;
import com.pdh.hotel.repository.HotelRepository;
import com.pdh.hotel.service.AmenityService;
import com.pdh.hotel.service.HotelService;
import com.pdh.hotel.service.HotelInventoryService;
import com.pdh.hotel.service.HotelSearchSpecificationService;
import com.pdh.hotel.service.RoomTypeService;
import com.pdh.hotel.mapper.HotelMapper;
import com.pdh.common.dto.SearchResponse;
import com.pdh.common.dto.DestinationSearchResult;
import com.pdh.common.dto.ErrorResponse;
import com.pdh.common.validation.SearchValidation;
import com.pdh.hotel.util.HotelSearchResponseBuilder;
import com.pdh.hotel.util.HotelSearchUtils;
import com.pdh.hotel.util.HotelStaticData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityNotFoundException;

/**
 * Hotel Controller
 * 
 * Handles all hotel-related API endpoints including search, details, and reservations.
 * This controller provides both public APIs for storefront and internal APIs for booking integration.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Hotels", description = "Hotel management and search operations")
@SecurityRequirement(name = "oauth2")
public class HotelController {

    private final HotelRepository hotelRepository;
    private final HotelService hotelService;
    private final HotelSearchSpecificationService hotelSearchSpecificationService;
    private final RoomTypeService roomTypeService;
    private final AmenityService amenityService;
    private final ObjectMapper objectMapper;
    private final HotelMapper hotelMapper;
    private final HotelInventoryService hotelInventoryService;
    

  
    /**
     * Health check endpoint
     */
    @Operation(
        summary = "Hotel service health check",
        description = "Returns the health status of the hotel service",
        tags = {"Monitoring"}
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Service is healthy",
            content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @GetMapping("/backoffice/hotel/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.info("Hotel service health check requested");
        
        Map<String, Object> healthStatus = Map.of(
            "status", "UP",
            "service", "hotel-service",
            "timestamp", LocalDateTime.now(),
            "message", "Hotel Service is running properly"
        );
        
        return ResponseEntity.ok(healthStatus);
    }

    @Operation(
        summary = "Get room details",
        description = "Retrieve detailed information about a specific room by ID",
        tags = {"Public API", "Details"}
    )
    @OpenApiResponses.StandardApiResponsesWithNotFound
    @GetMapping("/storefront/rooms/{roomTypeId}")
    public ResponseEntity<RoomResponseDto> getRoomDetails(
            @Parameter(description = "Room type ID", required = true, example = "101")
            @PathVariable Long roomTypeId,
            @Parameter(description = "Check-in date (YYYY-MM-DD)", required = false)
            @RequestParam(required = false) String checkInDate,
            @Parameter(description = "Check-out date (YYYY-MM-DD)", required = false)
            @RequestParam(required = false) String checkOutDate,
            @Parameter(description = "Number of rooms requested", required = false)
            @RequestParam(defaultValue = "1") Integer roomsRequested) {
        log.info("Room type details request for ID: {}", roomTypeId);
        try {
            LocalDate checkIn = StringUtils.hasText(checkInDate) ? LocalDate.parse(checkInDate) : LocalDate.now();
            LocalDate checkOut = StringUtils.hasText(checkOutDate) ? LocalDate.parse(checkOutDate) : checkIn.plusDays(1);

            if (!checkOut.isAfter(checkIn)) {
                return ResponseEntity.badRequest().build();
            }

            RoomResponseDto room = roomTypeService.getRoomDetails(roomTypeId, checkIn, checkOut, roomsRequested);
            return ResponseEntity.ok(room);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error retrieving room details", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // === STOREFRONT API ENDPOINTS ===

    /**
     * Search hotels for storefront
     * GET /hotels/storefront/search?destination=Ho Chi Minh City&checkInDate=2024-02-15&checkOutDate=2024-02-17&guests=2&rooms=1
     */
    @GetMapping("/storefront/search")
    @Tool(name = "search_hotels", description = "Search hotels by destination(required = false), stay dates (required = false), price range (required = false), room type (required = false), " +
            "amenities (required = false), and rating (required = false) " +
            "filters with pagination (default if user not provide: guests=2 rooms=1, page=1, page_size=20) support.")
    public ResponseEntity<Map<String, Object>> searchHotels(
            @Schema(description = "Destination city", example = "Ho Chi Minh City")
            @RequestParam(required = false) String destination,
            @Schema(description = "Hotel name", example = "Rex Hotel Saigon")
            @RequestParam(required = false) String hotelName,
            @Schema(description = "Room type", example = "Deluxe", allowableValues = {"Standard", "Deluxe", "Suite", "Executive"})
            @RequestParam(required = false) String roomType,
            @Schema(description = "List of amenities", example = "wifi,pool,gym")
            @RequestParam(required = false) List<String> amenities,
            @Schema(description = "Minimum price per night in VND", example = "500000", minimum = "0")
            @RequestParam(required = false) BigDecimal minPrice,
            @Schema(description = "Maximum price per night in VND", example = "5000000", minimum = "0")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Schema(description = "Minimum rating (1-5 stars)", example = "3", minimum = "1", maximum = "5")
            @RequestParam(required = false) BigDecimal minRating,
            @Schema(description = "Maximum rating (1-5 stars)", example = "5", minimum = "1", maximum = "5")
            @RequestParam(required = false) BigDecimal maxRating,
            @Schema(description = "Check-in date (YYYY-MM-DD)", example = "2024-12-25", pattern = "^\\d{4}-\\d{2}-\\d{2}$")
            @RequestParam(required = false) String checkInDate,
            @Schema(description = "Check-out date (YYYY-MM-DD)", example = "2024-12-30", pattern = "^\\d{4}-\\d{2}-\\d{2}$")
            @RequestParam(required = false) String checkOutDate,
            @Schema(description = "Number of guests", example = "2", minimum = "1", maximum = "10", defaultValue = "2")
            @RequestParam(defaultValue = "2") Integer guests,
            @Schema(description = "Number of rooms", example = "1", minimum = "1", maximum = "5", defaultValue = "1")
            @RequestParam(defaultValue = "1") Integer rooms,
            @Schema(description = "Page number (1-based)", example = "1", minimum = "1", defaultValue = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Schema(description = "Number of results per page", example = "20", minimum = "1", maximum = "100", defaultValue = "20")
            @RequestParam(defaultValue = "20") Integer limit) {

        log.info("Hotel search request: destination={}, hotelName={}, roomType={}, minPrice={}, maxPrice={}, checkIn={}, checkOut={}, guests={}, rooms={}",
                destination, hotelName, roomType, minPrice, maxPrice, checkInDate, checkOutDate, guests, rooms);

        try {
            Integer effectiveGuests = (guests != null && guests > 0) ? guests : 2;
            Integer effectiveRooms = (rooms != null && rooms > 0) ? rooms : 1;
            Integer effectivePage = (page != null && page > 0) ? page : 1;
            Integer effectiveLimit = (limit != null && limit > 0) ? limit : 20;

            // Validate textual inputs
            SearchValidation.ValidationResult destinationValidation = SearchValidation.validateSearchQuery(destination);
            if (!destinationValidation.isValid()) {
                return ResponseEntity.badRequest().body(HotelSearchResponseBuilder.validationError("Invalid destination: " + destinationValidation.getErrorMessage()));
            }

            SearchValidation.ValidationResult hotelValidation = SearchValidation.validateSearchQuery(hotelName);
            if (!hotelValidation.isValid()) {
                return ResponseEntity.badRequest().body(HotelSearchResponseBuilder.validationError("Invalid hotel name: " + hotelValidation.getErrorMessage()));
            }

            SearchValidation.ValidationResult roomTypeValidation = SearchValidation.validateSearchQuery(roomType);
            if (!roomTypeValidation.isValid()) {
                return ResponseEntity.badRequest().body(HotelSearchResponseBuilder.validationError("Invalid room type: " + roomTypeValidation.getErrorMessage()));
            }

            if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
                return ResponseEntity.badRequest().body(HotelSearchResponseBuilder.validationError("minPrice cannot be greater than maxPrice"));
            }

            if (minRating != null && maxRating != null && minRating.compareTo(maxRating) > 0) {
                return ResponseEntity.badRequest().body(HotelSearchResponseBuilder.validationError("minRating cannot be greater than maxRating"));
            }

            String sanitizedDestination = SearchValidation.sanitizeSearchQuery(destination);
            List<String> destinationTerms = HotelSearchUtils.extractDestinationTerms(sanitizedDestination);
            String sanitizedHotelName = SearchValidation.sanitizeSearchQuery(hotelName);
            String sanitizedRoomType = SearchValidation.sanitizeSearchQuery(roomType);

            List<String> sanitizedAmenities = HotelSearchUtils.sanitizeAmenities(amenities);

            LocalDate effectiveCheckIn;
            LocalDate effectiveCheckOut;
            try {
                effectiveCheckIn = StringUtils.hasText(checkInDate) ? LocalDate.parse(checkInDate) : LocalDate.now();
                effectiveCheckOut = StringUtils.hasText(checkOutDate) ? LocalDate.parse(checkOutDate) : effectiveCheckIn.plusDays(1);
            } catch (DateTimeParseException ex) {
                return ResponseEntity.badRequest().body(HotelSearchResponseBuilder.validationError("Invalid date format. Expected YYYY-MM-DD"));
            }

            if (!effectiveCheckOut.isAfter(effectiveCheckIn)) {
                return ResponseEntity.badRequest().body(HotelSearchResponseBuilder.validationError("checkOutDate must be after checkInDate"));
            }

            boolean hasFilters = StringUtils.hasText(sanitizedDestination)
                || StringUtils.hasText(sanitizedHotelName)
                || StringUtils.hasText(sanitizedRoomType)
                || (minPrice != null)
                || (maxPrice != null)
                || (minRating != null)
                || (maxRating != null)
                || !sanitizedAmenities.isEmpty();

            Pageable pageable = PageRequest.of(Math.max(effectivePage - 1, 0), effectiveLimit);

            if (!hasFilters) {
                log.info("Returning initial hotel data without filters");
                Page<Hotel> hotelPage = hotelRepository.findAll(pageable);
                List<Map<String, Object>> hotels = hotelPage.getContent().stream()
                    .map(hotel -> hotelMapper.toStorefrontSearchResponse(
                        hotel,
                        effectiveCheckIn,
                        effectiveCheckOut,
                        effectiveGuests,
                        effectiveRooms))
                    .collect(Collectors.toList());

                Map<String, Object> availableFilters = Map.of(
                    "destinations", hotels.stream()
                        .map(entry -> (String) entry.getOrDefault("city", ""))
                        .filter(StringUtils::hasText)
                        .distinct()
                        .collect(Collectors.toList())
                );

                return ResponseEntity.ok(HotelSearchResponseBuilder.pagedResponse(
                    hotels,
                    hotelPage.getTotalElements(),
                    effectivePage,
                    effectiveLimit,
                    hotelPage.hasNext(),
                    Map.of(),
                    availableFilters
                ));
            }

            HotelSearchSpecificationService.HotelSearchCriteria criteria = new HotelSearchSpecificationService.HotelSearchCriteria();
            criteria.setDestination(sanitizedDestination);
            criteria.setDestinationTerms(destinationTerms);
            criteria.setName(sanitizedHotelName);
            criteria.setRoomType(sanitizedRoomType);
            criteria.setAmenities(sanitizedAmenities);
            criteria.setMinPrice(minPrice);
            criteria.setMaxPrice(maxPrice);
            criteria.setMinRating(minRating);
            criteria.setMaxRating(maxRating);

            Page<Hotel> hotelPage = hotelSearchSpecificationService.searchHotels(criteria, pageable);

            List<Map<String, Object>> hotels = hotelPage.getContent().stream()
                .map(hotel -> hotelMapper.toStorefrontSearchResponse(
                    hotel,
                    effectiveCheckIn,
                    effectiveCheckOut,
                    effectiveGuests,
                    effectiveRooms))
                .collect(Collectors.toList());

            Map<String, Object> appliedFilters = new LinkedHashMap<>();
            appliedFilters.put("destination", sanitizedDestination);
            appliedFilters.put("hotelName", sanitizedHotelName);
            appliedFilters.put("roomType", sanitizedRoomType);
            appliedFilters.put("minPrice", minPrice);
            appliedFilters.put("maxPrice", maxPrice);
            appliedFilters.put("minRating", minRating);
            appliedFilters.put("maxRating", maxRating);
            appliedFilters.put("amenities", sanitizedAmenities);

            Map<String, Object> availableFilters = Map.of(
                "amenities", amenityService.getActiveAmenities().stream()
                    .map(dto -> dto.getName())
                    .collect(Collectors.toList())
            );

            log.info("Found {} hotels for search criteria", hotels.size());
            return ResponseEntity.ok(HotelSearchResponseBuilder.pagedResponse(
                hotels,
                hotelPage.getTotalElements(),
                effectivePage,
                effectiveLimit,
                hotelPage.hasNext(),
                appliedFilters,
                availableFilters
            ));

        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error searching hotels", e);
            int effectivePage = (page != null && page > 0) ? page : 1;
            int effectiveLimit = (limit != null && limit > 0) ? limit : 20;
            return ResponseEntity.ok(HotelSearchResponseBuilder.searchFailure(e.getMessage(), effectivePage, effectiveLimit));
        }
    }

    @GetMapping("/storefront/availability")
    @Tool(name = "check_hotel_availability", description = "Check if a hotel room type has enough inventory for the requested stay dates.")
    public ResponseEntity<Map<String, Object>> checkHotelAvailability(
            @Schema(description = "Hotel identifier", example = "1", minimum = "1", required = true)
            @RequestParam Long hotelId,
            @Schema(description = "Room type name", example = "Deluxe", required = true)
            @RequestParam String roomType,
            @Schema(description = "Check-in date in YYYY-MM-DD format", example = "2024-12-25", pattern = "^\\d{4}-\\d{2}-\\d{2}$", required = true)
            @RequestParam String checkInDate,
            @Schema(description = "Check-out date in YYYY-MM-DD format", example = "2024-12-30", pattern = "^\\d{4}-\\d{2}-\\d{2}$", required = true)
            @RequestParam String checkOutDate,
            @Schema(description = "Number of rooms requested", example = "1", minimum = "1", maximum = "10", defaultValue = "1")
            @RequestParam(defaultValue = "1") Integer rooms) {

        if (hotelId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "hotelId is required"));
        }

        if (!StringUtils.hasText(roomType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "roomType is required"));
        }

        try {
            LocalDate checkIn = LocalDate.parse(checkInDate);
            LocalDate checkOut = LocalDate.parse(checkOutDate);

            HotelInventoryService.AvailabilitySummary summary = hotelInventoryService.getAvailabilitySummary(
                    hotelId,
                    roomType.trim(),
                    rooms,
                    checkIn,
                    checkOut);

            List<Map<String, Object>> dailyAvailability = summary.getDailyDetails().stream()
                    .map(detail -> {
                        Map<String, Object> detailMap = new LinkedHashMap<>();
                        detailMap.put("date", detail.getDate().toString());
                        detailMap.put("totalInventory", detail.getTotalInventory());
                        detailMap.put("totalReserved", detail.getTotalReserved());
                        detailMap.put("remaining", detail.getRemaining());
                        return detailMap;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("hotelId", hotelId.toString());
            response.put("roomType", roomType.trim());
            response.put("checkIn", checkIn.toString());
            response.put("checkOut", checkOut.toString());
            response.put("roomsRequested", summary.getRequestedRooms());
            response.put("roomsAvailable", summary.getRoomsAvailable());
            response.put("available", summary.isAvailable());
            response.put("message", summary.getMessage());
            response.put("dailyAvailability", dailyAvailability);

            return ResponseEntity.ok(response);

        } catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Invalid date format. Expected YYYY-MM-DD"));
        }
    }


    /**
     * Get hotel details by ID for storefront
     */
    @Operation(
        summary = "Get hotel details",
        description = "Retrieve detailed information about a specific hotel including rooms, amenities, and policies",
        tags = {"Public API"}
    )
    @OpenApiResponses.StandardApiResponsesWithNotFound
    @GetMapping("/storefront/{hotelId}")
    public ResponseEntity<Map<String, Object>> getStorefrontHotelDetails(
            @Parameter(description = "Hotel ID", required = true, example = "1")
            @PathVariable Long hotelId,
            @Parameter(description = "Check-in date (YYYY-MM-DD)", required = false)
            @RequestParam(required = false) String checkInDate,
            @Parameter(description = "Check-out date (YYYY-MM-DD)", required = false)
            @RequestParam(required = false) String checkOutDate) {
        log.info("Hotel details request for ID: {}, checkIn: {}, checkOut: {}", hotelId, checkInDate, checkOutDate);

        try {
            Optional<Hotel> hotelOpt = hotelRepository.findById(hotelId);
            if (hotelOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            LocalDate effectiveCheckIn = StringUtils.hasText(checkInDate) ? LocalDate.parse(checkInDate) : LocalDate.now();
            LocalDate effectiveCheckOut = StringUtils.hasText(checkOutDate) ? LocalDate.parse(checkOutDate) : effectiveCheckIn.plusDays(1);

            Hotel hotel = hotelOpt.get();
            Map<String, Object> response = hotelMapper.toStorefrontDetailedResponse(hotel, effectiveCheckIn, effectiveCheckOut);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting hotel details", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get hotel details for backoffice
     */
    @Operation(
        summary = "Get hotel details for backoffice",
        description = "Retrieve hotel information for administrative purposes",
        tags = {"Admin API"}
    )
    @SecurityRequirement(name = "oauth2", scopes = {"admin"})
    @OpenApiResponses.StandardApiResponsesWithNotFound
    @GetMapping("/backoffice/{hotelId}")
    public ResponseEntity<Long> getHotelDetails(
            @Parameter(description = "Hotel ID", required = true, example = "1")
            @PathVariable Long hotelId) {
        log.info("Getting hotel details for ID: {}", hotelId);

        return ResponseEntity.ok(hotelId);
    }

    // === BOOKING INTEGRATION ENDPOINTS ===
    
    /**
     * Reserve hotel for booking (called by Booking Service)
     * Enhanced to handle detailed product information
     */
    @Operation(
        summary = "Reserve hotel",
        description = "Create a hotel reservation as part of the booking process. Supports both detailed product information and legacy mode.",
        tags = {"Internal API", "Booking"}
    )
    @SecurityRequirement(name = "oauth2", scopes = {"admin", "internal"})
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reservation created successfully",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid reservation data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Reservation failed")
    })
    @PostMapping("/reserve")
    public ResponseEntity<Map<String, Object>> reserveHotel(
            @Parameter(description = "Reservation request containing booking details", required = true)
            @RequestBody Map<String, Object> request) {
        log.info("Hotel reservation request: {}", request);

        try {
            String bookingId = (String) request.get("bookingId");
            String sagaId = (String) request.get("sagaId");

            // Check if detailed hotel information is provided
            Object hotelDetailsObj = request.get("hotelDetails");

            if (hotelDetailsObj != null) {
                // Handle detailed reservation with product information
                HotelBookingDetailsDto hotelDetails = objectMapper.convertValue(hotelDetailsObj, HotelBookingDetailsDto.class);

                // Call enhanced hotel service method
                hotelService.reserveHotel(UUID.fromString(bookingId), sagaId, hotelDetails);

                Map<String, Object> response = Map.of(
                    "status", "success",
                    "message", "Hotel reservation created with detailed product information",
                    "reservationId", "HTL-" + bookingId.substring(0, 8),
                    "bookingId", bookingId,
                    "sagaId", sagaId,
                    "hotelId", hotelDetails.getHotelId(),
                    "roomTypeId", hotelDetails.getRoomTypeId(),
                    "roomId", hotelDetails.getRoomId(),
                    "guests", hotelDetails.getNumberOfGuests(),
                    "rooms", hotelDetails.getNumberOfRooms()
                );

                log.info("Detailed hotel reservation response: {}", response);
                return ResponseEntity.ok(response);

            } else {
                // Legacy support - basic reservation without detailed product info
                hotelService.reserveHotel(UUID.fromString(bookingId));

                Map<String, Object> response = Map.of(
                    "status", "success",
                    "message", "Hotel reservation created (legacy mode)",
                    "reservationId", "HTL-" + bookingId,
                    "bookingId", bookingId,
                    "sagaId", sagaId
                );

                log.info("Legacy hotel reservation response: {}", response);
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("Error processing hotel reservation: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = Map.of(
                "status", "error",
                "message", "Hotel reservation failed: " + e.getMessage(),
                "bookingId", request.get("bookingId"),
                "sagaId", request.get("sagaId")
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Cancel hotel reservation (compensation)
     */
    @Operation(
        summary = "Cancel hotel reservation",
        description = "Cancel a hotel reservation as part of compensation logic in the booking saga",
        tags = {"Internal API", "Booking"}
    )
    @SecurityRequirement(name = "oauth2", scopes = {"admin", "internal"})
    @OpenApiResponses.StandardApiResponses
    @PostMapping("/cancel-reservation")
    public ResponseEntity<Map<String, Object>> cancelHotelReservation(@RequestBody Map<String, Object> request) {
        log.info("Hotel cancellation request: {}", request);
        
        String bookingId = (String) request.get("bookingId");
        String sagaId = (String) request.get("sagaId");
        String reason = (String) request.get("reason");
        
        // Mock implementation - in real scenario, this would:
        // 1. Find and cancel the reservation
        // 2. Free up the rooms
        // 3. Update reservation status
        
        Map<String, Object> response = Map.of(
            "status", "success",
            "message", "Hotel reservation cancelled",
            "bookingId", bookingId,
            "sagaId", sagaId,
            "reason", reason
        );
        
        log.info("Hotel cancellation response: {}", response);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Confirm hotel reservation (final step)
     */
    @Operation(
        summary = "Confirm hotel reservation",
        description = "Confirm a hotel reservation as the final step in the booking process",
        tags = {"Internal API", "Booking"}
    )
    @SecurityRequirement(name = "oauth2", scopes = {"admin", "internal"})
    @OpenApiResponses.StandardApiResponses
    @PostMapping("/confirm-reservation")
    public ResponseEntity<Map<String, Object>> confirmHotelReservation(@RequestBody Map<String, Object> request) {
        log.info("Hotel confirmation request: {}", request);
        
        String bookingId = (String) request.get("bookingId");
        String sagaId = (String) request.get("sagaId");
        String confirmationNumber = (String) request.get("confirmationNumber");
        
        // Mock implementation - in real scenario, this would:
        // 1. Convert temporary reservation to confirmed booking
        // 2. Generate vouchers
        // 3. Send confirmation to customer
        
        Map<String, Object> response = Map.of(
            "status", "success",
            "message", "Hotel reservation confirmed",
            "bookingId", bookingId,
            "sagaId", sagaId,
            "confirmationNumber", confirmationNumber,
            "voucherNumber", "VCH-" + bookingId
        );
        
        log.info("Hotel confirmation response: {}", response);
        return ResponseEntity.ok(response);
    }

    // === PUBLIC ENDPOINTS ===


    /**
     * Get popular origins for hotel search
     */
    @GetMapping("/storefront/origins")
    public ResponseEntity<Map<String, Object>> getPopularOrigins() {
        return ResponseEntity.ok(Map.of("origins", HotelStaticData.POPULAR_ORIGINS));
    }
    
    /**
     * Search hotel destinations
     * GET /hotels/storefront/destinations/search?q=hanoi
     */
    @GetMapping("/storefront/destinations/search")
    @Operation(summary = "Search hotel destinations", description = "Search for hotel destinations by city, district, or hotel name")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "Search results returned successfully",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "Invalid search query",
            content = @Content
        )
    })
    public ResponseEntity<SearchResponse<DestinationSearchResult>> searchDestinations(
            @Parameter(description = "Search query (city or hotel name)", example = "hanoi")
            @RequestParam(required = false) String q) {
        
        log.info("Hotel destination search request: q={}", q);
        
        try {
            // Validate input
            SearchValidation.ValidationResult validation = SearchValidation.validateSearchQuery(q);
            if (!validation.isValid()) {
                log.warn("Invalid search query: {}", validation.getErrorMessage());
                SearchResponse<DestinationSearchResult> errorResponse = SearchResponse.<DestinationSearchResult>builder()
                    .results(List.of())
                    .totalCount(0L)
                    .query(q != null ? q : "")
                    .metadata(Map.of("error", ErrorResponse.of("VALIDATION_ERROR", validation.getErrorMessage(), null, "/hotels/storefront/destinations/search")))
                    .build();
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Sanitize input
            String sanitizedQuery = SearchValidation.sanitizeSearchQuery(q);
            
            long startTime = System.currentTimeMillis();
            final List<DestinationSearchResult> destinations;
            
            if (sanitizedQuery != null && !sanitizedQuery.trim().isEmpty()) {
                String query = sanitizedQuery.trim();
                List<DestinationSearchResult> tempDestinations = new ArrayList<>();
                
                // Search in cities
                List<String> cities = hotelRepository.findDistinctCities();
                cities.stream()
                    .filter(city -> city.toLowerCase().contains(query.toLowerCase()))
                    .forEach(city -> tempDestinations.add(DestinationSearchResult.city(
                        city, "Vietnam", null
                    )));
                
                // Remove duplicates and limit results
                destinations = tempDestinations.stream()
                    .distinct()
                    .limit(20)
                    .collect(Collectors.toList());
            } else {
                // Return popular destinations when no query using static catalogue
                destinations = HotelStaticData.POPULAR_DESTINATIONS.stream()
                    .map(entry -> DestinationSearchResult.builder()
                        .name((String) entry.get("name"))
                        .type((String) entry.get("type"))
                        .country((String) entry.get("country"))
                        .category(((String) entry.get("type")).toLowerCase())
                        .iataCode((String) entry.get("code"))
                        .relevanceScore(1.0)
                        .build())
                    .collect(Collectors.toList());
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            SearchResponse<DestinationSearchResult> response = SearchResponse.<DestinationSearchResult>builder()
                .results(destinations)
                .totalCount((long) destinations.size())
                .query(q != null ? q : "")
                .executionTimeMs(executionTime)
                .metadata(Map.of("category", "hotel_destinations"))
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error searching hotel destinations", e);
            SearchResponse<DestinationSearchResult> errorResponse = SearchResponse.<DestinationSearchResult>builder()
                .results(List.of())
                .totalCount(0L)
                .query(q != null ? q : "")
                .metadata(Map.of("error", ErrorResponse.of("SEARCH_ERROR", "Hotel destination search failed", e.getMessage(), "/hotels/storefront/destinations/search")))
                .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get popular hotel destinations
     * GET /hotels/storefront/destinations/popular
     */
    @GetMapping("/storefront/destinations/popular")
    @Operation(summary = "Get popular hotel destinations", description = "Get list of popular hotel destinations")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "Popular destinations returned successfully",
            content = @Content(schema = @Schema(implementation = Map.class))
        )
    })
    public ResponseEntity<SearchResponse<DestinationSearchResult>> getPopularDestinations() {
        log.info("Popular hotel destinations request");
        
        try {
            long startTime = System.currentTimeMillis();
            
            List<DestinationSearchResult> destinations = List.of(
                DestinationSearchResult.city("Ho Chi Minh City", "Vietnam", null).toBuilder()
                    .description("Thành phố lớn nhất Việt Nam")
                    .build(),
                DestinationSearchResult.city("Hanoi", "Vietnam", null).toBuilder()
                    .description("Thủ đô của Việt Nam")
                    .build(),
                DestinationSearchResult.city("Da Nang", "Vietnam", null).toBuilder()
                    .description("Thành phố biển miền Trung")
                    .build(),
                DestinationSearchResult.city("Nha Trang", "Vietnam", null).toBuilder()
                    .description("Thành phố biển nổi tiếng")
                    .build(),
                DestinationSearchResult.builder()
                    .name("Phu Quoc")
                    .type("Đảo")
                    .country("Vietnam")
                    .category("island")
                    .description("Đảo ngọc Việt Nam")
                    .relevanceScore(1.0)
                    .build(),
                DestinationSearchResult.city("Da Lat", "Vietnam", null).toBuilder()
                    .description("Thành phố ngàn hoa")
                    .build(),
                DestinationSearchResult.city("Hue", "Vietnam", null).toBuilder()
                    .description("Cố đô Huế")
                    .build(),
                DestinationSearchResult.city("Hoi An", "Vietnam", null).toBuilder()
                    .description("Phố cổ Hội An")
                    .build()
            );
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            SearchResponse<DestinationSearchResult> response = SearchResponse.<DestinationSearchResult>builder()
                .results(destinations)
                .totalCount((long) destinations.size())
                .query("popular")
                .executionTimeMs(executionTime)
                .metadata(Map.of("category", "popular_hotel_destinations"))
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting popular hotel destinations", e);
            SearchResponse<DestinationSearchResult> errorResponse = SearchResponse.<DestinationSearchResult>builder()
                .results(List.of())
                .totalCount(0L)
                .query("popular")
                .metadata(Map.of("error", ErrorResponse.of("POPULAR_DESTINATIONS_ERROR", "Failed to get popular hotel destinations", e.getMessage(), "/hotels/storefront/destinations/popular")))
                .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

}
