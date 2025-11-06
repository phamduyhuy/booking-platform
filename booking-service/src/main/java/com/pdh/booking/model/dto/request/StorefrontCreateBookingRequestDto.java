package com.pdh.booking.model.dto.request;

import com.pdh.booking.model.enums.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * DTO for creating a new booking from Storefront
 * Uses frontend-compatible data types (String for IDs, double for amounts)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorefrontCreateBookingRequestDto {
    
 

    /**
     * Type of booking (FLIGHT, HOTEL, COMBO, etc.)
     */
    @NotNull(message = "Booking type is required")
    @JsonProperty(required = true, value = "bookingType")
    @JsonPropertyDescription("Type of booking. Valid values: FLIGHT, HOTEL, BUS, TRAIN, COMBO")
    private BookingType bookingType;
    
    /**
     * Total amount as double (frontend-compatible)
     */
    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Total amount must be greater than 0")
    @JsonProperty(required = true, value = "totalAmount")
    @JsonPropertyDescription("Total booking amount in the specified currency. Must be greater than 0")
    private Double totalAmount;
    
    /**
     * Currency code (default: VND)
     */
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Builder.Default
    @JsonProperty(required = false, value = "currency")
    @JsonPropertyDescription("3-letter currency code (e.g., VND, USD, EUR). Default is VND")
    private String currency = "VND";
    
    /**
     * Selected flight payload (required for FLIGHT and COMBO bookings)
     */
    @Valid
    @JsonProperty(required = false, value = "flightSelection")
    @JsonPropertyDescription("Flight selection details. Required for FLIGHT and COMBO bookings")
    private StorefrontFlightSelectionRequestDto flightSelection;

    /**
     * Selected hotel payload (required for HOTEL and COMBO bookings)
     */
    @Valid
    @JsonProperty(required = false, value = "hotelSelection")
    @JsonPropertyDescription("Hotel selection details. Required for HOTEL and COMBO bookings")
    private StorefrontHotelSelectionRequestDto hotelSelection;

    /**
     * Optional combo discount amount when booking both flight and hotel
     */
    @JsonProperty(required = false, value = "comboDiscount")
    @JsonPropertyDescription("Discount amount applied when booking both flight and hotel together")
    private Double comboDiscount;

    /**
     * Additional notes or special requests
     */
    @JsonProperty(required = false, value = "notes")
    @JsonPropertyDescription("Additional notes or special requests for the booking")
    private String notes;

    /**
     * Booking source (STOREFRONT)
     */
    @Builder.Default
    @JsonProperty(required = false, value = "bookingSource")
    @JsonPropertyDescription("Source of the booking. Always 'STOREFRONT' for customer bookings")
    private String bookingSource = "STOREFRONT";
}