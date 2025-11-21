package com.pdh.booking.query;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Query to get booking by booking reference code
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetBookingByReferenceQuery {
    
    @NotBlank(message = "Booking reference is required")
    private String bookingReference;
    
    private UUID userId; // Optional: for authorization
}
