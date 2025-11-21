package com.pdh.booking.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdh.booking.controller.BookingController;
import com.pdh.booking.model.Booking;
import com.pdh.booking.model.dto.request.StorefrontCreateBookingRequestDto;
import com.pdh.booking.model.dto.response.BookingHistoryResponseDto;
import com.pdh.booking.model.dto.response.StorefrontBookingResponseDto;
import com.pdh.booking.model.enums.BookingStatus;
import com.pdh.booking.service.BookingCqrsService;
import com.pdh.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Booking MCP Tool Service - Exposes booking operations as AI-callable MCP tools
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingMcpToolService {

    private final BookingController bookingController;
    private final BookingCqrsService bookingCqrsService;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    @McpTool(
        name = "create_booking",
        description = """
            Create booking for flights/hotels/combo. 
            
            CRITICAL: When creating flight bookings, you MUST preserve ALL fields from the search_flights result object:
            - departureDateTime (ISO 8601 with timezone, e.g., "2025-11-20T08:00:00+07:00")
            - arrivalDateTime (ISO 8601 with timezone)
            - scheduleId (UUID from search result - MUST be valid UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
            - fareId (UUID from search result - MUST be valid UUID format)
            - flightId, flightNumber, airline, originAirport, destinationAirport
            - pricePerPassenger, totalFlightPrice
            
            For hotel bookings, preserve:
            - hotelId, roomTypeId, checkInDate, checkOutDate, pricePerNight
            
            Add passenger/guest details from user input or customer profile.
            
            IMPORTANT: userId parameter is required - pass the authenticated user's UUID.
            Returns: bookingId, sagaId, bookingReference, status
            """
    )
    public Map<String, Object> createBooking(
            @McpToolParam(description = "User ID (UUID of the authenticated user)") String userId,
            @McpToolParam(description = "JSON booking payload") StorefrontCreateBookingRequestDto bookingPayload) {
        try {
            log.info("MCP Tool: Creating booking for userId: {}", userId);
            
            // Validate and parse userId
            UUID userUuid;
            try {
                userUuid = UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid userId format: {}", userId);
                return Map.of(
                    "success", false, 
                    "error", "Invalid userId format. Must be a valid UUID."
                );
            }
            
            // Validate flight booking fields
            if (bookingPayload.getFlightSelection() != null) {
                var flightDetails = bookingPayload.getFlightSelection();
                
                // Validate departureDateTime (now String in ISO 8601 format)
                if (flightDetails.getDepartureDateTime() == null || flightDetails.getDepartureDateTime().isBlank() || flightDetails.getDepartureDateTime().equals("null")) {
                    return Map.of(
                        "success", false, 
                        "error", "Missing departureDateTime for flight booking. Please use the exact flight object from search_flights result with ISO 8601 format (e.g., 2025-11-16T20:45:00+07:00)."
                    );
                }
                
                // Validate arrivalDateTime (now String in ISO 8601 format)
                if (flightDetails.getArrivalDateTime() == null || flightDetails.getArrivalDateTime().isBlank() || flightDetails.getArrivalDateTime().equals("null")) {
                    return Map.of(
                        "success", false, 
                        "error", "Missing arrivalDateTime for flight booking. Please use the exact flight object from search_flights result with ISO 8601 format (e.g., 2025-11-16T22:20:00+07:00)."
                    );
                }
                
                // Validate and sanitize scheduleId
                if (flightDetails.getScheduleId() == null || flightDetails.getScheduleId().isBlank()) {
                    return Map.of(
                        "success", false, 
                        "error", "Missing scheduleId for flight booking. Please use the exact flight object from search_flights result."
                    );
                }
                if (!isValidUUID(flightDetails.getScheduleId())) {
                    return Map.of(
                        "success", false, 
                        "error", "Invalid scheduleId format: '" + flightDetails.getScheduleId() + "'. Must be a valid UUID (e.g., 7dd0f7ea-a238-481f-bce4-2e7147b73e32). Please use the EXACT scheduleId from search_flights result."
                    );
                }
                
                // Validate and sanitize fareId
                if (flightDetails.getFareId() == null || flightDetails.getFareId().isBlank()) {
                    return Map.of(
                        "success", false, 
                        "error", "Missing fareId for flight booking. Please use the exact flight object from search_flights result."
                    );
                }
                if (!isValidUUID(flightDetails.getFareId())) {
                    return Map.of(
                        "success", false, 
                        "error", "Invalid fareId format: '" + flightDetails.getFareId() + "'. Must be a valid UUID. Please use the EXACT fareId from search_flights result."
                    );
                }
            }
            
            bookingPayload.setBookingSource("AI_AGENT");

            // Call internal method with explicit userId
            ResponseEntity<StorefrontBookingResponseDto> responseEntity = bookingController.createBookingInternal(bookingPayload, userUuid);
            StorefrontBookingResponseDto response = responseEntity.getBody();

            if (response == null) {
                return Map.of("success", false, "error", "No response from booking service");
            }

            return Map.of(
                "success", true,
                "bookingId", response.getBookingId(),
                "sagaId", response.getSagaId(),
                "bookingReference", response.getBookingReference(),
                "status", response.getStatus(),
                "totalAmount", response.getTotalAmount(),
                "currency", response.getCurrency(),
                "message", "Booking created: " + response.getStatus()
            );
        } catch (Exception e) {
            log.error("MCP Tool: Error creating booking", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @McpTool(
        name = "get_booking_status",
        description = "Get booking status by bookingId or sagaId. Requires userId for authorization."
    )
    public Map<String, Object> getBookingStatus(
        @McpToolParam(description = "User ID (UUID of the authenticated user)") String userId,
        @McpToolParam(description = "Booking ID") String bookingId,
        @McpToolParam(description = "Saga ID") String sagaId
    ) {
        try {
            log.info("MCP Tool: Getting booking status for userId: {}", userId);
            
            // Validate and parse userId
            UUID userUuid;
            try {
                userUuid = UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid userId format: {}", userId);
                return Map.of(
                    "success", false,
                    "error", "Invalid userId format. Must be a valid UUID."
                );
            }
            
            Optional<Booking> bookingOpt = Optional.empty();

            if (bookingId != null && !bookingId.isBlank()) {
                bookingOpt = bookingCqrsService.getBookingById(UUID.fromString(bookingId), userUuid);
            } else if (sagaId != null && !sagaId.isBlank()) {
                bookingOpt = bookingCqrsService.getBookingBySagaId(sagaId, userUuid);
            }

            if (bookingOpt.isEmpty()) {
                return Map.of("success", false, "error", "Booking not found");
            }

            Booking booking = bookingOpt.get();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("bookingId", booking.getBookingId().toString());
            result.put("sagaId", booking.getSagaId());
            result.put("bookingReference", booking.getBookingReference());
            result.put("status", booking.getStatus().toString());
            result.put("bookingType", booking.getBookingType().toString());
            result.put("totalAmount", booking.getTotalAmount());
            result.put("currency", booking.getCurrency());
            result.put("statusInfo", getStatusInfo(booking.getStatus()));

            return result;
        } catch (Exception e) {
            log.error("MCP Tool: Error getting status", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @McpTool(
        name = "get_booking_by_reference",
        description = """
            Get booking details by booking reference code (e.g., BK17612915025233).
            This is the most user-friendly way to check booking status as users receive this reference code.
            Returns complete booking information including status, amounts, and IDs.
            Requires userId for authorization.
            """
    )
    public Map<String, Object> getBookingByReference(
        @McpToolParam(description = "User ID (UUID of the authenticated user)") String userId,
        @McpToolParam(description = "Booking reference code (e.g., BK-ABC123)") String bookingReference
    ) {
        try {
            log.info("MCP Tool: Getting booking by reference: {} for userId: {}", bookingReference, userId);
            
            if (bookingReference == null || bookingReference.isBlank()) {
                return Map.of("success", false, "error", "Booking reference is required");
            }
            
            // Validate and parse userId
            UUID userUuid;
            try {
                userUuid = UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid userId format: {}", userId);
                return Map.of(
                    "success", false,
                    "error", "Invalid userId format. Must be a valid UUID."
                );
            }
            
            Optional<Booking> bookingOpt = bookingCqrsService.getBookingByReference(bookingReference, userUuid);

            if (bookingOpt.isEmpty()) {
                return Map.of(
                    "success", false, 
                    "error", "Booking not found with reference: " + bookingReference
                );
            }

            Booking booking = bookingOpt.get();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("bookingId", booking.getBookingId().toString());
            result.put("sagaId", booking.getSagaId());
            result.put("bookingReference", booking.getBookingReference());
            result.put("status", booking.getStatus().toString());
            result.put("bookingType", booking.getBookingType().toString());
            result.put("totalAmount", booking.getTotalAmount());
            result.put("currency", booking.getCurrency());
            result.put("createdAt", booking.getCreatedAt().toString());
            result.put("statusInfo", getStatusInfo(booking.getStatus()));

            return result;
        } catch (Exception e) {
            log.error("MCP Tool: Error getting booking by reference", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @McpTool(
        name = "cancel_booking",
        description = "Cancel a booking by bookingId. Requires userId for authorization."
    )
    public Map<String, Object> cancelBooking(
        @McpToolParam(description = "User ID (UUID of the authenticated user)") String userId,
        @McpToolParam(description = "Booking ID to cancel") String bookingId,
        @McpToolParam(description = "Cancellation reason") String reason
    ) {
        try {
            log.info("MCP Tool: Cancelling booking for userId: {}", userId);
            
            // Validate and parse userId
            UUID userUuid;
            try {
                userUuid = UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid userId format: {}", userId);
                return Map.of(
                    "success", false,
                    "error", "Invalid userId format. Must be a valid UUID."
                );
            }
            
            UUID bookingUuid = UUID.fromString(bookingId);

            Optional<Booking> bookingOpt = bookingCqrsService.getBookingById(bookingUuid, userUuid);
            if (bookingOpt.isEmpty()) {
                return Map.of("success", false, "error", "Booking not found");
            }

            Booking booking = bookingOpt.get();
            if (!canCancelBooking(booking.getStatus())) {
                return Map.of("success", false, "error", "Cannot cancel booking with status: " + booking.getStatus());
            }

            // TODO: Implement proper cancellation command
            log.warn("Cancellation needs proper CQRS implementation");
            return Map.of(
                "success", true,
                "bookingId", bookingId,
                "message", "Cancellation request received"
            );
        } catch (Exception e) {
            log.error("MCP Tool: Error cancelling booking", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @McpTool(
        name = "get_booking_history",
        description = """
            Get paginated booking history for a user. Returns list of bookings with details, status, and booking information.
            Default page=0, size=10. Requires userId for authorization.
            """
    )
    public Map<String, Object> getBookingHistory(
        @McpToolParam(description = "User ID (UUID of the authenticated user)") String userId,
        @McpToolParam(description = "Page number (0-based, default: 0)") Integer page,
        @McpToolParam(description = "Number of results per page (default: 10)") Integer size
    ) {
        try {
            log.info("MCP Tool: Getting booking history for userId: {}", userId);
            
            // Validate and parse userId
            UUID userUuid;
            try {
                userUuid = UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid userId format: {}", userId);
                return Map.of(
                    "success", false,
                    "error", "Invalid userId format. Must be a valid UUID."
                );
            }
            
            int pageIndex = Math.max(page != null ? page : 0, 0);
            int pageSize = Math.max(1, Math.min(size != null ? size : 10, 50));
            Pageable pageable = PageRequest.of(pageIndex, pageSize);

            var historyPage = bookingService.getBookingHistory(userUuid, pageable);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("items", historyPage.getContent());
            result.put("page", historyPage.getNumber());
            result.put("size", historyPage.getSize());
            result.put("totalElements", historyPage.getTotalElements());
            result.put("totalPages", historyPage.getTotalPages());
            result.put("hasNext", historyPage.hasNext());

            return result;
        } catch (Exception e) {
            log.error("MCP Tool: Error getting booking history", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    private String getStatusInfo(BookingStatus status) {
        return switch (status) {
            case PENDING -> "Booking pending - payment may be required";
            case CONFIRMED -> "Booking confirmed";
            case PAID -> "Booking completed and paid";
            case CANCELLED -> "Booking cancelled";
            case FAILED -> "Booking failed";
            case PAYMENT_PENDING -> "Awaiting payment";
            case PAYMENT_FAILED -> "Payment failed";
            case VALIDATION_PENDING -> "Validation in progress";
            case VALIDATION_FAILED -> "Validation failed";
            default -> "Status: " + status;
        };
    }

    private boolean canCancelBooking(BookingStatus status) {
        return status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED || status == BookingStatus.PAYMENT_PENDING;
    }
    
    /**
     * Validate UUID format
     */
    private boolean isValidUUID(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
