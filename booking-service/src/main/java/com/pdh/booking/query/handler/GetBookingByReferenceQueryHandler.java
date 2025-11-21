package com.pdh.booking.query.handler;

import com.pdh.booking.model.Booking;
import com.pdh.booking.query.GetBookingByReferenceQuery;
import com.pdh.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Query handler for getting booking by reference code
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetBookingByReferenceQueryHandler {
    
    private final BookingRepository bookingRepository;
    
    public Optional<Booking> handle(GetBookingByReferenceQuery query) {
        log.debug("Processing GetBookingByReferenceQuery for reference: {}", query.getBookingReference());
        
        try {
            Optional<Booking> booking = bookingRepository.findByBookingReference(query.getBookingReference());
            
            // Optional authorization check
            if (booking.isPresent() && query.getUserId() != null) {
                if (!booking.get().getUserId().equals(query.getUserId())) {
                    log.warn("Unauthorized access attempt for booking reference: {} by user: {}", 
                            query.getBookingReference(), query.getUserId());
                    return Optional.empty();
                }
            }
            
            return booking;
            
        } catch (Exception e) {
            log.error("Error processing GetBookingByReferenceQuery: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}
