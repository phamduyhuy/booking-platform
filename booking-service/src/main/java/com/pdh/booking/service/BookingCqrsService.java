package com.pdh.booking.service;

import com.pdh.booking.command.CreateBookingCommand;
import com.pdh.booking.command.ProcessPaymentCommand;
import com.pdh.booking.command.CancelBookingCommand;
import com.pdh.booking.command.handler.CreateBookingCommandHandler;
import com.pdh.booking.command.handler.ProcessPaymentCommandHandler;
import com.pdh.booking.command.handler.CancelBookingCommandHandler;
import com.pdh.booking.model.Booking;
import com.pdh.booking.query.GetBookingByIdQuery;
import com.pdh.booking.query.GetBookingByReferenceQuery;
import com.pdh.booking.query.GetBookingBySagaIdQuery;
import com.pdh.booking.query.GetUserBookingsQuery;
import com.pdh.booking.query.handler.GetBookingByIdQueryHandler;
import com.pdh.booking.query.handler.GetBookingByReferenceQueryHandler;
import com.pdh.booking.query.handler.GetBookingBySagaIdQueryHandler;
import com.pdh.booking.query.handler.GetUserBookingsQueryHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * CQRS Service for booking operations
 * This service coordinates commands and queries for the booking domain
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCqrsService {
    
    // Command handlers
    private final CreateBookingCommandHandler createBookingCommandHandler;
    private final ProcessPaymentCommandHandler processPaymentCommandHandler;
    private final CancelBookingCommandHandler cancelBookingCommandHandler;
    
    // Query handlers
    private final GetBookingByIdQueryHandler getBookingByIdQueryHandler;
    private final GetBookingByReferenceQueryHandler getBookingByReferenceQueryHandler;
    private final GetBookingBySagaIdQueryHandler getBookingBySagaIdQueryHandler;
    private final GetUserBookingsQueryHandler getUserBookingsQueryHandler;
    
    // ============== COMMAND METHODS ==============
    
    /**
     * Create a new booking using saga orchestration
     */
    public Booking createBooking(CreateBookingCommand command) {
        log.info("Creating booking via CQRS for user: {}", command.getUserId());
        return createBookingCommandHandler.handle(command);
    }
    
    /**
     * Process payment for a booking
     */
    public void processPayment(ProcessPaymentCommand command) {
        log.info("Processing payment via CQRS for booking: {}", command.getBookingId());
        processPaymentCommandHandler.handle(command);
    }
    
    /**
     * Cancel a booking
     */
    public void cancelBooking(CancelBookingCommand command) {
        log.info("Canceling booking via CQRS for booking: {}", command.getBookingId());
        cancelBookingCommandHandler.handle(command);
    }
    
    // ============== QUERY METHODS ==============
    
    /**
     * Get booking by ID
     */
    public Optional<Booking> getBookingById(UUID bookingId) {
        log.debug("Getting booking by ID: {}", bookingId);
        GetBookingByIdQuery query = GetBookingByIdQuery.builder()
                .bookingId(bookingId)
                .build();
        return getBookingByIdQueryHandler.handle(query);
    }
    
    /**
     * Get booking by ID with user authorization
     */
    public Optional<Booking> getBookingById(UUID bookingId, UUID userId) {
        log.debug("Getting booking by ID: {} for user: {}", bookingId, userId);
        GetBookingByIdQuery query = GetBookingByIdQuery.builder()
                .bookingId(bookingId)
                .userId(userId)
                .build();
        return getBookingByIdQueryHandler.handle(query);
    }
    
    /**
     * Get booking by reference code
     */
    public Optional<Booking> getBookingByReference(String bookingReference) {
        log.debug("Getting booking by reference: {}", bookingReference);
        GetBookingByReferenceQuery query = GetBookingByReferenceQuery.builder()
                .bookingReference(bookingReference)
                .build();
        return getBookingByReferenceQueryHandler.handle(query);
    }
    
    /**
     * Get booking by reference code with user authorization
     */
    public Optional<Booking> getBookingByReference(String bookingReference, UUID userId) {
        log.debug("Getting booking by reference: {} for user: {}", bookingReference, userId);
        GetBookingByReferenceQuery query = GetBookingByReferenceQuery.builder()
                .bookingReference(bookingReference)
                .userId(userId)
                .build();
        return getBookingByReferenceQueryHandler.handle(query);
    }
    
    /**
     * Get booking by saga ID
     */
    public Optional<Booking> getBookingBySagaId(String sagaId) {
        log.debug("Getting booking by saga ID: {}", sagaId);
        GetBookingBySagaIdQuery query = GetBookingBySagaIdQuery.builder()
                .sagaId(sagaId)
                .build();
        return getBookingBySagaIdQueryHandler.handle(query);
    }
    
    /**
     * Get booking by saga ID with user authorization
     */
    public Optional<Booking> getBookingBySagaId(String sagaId, String userId) {
        log.debug("Getting booking by saga ID: {} for user: {}", sagaId, userId);
        GetBookingBySagaIdQuery query = GetBookingBySagaIdQuery.builder()
                .sagaId(sagaId)
                .userId(userId)
                .build();
        return getBookingBySagaIdQueryHandler.handle(query);
    }
    
    /**
     * Get booking by saga ID with user authorization (UUID version)
     */
    public Optional<Booking> getBookingBySagaId(String sagaId, UUID userId) {
        return getBookingBySagaId(sagaId, userId.toString());
    }
    
    /**
     * Get user's bookings with filtering
     */
    public Page<Booking> getUserBookings(GetUserBookingsQuery query) {
        log.debug("Getting user bookings for user: {}", query.getUserId());
        return getUserBookingsQueryHandler.handle(query);
    }
}
