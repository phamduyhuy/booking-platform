package com.pdh.flight.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdh.common.config.OpenApiResponses;
import com.pdh.flight.dto.FlightBookingDetailsDto;
import com.pdh.flight.dto.response.FlightSearchResultDto;
import com.pdh.flight.dto.response.FlightFareDetailsResponse;
import com.pdh.flight.model.Flight;
import com.pdh.flight.model.FlightSchedule;
import com.pdh.flight.model.FlightFare;
import com.pdh.flight.model.enums.FareClass;
import com.pdh.flight.repository.FlightRepository;
import com.pdh.flight.repository.FlightScheduleRepository;
import com.pdh.flight.repository.FlightFareRepository;
import com.pdh.flight.service.FlightService;
import com.pdh.flight.service.FlightSearchService;
import com.pdh.flight.service.CityDataService;
import com.pdh.flight.service.CityMappingService;
import com.pdh.flight.util.FlightResponseAssembler;
import com.pdh.flight.util.FlightSearchResponseBuilder;
import com.pdh.flight.util.FlightStaticData;
import com.pdh.common.validation.SearchValidation;
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
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Flight Controller
 * 
 * Handles all flight-related API endpoints including search, details, and reservations.
 * This controller provides both public APIs for storefront and internal APIs for booking integration.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Flights", description = "Flight search and booking operations")
@SecurityRequirement(name = "oauth2")
public class FlightController {

    private final FlightRepository flightRepository;
    private final FlightService flightService;
    private final FlightSearchService flightSearchService;
    private final ObjectMapper objectMapper;
    private final CityDataService cityDataService;
    private final CityMappingService cityMappingService;
    private final FlightScheduleRepository flightScheduleRepository;
    private final FlightFareRepository flightFareRepository;
    private final FlightResponseAssembler flightResponseAssembler;


    /**
     * Search flights for storefront
     * GET /flights/storefront/search?origin=HAN&destination=SGN&departureDate=2024-02-15&passengers=1&seatClass=ECONOMY
     */

