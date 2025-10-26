package com.pdh.booking.model.dto.request;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Payload describing a storefront flight selection.
 * Clients send only identifiers plus pricing and passenger data; the booking service
 * enriches the remaining details via internal service calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorefrontFlightSelectionRequestDto {

    /**
     * Selected flight identifier (as provided by flight service).
     */
    @NotBlank(message = "Flight ID is required")
    @JsonProperty(required = true, value = "flightId")
    @JsonPropertyDescription("Unique identifier of the selected flight from the flight service")
    private String flightId;

    /**
     * Selected schedule identifier (optional but recommended for accuracy).
     */
    @JsonProperty(required = false, value = "scheduleId")
    @JsonPropertyDescription("Identifier of the selected flight schedule for accuracy")
    private String scheduleId;

    /**
     * Selected fare identifier (optional).
     */
    @JsonProperty(required = false, value = "fareId")
    @JsonPropertyDescription("Identifier of the selected fare class")
    private String fareId;

    /**
     * Requested seat class (ECONOMY, BUSINESS, etc.).
     */
    @JsonProperty(required = false, value = "seatClass")
    @JsonPropertyDescription("Requested seat class (ECONOMY, BUSINESS, FIRST)")
    private String seatClass;

    /**
     * Departure timestamp supplied by client (fallback if remote lookup fails).
     */
    @JsonProperty(required = false, value = "departureDateTime")
    @JsonPropertyDescription("Departure date and time (fallback if remote lookup fails)")
    private LocalDateTime departureDateTime;

    /**
     * Arrival timestamp supplied by client (fallback if remote lookup fails).
     */
    @JsonProperty(required = false, value = "arrivalDateTime")
    @JsonPropertyDescription("Arrival date and time (fallback if remote lookup fails)")
    private LocalDateTime arrivalDateTime;

    /**
     * Number of passengers in the booking.
     */
    @NotNull(message = "Passenger count is required")
    @Min(value = 1, message = "Passenger count must be at least 1")
    @JsonProperty(required = true, value = "passengerCount")
    @JsonPropertyDescription("Number of passengers in the booking (minimum 1)")
    private Integer passengerCount;

    /**
     * Passenger manifests captured on the storefront.
     */
    @NotNull(message = "Passenger details are required")
    @Valid
    @JsonProperty(required = true, value = "passengers")
    @JsonPropertyDescription("List of passenger details for the booking")
    private List<PassengerDetailsDto> passengers;

    /**
     * Selected seat numbers (optional).
     */
    @JsonProperty(required = false, value = "selectedSeats")
    @JsonPropertyDescription("List of selected seat numbers (optional)")
    private List<String> selectedSeats;

    /**
     * Optional extra services (baggage, meals, etc.).
     */
    @JsonProperty(required = false, value = "additionalServices")
    @JsonPropertyDescription("Optional extra services like baggage, meals, etc.")
    private List<FlightServiceDto> additionalServices;

    /**
     * Additional notes or requests for the flight.
     */
    @JsonProperty(required = false, value = "specialRequests")
    @JsonPropertyDescription("Special requests or notes for the flight")
    private String specialRequests;

    /**
     * Price per passenger recorded on the storefront (used for audit/comparison).
     */
    @JsonProperty(required = false, value = "pricePerPassenger")
    @JsonPropertyDescription("Price per passenger as recorded on the storefront")
    private Double pricePerPassenger;

    /**
     * Total flight price for all passengers.
     */
    @NotNull(message = "Total flight price is required")
    @Min(value = 0, message = "Total flight price must be positive")
    @JsonProperty(required = true, value = "totalFlightPrice")
    @JsonPropertyDescription("Total flight price for all passengers (must be positive)")
    private Double totalFlightPrice;

    /**
     * Optional airline logo URL captured on storefront.
     */
    @JsonProperty(required = false, value = "airlineLogo")
    @JsonPropertyDescription("URL of the airline logo captured on the storefront")
    private String airlineLogo;

    /**
     * Display name for origin airport from storefront selection.
     */
    @JsonProperty(required = false, value = "originAirportName")
    @JsonPropertyDescription("Display name for the origin airport")
    private String originAirportName;

    /**
     * Display name for destination airport from storefront selection.
     */
    @JsonProperty(required = false, value = "destinationAirportName")
    @JsonPropertyDescription("Display name for the destination airport")
    private String destinationAirportName;

    /**
     * Optional origin airport image URL for UI display.
     */
    @JsonProperty(required = false, value = "originAirportImage")
    @JsonPropertyDescription("URL of the origin airport image for UI display")
    private String originAirportImage;

    /**
     * Optional destination airport image URL for UI display.
     */
    @JsonProperty(required = false, value = "destinationAirportImage")
    @JsonPropertyDescription("URL of the destination airport image for UI display")
    private String destinationAirportImage;
}