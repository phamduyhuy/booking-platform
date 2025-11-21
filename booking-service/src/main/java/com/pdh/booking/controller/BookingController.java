package com.pdh.booking.controller;

import com.pdh.booking.command.CreateBookingCommand;
import com.pdh.booking.model.dto.request.CreateBookingRequestDto;
import com.pdh.booking.model.dto.request.StorefrontCreateBookingRequestDto;
import com.pdh.booking.model.dto.response.BookingResponseDto;
import com.pdh.booking.model.dto.response.StorefrontBookingResponseDto;
import com.pdh.booking.model.dto.response.BookingStatusResponseDto;
import com.pdh.booking.model.dto.response.BookingHistoryResponseDto;
import com.pdh.booking.model.dto.response.BookingHistoryItemDto;
import com.pdh.booking.mapper.BookingDtoMapper;
import com.pdh.booking.model.Booking;
import com.pdh.booking.service.BookingCqrsService;
import com.pdh.booking.service.BookingService;
import com.pdh.booking.service.ProductDetailsService;
import com.pdh.booking.service.StorefrontProductDetailsAssembler;
import com.pdh.booking.saga.BookingSagaOrchestrator;
import com.pdh.booking.model.enums.BookingStatus;
import com.pdh.common.config.OpenApiResponses;
import com.pdh.common.utils.AuthenticationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

/**
 * CQRS-based Booking Controller
 * 
 * Handles all booking-related operations using Command Query Responsibility
 * Segregation (CQRS).
 * Uses saga orchestration for booking creation and direct queries for data
 * retrieval.
 * REST calls are only used for backoffice management purposes.
 */
@RestController

@Slf4j
@Tag(name = "Bookings", description = "Booking management and orchestration operations")
@SecurityRequirement(name = "oauth2")
public class BookingController {

    private final BookingCqrsService bookingCqrsService;
    private final BookingDtoMapper bookingDtoMapper;
    private final ObjectMapper objectMapper;
    private final BookingService bookingService;
    private final BookingSagaOrchestrator bookingSagaOrchestrator;
    private final StorefrontProductDetailsAssembler storefrontProductDetailsAssembler;
    private final ProductDetailsService productDetailsService;
    private final BeanOutputConverter<StorefrontCreateBookingRequestDto> beanOutputConverter;
    private final String REQUEST_BOOKING_SCHEMA;
    public BookingController(
            BookingCqrsService bookingCqrsService,
            BookingDtoMapper bookingDtoMapper,
            ObjectMapper objectMapper,
            BookingService bookingService,
            BookingSagaOrchestrator bookingSagaOrchestrator,
            StorefrontProductDetailsAssembler storefrontProductDetailsAssembler,
            ProductDetailsService productDetailsService) {
        this.bookingCqrsService = bookingCqrsService;
        this.bookingDtoMapper = bookingDtoMapper;
        this.objectMapper = objectMapper;
        this.bookingService = bookingService;
        this.bookingSagaOrchestrator = bookingSagaOrchestrator;
        this.storefrontProductDetailsAssembler = storefrontProductDetailsAssembler;
        this.productDetailsService = productDetailsService;
        this.beanOutputConverter = new BeanOutputConverter<>( StorefrontCreateBookingRequestDto.class);
        this.REQUEST_BOOKING_SCHEMA = this.beanOutputConverter.getJsonSchema();
        System.out.println(REQUEST_BOOKING_SCHEMA);
    }