    @GetMapping("/storefront/search")
    @McpTool(name = "search_flights", description = "Search flights with filters for origin, destination, dates, airline, seat class, price range, and duration.")
    public ResponseEntity<Map<String, Object>> searchFlights(
            @McpToolParam(description = "Origin airport, city, or IATA code", required = false)
            @RequestParam(required = false) String origin,
            @McpToolParam(description = "Destination airport, city, or IATA code", required = false)
            @RequestParam(required = false) String destination,
            @McpToolParam(description = "Airline name filter", required = false)
            @RequestParam(required = false) String airlineName,
            @McpToolParam(description = "Airline IATA code filter", required = false)
            @RequestParam(required = false) String airlineCode,
            @McpToolParam(description = "Departure date in YYYY-MM-DD format", required = false)
            @RequestParam(required = false) String departureDate,
            @McpToolParam(description = "Return date in YYYY-MM-DD format (for round-trip)",  required = false)
            @RequestParam(required = false) String returnDate,
            @McpToolParam(description = "Number of passengers")
            @RequestParam(defaultValue = "1") Integer passengers,
            @McpToolParam(description = "Seat class")
            @RequestParam(defaultValue = "ECONOMY") String seatClass,
            @McpToolParam(description = "Minimum total fare")
            @RequestParam(required = false) BigDecimal minPrice,
            @McpToolParam(description = "Maximum total fare")
            @RequestParam(required = false) BigDecimal maxPrice,
            @McpToolParam(description = "Minimum duration in minutes")
            @RequestParam(required = false) Integer minDuration,
            @McpToolParam(description = "Maximum duration in minutes")
            @RequestParam(required = false) Integer maxDuration,
            @McpToolParam(description = "Sort by criteria (price, duration, departure, arrival")
            @RequestParam(defaultValue = "departure") String sortBy,
            @McpToolParam(description = "Filter by airline ID")
            @RequestParam(required = false) Long airlineId,
            @McpToolParam(description = "Filter by departure airport ID")
            @RequestParam(required = false) Long departureAirportId,
            @McpToolParam(description = "Page number (1-based)")
            @RequestParam(defaultValue = "1") Integer page,
            @McpToolParam(description = "Number of results per page")
            @RequestParam(defaultValue = "20") Integer limit) {

        log.info("Flight search request: origin={}, destination={}, departureDate={}, airlineName={}, minPrice={}, maxPrice={}, seatClass={}",
                origin, destination, departureDate, airlineName, minPrice, maxPrice, seatClass);
        
        Integer effectivePage = (page != null && page > 0) ? page : 1;
        Integer effectiveLimit = (limit != null && limit > 0) ? limit : 20;

        try {
            SearchValidation.ValidationResult originValidation = SearchValidation.validateSearchQuery(origin);
            SearchValidation.ValidationResult destinationValidation = SearchValidation.validateSearchQuery(destination);
            SearchValidation.ValidationResult airlineValidation = SearchValidation.validateSearchQuery(airlineName);

            if (!originValidation.isValid()) {
                log.debug("Origin validation failed: {}", originValidation.getErrorMessage());
                return ResponseEntity.badRequest().body(FlightSearchResponseBuilder.validationError(originValidation.getErrorMessage(), effectivePage, effectiveLimit));
            }

            if (!destinationValidation.isValid()) {
                log.debug("Destination validation failed: {}", destinationValidation.getErrorMessage());
                return ResponseEntity.badRequest().body(FlightSearchResponseBuilder.validationError(destinationValidation.getErrorMessage(), effectivePage, effectiveLimit));
            }

            if (!airlineValidation.isValid()) {
                log.debug("Airline name validation failed: {}", airlineValidation.getErrorMessage());
                return ResponseEntity.badRequest().body(FlightSearchResponseBuilder.validationError(airlineValidation.getErrorMessage(), effectivePage, effectiveLimit));
            }

            if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
                log.debug("Price range validation failed: minPrice {} > maxPrice {}", minPrice, maxPrice);
                return ResponseEntity.badRequest().body(FlightSearchResponseBuilder.validationError("minPrice cannot be greater than maxPrice", effectivePage, effectiveLimit));
            }

            if (minDuration != null && maxDuration != null && minDuration > maxDuration) {
                log.debug("Duration range validation failed: minDuration {} > maxDuration {}", minDuration, maxDuration);
                return ResponseEntity.badRequest().body(FlightSearchResponseBuilder.validationError("minDuration cannot be greater than maxDuration", effectivePage, effectiveLimit));
            }

            if (StringUtils.hasText(airlineCode)) {
                SearchValidation.ValidationResult codeValidation = SearchValidation.validateSearchQuery(airlineCode);
                if (!codeValidation.isValid()) {
                    log.debug("Airline code validation failed: {}", codeValidation.getErrorMessage());
                    return ResponseEntity.badRequest().body(FlightSearchResponseBuilder.validationError(codeValidation.getErrorMessage(), effectivePage, effectiveLimit));
                }
            }

            String sanitizedOrigin = SearchValidation.sanitizeSearchQuery(origin);
            String sanitizedDestination = SearchValidation.sanitizeSearchQuery(destination);
            String sanitizedAirlineName = SearchValidation.sanitizeSearchQuery(airlineName);
            String sanitizedAirlineCode = StringUtils.hasText(airlineCode) ? airlineCode.trim().toUpperCase() : null;

            String resolvedOrigin = resolveCityToIataCode(sanitizedOrigin);
            String resolvedDestination = resolveCityToIataCode(sanitizedDestination);

            List<String> originTerms = buildLocationTerms(sanitizedOrigin, resolvedOrigin);
            List<String> destinationTerms = buildLocationTerms(sanitizedDestination, resolvedDestination);

            boolean hasSearchCriteria = StringUtils.hasText(resolvedOrigin)
                || StringUtils.hasText(resolvedDestination)
                || StringUtils.hasText(departureDate)
                || StringUtils.hasText(sanitizedAirlineName)
                || StringUtils.hasText(sanitizedAirlineCode)
                || minPrice != null || maxPrice != null
                || minDuration != null || maxDuration != null;

            Pageable pageable = PageRequest.of(Math.max(effectivePage - 1, 0), effectiveLimit);

            if (!hasSearchCriteria) {
                Page<Flight> flightPage = flightRepository.findAll(pageable);
                List<Map<String, Object>> flights = flightPage.getContent().stream()
                    .map(flightResponseAssembler::toFlightDetailMap)
                    .collect(Collectors.toList());

                Map<String, Object> response = Map.of(
                    "flights", flights,
                    "popularDestinations", FlightStaticData.POPULAR_DESTINATIONS,
                    "totalCount", flightPage.getTotalElements(),
                    "page", effectivePage,
                    "limit", effectiveLimit,
                    "hasMore", flightPage.hasNext(),
                    "filters", Map.of("applied", Map.of())
                );

                return ResponseEntity.ok(response);
            }

            LocalDate depDate;
            try {
                depDate = LocalDate.parse(departureDate);
            } catch (DateTimeParseException | NullPointerException e) {
                log.debug("Departure date validation failed: invalid format");
                return ResponseEntity.badRequest().body(FlightSearchResponseBuilder.validationError("Invalid departure date format. Expected YYYY-MM-DD", effectivePage, effectiveLimit));
            }

            LocalDate retDate = null;
            if (StringUtils.hasText(returnDate)) {
                try {
                    retDate = LocalDate.parse(returnDate);
                } catch (DateTimeParseException e) {
                    log.debug("Return date validation failed: invalid format");
                    return ResponseEntity.badRequest().body(FlightSearchResponseBuilder.validationError("Invalid return date format. Expected YYYY-MM-DD", effectivePage, effectiveLimit));
                }
            }

            FareClass fareClass;
            try {
                fareClass = FareClass.valueOf(seatClass.toUpperCase());
            } catch (Exception e) {
                log.debug("Seat class validation failed: invalid value");
                return ResponseEntity.badRequest().body(FlightSearchResponseBuilder.validationError("Invalid seat class. Valid values are: ECONOMY, BUSINESS, FIRST", effectivePage, effectiveLimit));
            }

            Page<FlightSearchResultDto> flightPage = flightSearchService.searchFlights(
                resolvedOrigin,
                resolvedDestination,
                originTerms,
                destinationTerms,
                depDate,
                retDate,
                passengers,
                fareClass,
                pageable,
                sortBy,
                airlineId,
                departureAirportId,
                sanitizedAirlineName,
                sanitizedAirlineCode,
                minPrice,
                maxPrice,
                minDuration,
                maxDuration
            );

            List<Map<String, Object>> flights = flightPage.getContent().stream()
                .map(flightResponseAssembler::toSearchResultMap)
                .collect(Collectors.toList());

            Map<String, Object> appliedFilters = new LinkedHashMap<>();
            appliedFilters.put("origin", sanitizedOrigin);
            appliedFilters.put("destination", sanitizedDestination);
            appliedFilters.put("airlineName", sanitizedAirlineName);
            appliedFilters.put("airlineCode", sanitizedAirlineCode);
            appliedFilters.put("minPrice", minPrice);
            appliedFilters.put("maxPrice", maxPrice);
            appliedFilters.put("minDuration", minDuration);
            appliedFilters.put("maxDuration", maxDuration);
            appliedFilters.put("departureDate", depDate);
            appliedFilters.put("returnDate", retDate);

            Map<String, Object> response = Map.of(
                "flights", flights,
                "totalCount", flightPage.getTotalElements(),
                "page", effectivePage,
                "limit", effectiveLimit,
                "hasMore", flightPage.hasNext(),
                "filters", Map.of("applied", appliedFilters)
            );

            log.info("Found {} flights for search criteria", flights.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error searching flights", e);
            return ResponseEntity.ok(FlightSearchResponseBuilder.failureResponse(e.getMessage(), effectivePage, effectiveLimit));
        }
    }


