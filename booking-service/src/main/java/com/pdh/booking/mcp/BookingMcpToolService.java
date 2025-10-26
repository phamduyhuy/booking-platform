//package com.pdh.booking.mcp;
//
//import com.pdh.booking.command.CreateBookingCommand;
//import com.pdh.booking.model.Booking;
//import com.pdh.booking.model.dto.request.StorefrontCreateBookingRequestDto;
//import com.pdh.booking.model.dto.response.BookingHistoryItemDto;
//import com.pdh.booking.model.dto.response.StorefrontBookingResponseDto;
//import com.pdh.booking.model.enums.BookingStatus;
//import com.pdh.booking.model.enums.BookingType;
//import com.pdh.booking.service.BookingCqrsService;
//import com.pdh.common.utils.AuthenticationUtils;
//import com.pdh.booking.mapper.BookingDtoMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springaicommunity.mcp.annotation.McpTool;
//import org.springaicommunity.mcp.annotation.McpToolParam;
//import org.springframework.ai.tool.annotation.Tool;
//import org.springframework.ai.tool.annotation.ToolParam;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.stereotype.Service;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.stream.Collectors;
//
///**
// * Booking MCP Tool Service
// * Exposes booking operations as AI-callable tools
// */
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class BookingMcpToolService {
//
//    private final BookingCqrsService bookingCqrsService;
//    private final BookingDtoMapper bookingDtoMapper;
//
//    /**
//     * Create a new booking for flights or hotels
//     */
//    @McpTool(generateOutputSchema = true)
//    @Tool(
//        name = "create_booking",
//        description = "Create a new booking for a flight or hotel. Required parameters: " +
//                "bookingType (must be 'FLIGHT' or 'HOTEL'), " +
//                "serviceItemId (the flight ID or hotel ID to book), " +
//                "totalAmount (total price as decimal number), " +
//                "currency (e.g., 'USD', 'VND'). " +
//                "Optional: specialRequests (any special requests from customer), " +
//                "passengerInfo (passenger details as JSON string). " +
//                "Returns a booking with sagaId that can be used to track the booking status. " +
//                "The booking will be in PENDING status initially and needs payment to be confirmed."
//    )
//    public Map<String, Object> createBooking(
//            @McpToolParam
//            @ToolParam(description = "Booking type: either 'FLIGHT' or 'HOTEL' or 'COMBO'", required = true)
//            String bookingType,
//
//            @ToolParam(description = "The ID of the flight or hotel to book (UUID format)", required = true)
//            String serviceItemId,
//
//            @ToolParam(description = "Total amount to pay (decimal number)", required = true)
//            BigDecimal totalAmount,
//
//            @ToolParam(description = "Currency code (e.g., 'USD', 'VND', 'EUR')", required = true)
//            String currency,
//
//
//            @ToolParam(description = "Any special requests or notes from the customer", required = false)
//            String specialRequests,
//
//            @ToolParam(description = "Passenger information as JSON string (for flights) or guest info (for hotels)", required = false)
//            String passengerInfo
//    ) {
//        try {
//            log.info("AI Tool: Creating booking - type={}, serviceItemId={}, amount={} {}",
//                    bookingType, serviceItemId, totalAmount, currency);
//
//            // Validate booking type
//            BookingType type;
//            try {
//                type = BookingType.valueOf(bookingType.toUpperCase());
//            } catch (IllegalArgumentException e) {
//                return createErrorResponse("Invalid booking type. Must be 'FLIGHT' or 'HOTEL'");
//            }
//
//            // Create product details JSON
//            String productDetailsJson = String.format(
//                "{\"serviceItemId\":\"%s\",\"passengerInfo\":%s}",
//                serviceItemId,
//                passengerInfo != null ? passengerInfo : "\"\""
//            );
//
//            // Execute booking command directly
//            CreateBookingCommand command = CreateBookingCommand.builder()
//                .userId(AuthenticationUtils.getCurrentUserIdFromContext())
//                .bookingType(type)
//                .totalAmount(totalAmount)
//                .currency(currency)
//                .productDetailsJson(productDetailsJson)
//                .notes(specialRequests)
//                .bookingSource("AI_AGENT")
//                .sagaId(UUID.randomUUID().toString())
//                .correlationId(UUID.randomUUID().toString())
//                .build();
//
//            Booking createdBooking = bookingCqrsService.createBooking(command);
//            String sagaId = createdBooking.getSagaId();
//
//            return Map.of(
//                "success", true,
//                "sagaId", sagaId,
//                "bookingId", createdBooking.getBookingId().toString(),
//                "status", createdBooking.getStatus().toString(),
//                "bookingType", createdBooking.getBookingType().toString(),
//                "totalAmount", createdBooking.getTotalAmount(),
//                "currency", createdBooking.getCurrency(),
//                "message", "Booking created successfully. Use the sagaId to track status or proceed with payment.",
//                "nextStep", "Call process_payment with this bookingId to complete the booking"
//            );
//
//        } catch (Exception e) {
//            log.error("AI Tool: Error creating booking", e);
//            return createErrorResponse("Failed to create booking: " + e.getMessage());
//        }
//    }
//
//    /**
//     * Get booking status by saga ID
//     */
//    @Tool(
//        name = "get_booking_status",
//        description = "Get the current status and details of a booking using its sagaId. " +
//                "Returns booking status (PENDING, CONFIRMED, COMPLETED, FAILED, CANCELLED), " +
//                "booking details, amounts, and processing progress. " +
//                "Use this to track booking creation progress or check current booking state."
//    )
//    public Map<String, Object> getBookingStatus(
//            @ToolParam(description = "The saga ID returned when booking was created", required = true)
//            String sagaId,
//
//            @ToolParam(description = "User ID who owns the booking", required = true)
//            String userId
//    ) {
//        try {
//            log.info("AI Tool: Getting booking status - sagaId={}", sagaId);
//
//            Optional<Booking> bookingOpt = bookingCqrsService.getBookingBySagaId(sagaId, userId);
//
//            if (bookingOpt.isEmpty()) {
//                return createErrorResponse("Booking not found with sagaId: " + sagaId);
//            }
//
//            Booking booking = bookingOpt.get();
//
//            Map<String, Object> response = new LinkedHashMap<>();
//            response.put("success", true);
//            response.put("bookingId", booking.getBookingId().toString());
//            response.put("sagaId", booking.getSagaId());
//            response.put("status", booking.getStatus().toString());
//            response.put("bookingType", booking.getBookingType().toString());
//            response.put("productDetails", booking.getProductDetailsJson());
//            response.put("totalAmount", booking.getTotalAmount());
//            response.put("currency", booking.getCurrency());
//            response.put("createdAt", booking.getCreatedAt());
//
//            if (booking.getNotes() != null) {
//                response.put("notes", booking.getNotes());
//            }
//
//            // Add status-specific guidance
//            switch (booking.getStatus()) {
//                case VALIDATION_PENDING:
//                    response.put("message", "Booking is being validated. Please wait...");
//                    response.put("nextStep", "Wait for validation to complete");
//                    break;
//                case PENDING:
//                    response.put("message", "Booking is pending. Proceed with payment to confirm.");
//                    response.put("nextStep", "Call process_payment to complete the booking");
//                    break;
//                case CONFIRMED:
//                    response.put("message", "Booking is confirmed. Proceed with payment.");
//                    response.put("nextStep", "Call process_payment to complete the booking");
//                    break;
//                case PAYMENT_PENDING:
//                    response.put("message", "Payment is being processed...");
//                    response.put("nextStep", "Wait for payment confirmation");
//                    break;
//                case PAID:
//                    response.put("message", "Booking is complete. Payment successful.");
//                    response.put("nextStep", "Booking is complete. User can view booking details.");
//                    break;
//                case PAYMENT_FAILED:
//                    response.put("message", "Payment failed. Please try again.");
//                    response.put("nextStep", "Call process_payment again with valid payment method");
//                    break;
//                case FAILED:
//                    response.put("message", "Booking failed. Please try creating a new booking.");
//                    response.put("nextStep", "Create a new booking");
//                    break;
//                case VALIDATION_FAILED:
//                    response.put("message", "Booking validation failed. Service unavailable or no availability.");
//                    response.put("nextStep", "Try different dates or service");
//                    break;
//                case CANCELLED:
//                    response.put("message", "Booking has been cancelled.");
//                    response.put("nextStep", "Booking is cancelled");
//                    break;
//            }
//
//            return response;
//
//        } catch (Exception e) {
//            log.error("AI Tool: Error getting booking status", e);
//            return createErrorResponse("Failed to get booking status: " + e.getMessage());
//        }
//    }
//
//    /**
//     * Get booking history for a user
//     */
//    @Tool(
//        name = "get_user_booking_history",
//        description = "Get paginated booking history for a user. Returns list of all bookings " +
//                "with their status, dates, amounts, and details. Useful for showing user their past bookings. " +
//                "Default page=0 (first page), size=10 (10 bookings per page)."
//    )
//    public Map<String, Object> getUserBookingHistory(
//            @ToolParam(description = "User ID to get booking history for", required = true)
//            String userId,
//
//            @ToolParam(description = "Page number (0-based). Default: 0", required = false)
//            Integer page,
//
//            @ToolParam(description = "Number of bookings per page. Default: 10", required = false)
//            Integer size
//    ) {
//        try {
//            int effectivePage = (page != null && page >= 0) ? page : 0;
//            int effectiveSize = (size != null && size > 0) ? size : 10;
//
//            log.info("AI Tool: Getting booking history - userId={}, page={}, size={}",
//                    userId, effectivePage, effectiveSize);
//
//            UUID userUuid = UUID.fromString(userId);
//
//            // Create query for user bookings
//            com.pdh.booking.query.GetUserBookingsQuery query =
//                com.pdh.booking.query.GetUserBookingsQuery.builder()
//                    .userId(userUuid)
//                    .page(effectivePage)
//                    .size(effectiveSize)
//                    .build();
//
//            Page<Booking> bookingsPage = bookingCqrsService.getUserBookings(query);
//
//            List<Map<String, Object>> bookings = bookingsPage.getContent().stream()
//                .map(booking -> {
//                    Map<String, Object> bookingMap = new LinkedHashMap<>();
//                    bookingMap.put("bookingId", booking.getBookingId().toString());
//                    bookingMap.put("sagaId", booking.getSagaId());
//                    bookingMap.put("bookingType", booking.getBookingType().toString());
//                    bookingMap.put("status", booking.getStatus().toString());
//                    bookingMap.put("productDetails", booking.getProductDetailsJson());
//                    bookingMap.put("totalAmount", booking.getTotalAmount());
//                    bookingMap.put("currency", booking.getCurrency());
//                    bookingMap.put("createdAt", booking.getCreatedAt());
//                    if (booking.getNotes() != null) {
//                        bookingMap.put("notes", booking.getNotes());
//                    }
//                    return bookingMap;
//                })
//                .collect(Collectors.toList());
//
//            return Map.of(
//                "success", true,
//                "bookings", bookings,
//                "totalBookings", bookingsPage.getTotalElements(),
//                "totalPages", bookingsPage.getTotalPages(),
//                "currentPage", effectivePage,
//                "pageSize", effectiveSize,
//                "hasNext", bookingsPage.hasNext(),
//                "hasPrevious", bookingsPage.hasPrevious()
//            );
//
//        } catch (Exception e) {
//            log.error("AI Tool: Error getting booking history", e);
//            return createErrorResponse("Failed to get booking history: " + e.getMessage());
//        }
//    }
//
//    private Map<String, Object> createErrorResponse(String message) {
//        return Map.of(
//            "success", false,
//            "error", message
//        );
//    }
//}