    @Operation(summary = "Confirm booking (Storefront)", description = "Mark booking as confirmed after successful payment", tags = {
            "Public API", "Status Tracking" })
    @SecurityRequirement(name = "oauth2", scopes = { "customer" })
    @PostMapping("/storefront/{bookingId}/confirm")
    public ResponseEntity<BookingStatusResponseDto> confirmBooking(
            @PathVariable UUID bookingId) {
        try {
            Booking booking = bookingService.confirmBooking(bookingId);

            BookingStatusResponseDto response = BookingStatusResponseDto.builder()
                    .bookingId(booking.getBookingId().toString())
                    .bookingReference(booking.getBookingReference())
                    .status(booking.getStatus())
                    .lastUpdated(booking.getUpdatedAt().toString())
                    .message("Booking confirmed successfully!")
                    .reservationLockedAt(
                            booking.getReservationLockedAt() != null ? booking.getReservationLockedAt().toString()
                                    : null)
                    .reservationExpiresAt(
                            booking.getReservationExpiresAt() != null ? booking.getReservationExpiresAt().toString()
                                    : null)
                    .build();

            response.setEstimatedCompletion(null);
            response.setProgressPercentage(100);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error confirming booking {}", bookingId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Initiate payment for booking (Storefront)", description = "Mark the booking as ready for payment once the customer confirms the intent", tags = {
            "Public API", "Status Tracking" })
    @SecurityRequirement(name = "oauth2", scopes = { "customer" })
    @PostMapping("/storefront/{bookingId}/payment/initiate")
    public ResponseEntity<BookingStatusResponseDto> initiatePayment(
            @PathVariable UUID bookingId,
            @RequestBody(required = false) Map<String, String> payload) {
        UUID currentUser = AuthenticationUtils.getCurrentUserIdFromContext();

        Booking booking = bookingService.findByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (booking.getUserId() != null && !booking.getUserId().equals(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        bookingSagaOrchestrator.markPaymentInitiated(bookingId);

        Booking refreshed = bookingService.findByBookingId(bookingId).orElse(booking);

        BookingStatusResponseDto response = BookingStatusResponseDto.builder()
                .bookingId(refreshed.getBookingId().toString())
                .bookingReference(refreshed.getBookingReference())
                .status(refreshed.getStatus())
                .lastUpdated(refreshed.getUpdatedAt() != null ? refreshed.getUpdatedAt().toString() : null)
                .message("Payment initiation acknowledged. Please complete payment to confirm booking.")
                .reservationLockedAt(
                        refreshed.getReservationLockedAt() != null ? refreshed.getReservationLockedAt().toString()
                                : null)
                .reservationExpiresAt(
                        refreshed.getReservationExpiresAt() != null ? refreshed.getReservationExpiresAt().toString()
                                : null)
                .build();
        response.setProgressPercentage(null);
        response.setEstimatedCompletion(null);

        return ResponseEntity.accepted().body(response);
    }

    @Operation(summary = "Get booking history", description = "Retrieve paginated booking history for the authenticated storefront user", tags = {
            "Public API", "Status Tracking" })
    @SecurityRequirement(name = "oauth2", scopes = { "customer" })
    @GetMapping("/storefront/history")
    public ResponseEntity<BookingHistoryResponseDto> getBookingHistory(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        try {
            UUID userId = AuthenticationUtils.getCurrentUserIdFromContext();

            int pageIndex = Math.max(page, 0);
            int pageSize = Math.max(1, Math.min(size, 50));
            Pageable pageable = PageRequest.of(pageIndex, pageSize);

            Page<BookingHistoryItemDto> historyPage = bookingService.getBookingHistory(userId, pageable);

            BookingHistoryResponseDto response = BookingHistoryResponseDto.builder()
                    .items(historyPage.getContent())
                    .page(historyPage.getNumber())
                    .size(historyPage.getSize())
                    .totalElements(historyPage.getTotalElements())
                    .totalPages(historyPage.getTotalPages())
                    .hasNext(historyPage.hasNext())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error retrieving booking history", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get booking by saga ID (Backoffice/Admin)
     */
    @Operation(summary = "Get booking by saga ID", description = "Retrieve booking details using the saga ID for administrative purposes", tags = {
            "Admin API" })
    @SecurityRequirement(name = "oauth2", scopes = { "admin" })
    @OpenApiResponses.StandardApiResponsesWithNotFound
    @GetMapping("/saga/{sagaId}")
    public ResponseEntity<BookingResponseDto> getBookingBySagaId(
            @Parameter(description = "Saga ID", required = true, example = "saga-12345") @PathVariable String sagaId) {
        try {
            String userId = AuthenticationUtils.extractUserId();
            Optional<Booking> booking = bookingCqrsService.getBookingBySagaId(sagaId, userId);

            return booking.map(b -> {
                BookingResponseDto response = bookingDtoMapper.toResponseDto(b);
                return ResponseEntity.ok(response);
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error getting booking by saga ID: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // === STOREFRONT ENDPOINTS ===

    /**
     * Create a new booking using CQRS and saga orchestration (Storefront)
     */
    @Operation(summary = "Create booking (Customer)", description = "Create a new booking from the customer storefront using saga orchestration", tags = {
            "Public API", "Booking Creation" })
    @SecurityRequirement(name = "oauth2", scopes = { "customer" })
    @OpenApiResponses.CreationApiResponses
    @PostMapping("/storefront")
    public ResponseEntity<StorefrontBookingResponseDto> createStorefrontBooking(
            @Parameter(description = "Storefront booking creation request", required = true) 
            @Valid @RequestBody StorefrontCreateBookingRequestDto request,
            @Parameter(description = "User ID (optional, for MCP tool calls. If not provided, extracted from JWT token)", required = false)
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        try {
            log.info("Creating storefront booking with type: {} ", request.getBookingType());

            // Resolve userId: prioritize header (for MCP tools), fallback to JWT token
            UUID userId;
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                try {
                    userId = UUID.fromString(userIdHeader);
                    log.debug("Using userId from X-User-Id header: {}", userId);
                } catch (IllegalArgumentException e) {
                    log.error("Invalid X-User-Id header format: {}", userIdHeader);
                    throw new IllegalArgumentException("Invalid userId format in X-User-Id header");
                }
            } else {
                userId = AuthenticationUtils.getCurrentUserIdFromContext();
                log.debug("Using userId from JWT token: {}", userId);
            }

            return createBookingInternal(request, userId);

        } catch (Exception e) {
            log.error("Error creating storefront booking via CQRS: {}", e.getMessage(), e);
            StorefrontBookingResponseDto errorResponse = StorefrontBookingResponseDto.builder()
                    .error("An unexpected error occurred: " + e.getMessage())
                    .errorCode("BOOKING_CREATION_ERROR")
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Internal method for creating booking with explicit userId
     * Used by both REST endpoint and MCP tool service
     */
    public ResponseEntity<StorefrontBookingResponseDto> createBookingInternal(
            StorefrontCreateBookingRequestDto request, 
            UUID userId) {
        try {
            Object productDetails = storefrontProductDetailsAssembler.buildProductDetails(request);
            String productDetailsJson = productDetailsService.convertToJson(request.getBookingType(), productDetails);

            CreateBookingCommand command = CreateBookingCommand.builder()
                    .userId(userId)
                    .bookingType(request.getBookingType())
                    .totalAmount(BigDecimal.valueOf(request.getTotalAmount()))
                    .currency(request.getCurrency())
                    .productDetailsJson(productDetailsJson)
                    .notes(request.getNotes())
                    .bookingSource(request.getBookingSource() != null ? request.getBookingSource() : "STOREFRONT")
                    .sagaId(UUID.randomUUID().toString())
                    .correlationId(UUID.randomUUID().toString())
                    .build();

            // Execute command via CQRS service
            Booking createdBooking = bookingCqrsService.createBooking(command);

            // Convert entity to response DTO
            StorefrontBookingResponseDto response = bookingDtoMapper.toStorefrontResponseDto(createdBooking);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error creating storefront booking via CQRS: {}", e.getMessage(), e);
            StorefrontBookingResponseDto errorResponse = StorefrontBookingResponseDto.builder()
                    .error("An unexpected error occurred: " + e.getMessage())
                    .errorCode("BOOKING_CREATION_ERROR")
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Get booking by saga ID (Storefront) using CQRS
     */
    @Operation(summary = "Get booking by saga ID (Customer)", description = "Retrieve booking details using the saga ID for customer storefront", tags = {
            "Public API" })
    @SecurityRequirement(name = "oauth2", scopes = { "customer" })
    @OpenApiResponses.StandardApiResponsesWithNotFound
    @GetMapping("/storefront/saga/{sagaId}")
    public ResponseEntity<StorefrontBookingResponseDto> getStorefrontBookingBySagaId(
            @Parameter(description = "Saga ID", required = true, example = "saga-12345") @PathVariable String sagaId) {
        try {
            UUID userId = AuthenticationUtils.getCurrentUserIdFromContext();
            Optional<Booking> booking = bookingCqrsService.getBookingBySagaId(sagaId, userId.toString());

            return booking.map(b -> {
                StorefrontBookingResponseDto response = bookingDtoMapper.toStorefrontResponseDto(b);
                return ResponseEntity.ok(response);
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error getting booking by saga ID: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get booking status for polling (Storefront) using CQRS
     * Used by frontend to check validation progress
     */
    @org.springframework.ai.tool.annotation.Tool(name = "get_booking_status", description = "Get the current status of a booking by sagaId. Returns status (PENDING/CONFIRMED/COMPLETED/FAILED/CANCELLED), "
            +
            "booking details, and validation progress. Use this to track booking creation progress.")
    @Operation(summary = "Get booking status", description = "Poll booking status to track validation and processing progress", tags = {
            "Public API", "Status Tracking" })
    @SecurityRequirement(name = "oauth2", scopes = { "customer" })
    @OpenApiResponses.StandardApiResponsesWithNotFound
    @GetMapping("/storefront/{bookingId}/status")
    public ResponseEntity<BookingStatusResponseDto> getBookingStatus(
            @Parameter(description = "Booking ID", required = true, example = "123e4567-e89b-12d3-a456-426614174000") @org.springframework.ai.tool.annotation.ToolParam(description = "The booking ID (UUID) to check status for") @PathVariable UUID bookingId) {
        try {
            UUID userId = AuthenticationUtils.getCurrentUserIdFromContext();
            Optional<Booking> bookingOpt = bookingCqrsService.getBookingById(bookingId, userId);

            if (bookingOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Booking booking = bookingOpt.get();

            BookingStatus effectiveStatus = booking.getStatus();
            // if (bypassValidation && effectiveStatus == BookingStatus.VALIDATION_PENDING)
            // {
            // effectiveStatus = BookingStatus.PENDING;
            // }

            String reason = firstNonBlank(booking.getCancellationReason(), booking.getCompensationReason());
            BookingStatusResponseDto response = BookingStatusResponseDto.builder()
                    .bookingId(booking.getBookingId().toString())
                    .bookingReference(booking.getBookingReference())
                    .status(effectiveStatus)
                    .lastUpdated(booking.getUpdatedAt() != null ? booking.getUpdatedAt().toString() : null)
                    .progressPercentage(resolveProgress(effectiveStatus))
                    .reservationLockedAt(
                            booking.getReservationLockedAt() != null ? booking.getReservationLockedAt().toString()
                                    : null)
                    .reservationExpiresAt(
                            booking.getReservationExpiresAt() != null ? booking.getReservationExpiresAt().toString()
                                    : null)
                    .build();

            switch (effectiveStatus) {
                case VALIDATION_PENDING -> {
                    response.setMessage("Validating product availability...");
                    response.setEstimatedCompletion("2-5 seconds");
                }
                case PENDING -> {
                    response.setMessage("Processing your booking...");
                    response.setEstimatedCompletion("10-30 seconds");
                }
                case PAYMENT_PENDING -> {
                    response.setMessage("Waiting for payment processing...");
                    response.setEstimatedCompletion("5-15 seconds");
                }
                case CONFIRMED -> response.setMessage("Booking confirmed successfully!");
                case PAID -> response.setMessage("Payment completed successfully!");
                case PAYMENT_FAILED -> response.setMessage(reason != null ? reason : "Payment processing failed");
                case CANCELLED -> response.setMessage(reason != null ? reason : "Booking has been cancelled");
                case VALIDATION_FAILED ->
                    response.setMessage(reason != null ? reason : "Product availability validation failed");
                case FAILED -> response.setMessage(reason != null ? reason : "Booking processing failed");
                default -> response.setMessage("Unknown status");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting booking status for: {}", bookingId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private int resolveProgress(BookingStatus status) {
        return switch (status) {
            case VALIDATION_PENDING -> 10;
            case PENDING -> 40;
            case PAYMENT_PENDING -> 70;
            case CONFIRMED, PAID, CANCELLED, FAILED, PAYMENT_FAILED, VALIDATION_FAILED -> 100;
            default -> 0;
        };
    }

    private String firstNonBlank(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary;
        }
        return null;
    }

}