    /**
     * Get flight details by ID for storefront
     */
    @Operation(
        summary = "Get flight details",
        description = "Retrieve detailed information about a specific flight including schedule, pricing, and availability",
        tags = {"Public API"}
    )
    @OpenApiResponses.StandardApiResponsesWithNotFound
    @GetMapping("/storefront/{flightId}")
    public ResponseEntity<Map<String, Object>> getStorefrontFlightDetails(
            @Parameter(description = "Flight ID", required = true, example = "1")
            @PathVariable Long flightId) {
        log.info("Flight details request for ID: {}", flightId);

        try {
            Optional<Flight> flightOpt = flightRepository.findById(flightId);
            if (flightOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Flight flight = flightOpt.get();
            Map<String, Object> response = flightResponseAssembler.toFlightDetailMap(flight);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting flight details", e);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
        summary = "Get fare details for booked flight",
        description = "Retrieve fare and schedule information for a specific flight booking context",
        tags = {"Public API"}
    )
    @OpenApiResponses.StandardApiResponsesWithNotFound
    @GetMapping("/storefront/{flightId}/fare-details")
    public ResponseEntity<FlightFareDetailsResponse> getFareDetails(
            @PathVariable Long flightId,
            @RequestParam(name = "seatClass", required = false) String seatClass,
            @RequestParam(name = "scheduleId", required = false) UUID scheduleId,
            @RequestParam(name = "fareId", required = false) UUID fareId) {

        log.info("Fare details request flightId={}, seatClass={}, scheduleId={}, fareId={}",
                flightId, seatClass, scheduleId, fareId);

        try {
            FlightSchedule schedule = null;
            FlightFare fare = null;

            FareClass requestedFareClass = null;
            if (seatClass != null && !seatClass.isBlank()) {
                try {
                    requestedFareClass = FareClass.valueOf(seatClass.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // leave null so we can fall back gracefully
                }
            }

            UUID resolvedScheduleId = scheduleId;

            if (fareId != null) {
                Optional<FlightFare> fareOptional = flightFareRepository.findById(fareId);
                if (fareOptional.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }

                FlightFare resolvedFare = fareOptional.get();
                if (resolvedFare.isDeleted()) {
                    return ResponseEntity.notFound().build();
                }

                if (resolvedScheduleId != null && !resolvedFare.getScheduleId().equals(resolvedScheduleId)) {
                    log.warn("Requested fare {} does not belong to schedule {}", fareId, resolvedScheduleId);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }

                resolvedScheduleId = resolvedFare.getScheduleId();
                fare = resolvedFare;

                if (requestedFareClass != null && fare.getFareClass() != null && fare.getFareClass() != requestedFareClass) {
                    log.debug("Seat class {} requested but fare {} is {}. Using fare's class.",
                            requestedFareClass, fareId, fare.getFareClass());
                }

                if (fare.getFareClass() != null) {
                    requestedFareClass = fare.getFareClass();
                }
            }

            if (resolvedScheduleId != null) {
                schedule = flightScheduleRepository.findById(resolvedScheduleId).orElse(null);
                if (schedule == null || schedule.isDeleted() || !schedule.getFlight().getFlightId().equals(flightId)) {
                    log.warn("Schedule {} not found or does not belong to flight {}", resolvedScheduleId, flightId);
                    return ResponseEntity.notFound().build();
                }
            }

            if (schedule == null) {
                List<FlightSchedule> schedules = flightScheduleRepository.findByFlightId(flightId).stream()
                        .filter(fs -> !fs.isDeleted())
                        .sorted(Comparator.comparing(FlightSchedule::getDepartureTime))
                        .collect(Collectors.toList());

                if (schedules.isEmpty()) {
                    log.info("No schedules found for flight {}", flightId);
                    return ResponseEntity.notFound().build();
                }

                if (requestedFareClass != null) {
                    for (FlightSchedule candidate : schedules) {
                        FlightFare candidateFare = flightFareRepository.findByScheduleIdAndFareClass(candidate.getScheduleId(),
                                requestedFareClass);
                        if (candidateFare != null && !candidateFare.isDeleted()) {
                            schedule = candidate;
                            fare = candidateFare;
                            break;
                        }
                    }

                    if (schedule == null) {
                        log.info("No fares found for seatClass {} on flight {}", requestedFareClass, flightId);
                        return ResponseEntity.notFound().build();
                    }
                }
                else {
                    for (FlightSchedule candidate : schedules) {
                        List<FlightFare> scheduleFares = flightFareRepository.findByScheduleId(candidate.getScheduleId());
                        fare = scheduleFares.stream()
                                .filter(ff -> !ff.isDeleted())
                                .min(Comparator.comparing(FlightFare::getPrice))
                                .orElse(null);
                        if (fare != null) {
                            schedule = candidate;
                            break;
                        }
                    }

                    if (schedule == null) {
                        log.info("No fares available for flight {}", flightId);
                        return ResponseEntity.notFound().build();
                    }
                }
            }

            if (schedule != null && fare == null) {
                if (requestedFareClass != null) {
                    fare = flightFareRepository.findByScheduleIdAndFareClass(schedule.getScheduleId(), requestedFareClass);
                    if (fare == null) {
                        log.info("No fare found for seatClass {} on schedule {}", requestedFareClass, schedule.getScheduleId());
                        return ResponseEntity.notFound().build();
                    }
                }
                else {
                    List<FlightFare> scheduleFares = flightFareRepository.findByScheduleId(schedule.getScheduleId());
                    fare = scheduleFares.stream()
                            .filter(ff -> !ff.isDeleted())
                            .min(Comparator.comparing(FlightFare::getPrice))
                            .orElse(null);
                }
            }

            if (fare == null || fare.isDeleted()) {
                return ResponseEntity.notFound().build();
            }

            Flight flight = flightRepository.findById(flightId)
                    .filter(f -> !f.isDeleted())
                    .orElse(null);

            FlightFareDetailsResponse response = FlightFareDetailsResponse.builder()
                    .flightId(flightId)
                    .fareId(fare.getFareId())
                    .scheduleId(schedule.getScheduleId())
                    .seatClass(fare.getFareClass() != null ? fare.getFareClass().name() : null)
                    .price(fare.getPrice())
                    .currency("VND")
                    .availableSeats(fare.getAvailableSeats())
                    .departureTime(schedule.getDepartureTime().toString())
                    .arrivalTime(schedule.getArrivalTime().toString())
                    .flightNumber(flight != null ? flight.getFlightNumber() : null)
                    .airline(flight != null && flight.getAirline() != null ? flight.getAirline().getName() : null)
                    .originAirport(flight != null && flight.getDepartureAirport() != null ? flight.getDepartureAirport().getIataCode() : null)
                    .destinationAirport(flight != null && flight.getArrivalAirport() != null ? flight.getArrivalAirport().getIataCode() : null)
                    .aircraftType(schedule.getAircraftType())
                    .originLatitude(flight != null && flight.getDepartureAirport() != null ? flight.getDepartureAirport().getLatitude() : null)
                    .originLongitude(flight != null && flight.getDepartureAirport() != null ? flight.getDepartureAirport().getLongitude() : null)
                    .destinationLatitude(flight != null && flight.getArrivalAirport() != null ? flight.getArrivalAirport().getLatitude() : null)
                    .destinationLongitude(flight != null && flight.getArrivalAirport() != null ? flight.getArrivalAirport().getLongitude() : null)
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | DateTimeParseException e) {
            log.warn("Invalid fare detail request parameters", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error retrieving fare details", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get flight ID by schedule ID for storefront
     * Used by CDC listeners to trace back from schedule changes to flight
     */
    @Operation(
        summary = "Get flight ID by schedule ID",
        description = "Retrieve the flight ID associated with a specific schedule ID",
        tags = {"Public API"}
    )
    @OpenApiResponses.StandardApiResponsesWithNotFound
    @GetMapping("/storefront/schedule/{scheduleId}/flight-id")
    public ResponseEntity<Map<String, Object>> getFlightIdByScheduleId(
            @Parameter(description = "Schedule ID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID scheduleId) {
        log.info("Flight ID request by schedule ID: {}", scheduleId);

        try {
            FlightSchedule schedule = flightScheduleRepository.findById(scheduleId).orElse(null);
            if (schedule == null || schedule.isDeleted()) {
                return ResponseEntity.notFound().build();
            }

            Long flightId = schedule.getFlightId();
            if (flightId == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = Map.of(
                "flightId", flightId,
                "scheduleId", scheduleId
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting flight ID by schedule ID", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get flight ID by fare ID for storefront
     * Used by CDC listeners to trace back from fare changes to flight
     */
    @Operation(
        summary = "Get flight ID by fare ID",
        description = "Retrieve the flight ID associated with a specific fare ID through schedule",
        tags = {"Public API"}
    )
    @OpenApiResponses.StandardApiResponsesWithNotFound
    @GetMapping("/storefront/fare/{fareId}/flight-id")
    public ResponseEntity<Map<String, Object>> getFlightIdByFareId(
            @Parameter(description = "Fare ID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID fareId) {
        log.info("Flight ID request by fare ID: {}", fareId);

        try {
            // First get the fare to find the schedule ID
            FlightFare fare = flightFareRepository.findById(fareId).orElse(null);
            if (fare == null || fare.isDeleted()) {
                return ResponseEntity.notFound().build();
            }

            UUID scheduleId = fare.getScheduleId();
            if (scheduleId == null) {
                return ResponseEntity.notFound().build();
            }

            // Then get the schedule to find the flight ID
            FlightSchedule schedule = flightScheduleRepository.findById(scheduleId).orElse(null);
            if (schedule == null || schedule.isDeleted()) {
                return ResponseEntity.notFound().build();
            }

            Long flightId = schedule.getFlightId();
            if (flightId == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = Map.of(
                "flightId", flightId,
                "fareId", fareId,
                "scheduleId", scheduleId
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting flight ID by fare ID", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get popular destinations for storefront
     */
    @Operation(
        summary = "Get popular destinations",
        description = "Retrieve a list of popular flight destinations with average pricing information",
        tags = {"Public API"}
    )
    @OpenApiResponses.StandardApiResponses
    @GetMapping("/storefront/popular-destinations")
    public ResponseEntity<List<Map<String, Object>>> getPopularDestinations(
            @Parameter(description = "Origin airport code to filter destinations", example = "HAN")
            @RequestParam(required = false) String origin) {

        log.info("Popular destinations request with origin: {}", origin);

        try {
            // For now, return static popular destinations
            // In production, this would be based on booking statistics
            return ResponseEntity.ok(FlightStaticData.POPULAR_DESTINATIONS);
        } catch (Exception e) {
            log.error("Error getting popular destinations", e);
            return ResponseEntity.ok(List.of());
        }
    }


    // === BOOKING INTEGRATION ENDPOINTS ===

    /**
     * Reserve flight for booking (called by Booking Service)
     * Enhanced to handle detailed product information
     */
    @Operation(
        summary = "Reserve flight",
        description = "Create a flight reservation as part of the booking process. Supports both detailed product information and legacy mode.",
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
    public ResponseEntity<Map<String, Object>> reserveFlight(
            @Parameter(description = "Reservation request containing booking details", required = true)
            @RequestBody Map<String, Object> request) {
        log.info("Flight reservation request: {}", request);

        try {
            String bookingId = (String) request.get("bookingId");
            String sagaId = (String) request.get("sagaId");

            // Check if detailed flight information is provided
            Object flightDetailsObj = request.get("flightDetails");

            if (flightDetailsObj != null) {
                // Handle detailed reservation with product information
                FlightBookingDetailsDto flightDetails = objectMapper.convertValue(flightDetailsObj, FlightBookingDetailsDto.class);

                // Call enhanced flight service method
                flightService.reserveFlight(UUID.fromString(bookingId), sagaId, flightDetails);

                Map<String, Object> response = Map.of(
                    "status", "success",
                    "message", "Flight reservation created with detailed product information",
                    "reservationId", "FLT-" + bookingId.substring(0, 8),
                    "bookingId", bookingId,
                    "sagaId", sagaId,
                    "flightId", flightDetails.getFlightId(),
                    "passengers", flightDetails.getPassengerCount(),
                    "seatClass", flightDetails.getSeatClass()
                );

                log.info("Detailed flight reservation response: {}", response);
                return ResponseEntity.ok(response);

            } else {
                // Legacy support - basic reservation without detailed product info
                flightService.reserveFlight(UUID.fromString(bookingId));

                Map<String, Object> response = Map.of(
                    "status", "success",
                    "message", "Flight reservation created (legacy mode)",
                    "reservationId", "FLT-" + bookingId,
                    "bookingId", bookingId,
                    "sagaId", sagaId
                );

                log.info("Legacy flight reservation response: {}", response);
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("Error processing flight reservation: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = Map.of(
                "status", "error",
                "message", "Flight reservation failed: " + e.getMessage(),
                "bookingId", request.get("bookingId"),
                "sagaId", request.get("sagaId")
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Cancel flight reservation (compensation)
     */
    @Operation(
        summary = "Cancel flight reservation",
        description = "Cancel a flight reservation as part of compensation logic in the booking saga",
        tags = {"Internal API", "Booking"}
    )
    @SecurityRequirement(name = "oauth2", scopes = {"admin", "internal"})
    @OpenApiResponses.StandardApiResponses
    @PostMapping("/cancel-reservation")
    public ResponseEntity<Map<String, Object>> cancelFlightReservation(@RequestBody Map<String, Object> request) {

        
        // TODO


        return ResponseEntity.ok(Map.of());
    }
    
    /**
     * Confirm flight reservation (final step)
     */
    @Operation(
        summary = "Confirm flight reservation",
        description = "Confirm a flight reservation as the final step in the booking process",
        tags = {"Internal API", "Booking"}
    )
    @SecurityRequirement(name = "oauth2", scopes = {"admin", "internal"})
    @OpenApiResponses.StandardApiResponses
    @PostMapping("/confirm-reservation")
    public ResponseEntity<Map<String, Object>> confirmFlightReservation(@RequestBody Map<String, Object> request) {
        log.info("Flight confirmation request: {}", request);
        // TODO

        

        return ResponseEntity.ok(Map.of());
    }

    // === HELPER METHODS ===

    /**
     * Get popular destinations data
     */
    private List<Map<String, Object>> getPopularDestinationsData() {
        // For now, return static popular destinations
        // In production, this would be based on booking statistics
        return List.of(
            Map.of(
                "code", "SGN",
                "name", "Tan Son Nhat International Airport",
                "city", "Ho Chi Minh City",
                "country", "Vietnam",
                "image", "/destinations/hcmc.jpg",
                "averagePrice", 2200000,
                "currency", "VND"
            ),
            Map.of(
                "code", "DAD",
                "name", "Da Nang International Airport",
                "city", "Da Nang",
                "country", "Vietnam",
                "image", "/destinations/danang.jpg",
                "averagePrice", 1800000,
                "currency", "VND"
            ),
            Map.of(
                "code", "HAN",
                "name", "Noi Bai International Airport",
                "city", "Hanoi",
                "country", "Vietnam",
                "image", "/destinations/hanoi.jpg",
                "averagePrice", 2500000,
                "currency", "VND"
            )
        );
    }



    private ZonedDateTime parseDepartureDateTime(String departureDateTime) {
        try {
            return ZonedDateTime.parse(departureDateTime);
        } catch (DateTimeParseException ex) {
            LocalDateTime localDateTime = LocalDateTime.parse(departureDateTime);
            return localDateTime.atZone(ZoneOffset.UTC);
        }
    }

    /**
     * Resolve city name to IATA code using the city mapping service
     * @param cityOrIataCode the city name or IATA code
     * @return resolved IATA code or original input if already IATA code
     */
    private String resolveCityToIataCode(String cityOrIataCode) {
        if (cityOrIataCode == null || cityOrIataCode.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = cityOrIataCode.trim();
        // If already an IATA code, return as is
        if (trimmed.length() == 3 && trimmed.equals(trimmed.toUpperCase())) {
            return trimmed;
        }

        
        // Try to resolve city name to IATA code
        List<String> iataCodes = cityMappingService.getIataCodesForCity(trimmed);
        if (!iataCodes.isEmpty()) {
            log.debug("Resolved city '{}' to IATA code '{}'", trimmed, iataCodes.get(0));
            return iataCodes.get(0);
        }
        //Try to resolve city name to IATA code
        
        // If no resolution found, return original input for flexible search
        log.debug("Could not resolve city '{}' to IATA code, using original input", trimmed);
        return trimmed;
    }

    private List<String> buildLocationTerms(String rawValue, String resolvedValue) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();

        if (StringUtils.hasText(rawValue)) {
            String normalized = rawValue.trim().replaceAll("\\s+", " ");
            if (StringUtils.hasText(normalized)) {
                terms.add(normalized);

                for (String part : normalized.split(",")) {
                    String trimmed = part.trim();
                    if (StringUtils.hasText(trimmed)) {
                        terms.add(trimmed);
                    }
                }
            }
        }

        if (StringUtils.hasText(resolvedValue)) {
            String normalizedResolved = resolvedValue.trim();
            if (StringUtils.hasText(normalizedResolved)) {
                terms.add(normalizedResolved);
                if (normalizedResolved.length() <= 3) {
                    terms.add(normalizedResolved.toUpperCase());
                }
            }
        }

        return new ArrayList<>(terms);
    }
    
}
