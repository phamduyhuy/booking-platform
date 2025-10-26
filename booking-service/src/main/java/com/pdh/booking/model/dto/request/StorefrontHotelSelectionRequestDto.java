package com.pdh.booking.model.dto.request;

import java.time.LocalDate;
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
 * Payload describing a storefront hotel selection.
 * The storefront sends identifiers and user-supplied data,
 * while the booking service enriches the remaining details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorefrontHotelSelectionRequestDto {

    /**
     * Selected hotel identifier (as provided by hotel service).
     */
    @NotBlank(message = "Hotel ID is required")
    @JsonProperty(required = true, value = "hotelId")
    @JsonPropertyDescription("Unique identifier of the selected hotel from the hotel service")
    private String hotelId;

    /**
     * Selected room type identifier.
     */
    @NotBlank(message = "Room type ID is required")
    @JsonProperty(required = true, value = "roomTypeId")
    @JsonPropertyDescription("Identifier of the selected room type")
    private String roomTypeId;

    /**
     * Selected concrete room identifier (optional).
     */
    @JsonProperty(required = false, value = "roomId")
    @JsonPropertyDescription("Identifier of the selected concrete room (optional)")
    private String roomId;

    /**
     * Selected availability identifier (optional, used for inventory tracking).
     */
    @JsonProperty(required = false, value = "roomAvailabilityId")
    @JsonPropertyDescription("Identifier of the selected room availability (optional, used for inventory tracking)")
    private String roomAvailabilityId;

    /**
     * Check-in date.
     */
    @NotNull(message = "Check-in date is required")
    @JsonProperty(required = true, value = "checkInDate")
    @JsonPropertyDescription("Check-in date for the hotel stay")
    private LocalDate checkInDate;

    /**
     * Check-out date.
     */
    @NotNull(message = "Check-out date is required")
    @JsonProperty(required = true, value = "checkOutDate")
    @JsonPropertyDescription("Check-out date for the hotel stay")
    private LocalDate checkOutDate;

    /**
     * Total nights calculated on storefront.
     */
    @NotNull(message = "Number of nights is required")
    @Min(value = 1, message = "Number of nights must be at least 1")
    @JsonProperty(required = true, value = "numberOfNights")
    @JsonPropertyDescription("Total number of nights for the stay (minimum 1)")
    private Integer numberOfNights;

    /**
     * Number of rooms requested.
     */
    @NotNull(message = "Number of rooms is required")
    @Min(value = 1, message = "Number of rooms must be at least 1")
    @JsonProperty(required = true, value = "numberOfRooms")
    @JsonPropertyDescription("Number of rooms requested (minimum 1)")
    private Integer numberOfRooms;

    /**
     * Number of guests in total.
     */
    @NotNull(message = "Number of guests is required")
    @Min(value = 1, message = "Number of guests must be at least 1")
    @JsonProperty(required = true, value = "numberOfGuests")
    @JsonPropertyDescription("Total number of guests for the booking (minimum 1)")
    private Integer numberOfGuests;

    /**
     * Guest manifests captured on the storefront.
     */
    @NotNull(message = "Guest details are required")
    @Valid
    @JsonProperty(required = true, value = "guests")
    @JsonPropertyDescription("List of guest details for the booking")
    private List<GuestDetailsDto> guests;

    /**
     * Room price per night recorded on storefront.
     */
    @NotNull(message = "Price per night is required")
    @Min(value = 0, message = "Price per night must be positive")
    @JsonProperty(required = true, value = "pricePerNight")
    @JsonPropertyDescription("Price per night for the room (must be positive)")
    private Double pricePerNight;

    /**
     * Total room price for the stay.
     */
    @NotNull(message = "Total room price is required")
    @Min(value = 0, message = "Total room price must be positive")
    @JsonProperty(required = true, value = "totalRoomPrice")
    @JsonPropertyDescription("Total room price for the entire stay (must be positive)")
    private Double totalRoomPrice;

    /**
     * Preferred bed type.
     */
    @JsonProperty(required = false, value = "bedType")
    @JsonPropertyDescription("Preferred bed type (e.g., SINGLE, DOUBLE, QUEEN, KING)")
    private String bedType;

    /**
     * Selected room amenities (optional references).
     */
    @JsonProperty(required = false, value = "amenities")
    @JsonPropertyDescription("List of selected room amenities")
    private List<String> amenities;

    /**
     * Additional services requested.
     */
    @JsonProperty(required = false, value = "additionalServices")
    @JsonPropertyDescription("List of additional services requested")
    private List<HotelServiceDto> additionalServices;

    /**
     * Additional notes or requests for the stay.
     */
    @JsonProperty(required = false, value = "specialRequests")
    @JsonPropertyDescription("Special requests or notes for the hotel stay")
    private String specialRequests;

    /**
     * Specific cancellation policy snapshot selected on storefront.
     */
    @JsonProperty(required = false, value = "cancellationPolicy")
    @JsonPropertyDescription("Selected cancellation policy for the booking")
    private String cancellationPolicy;

    /**
     * Optional primary hotel image URL captured at selection time.
     */
    @JsonProperty(required = false, value = "hotelImage")
    @JsonPropertyDescription("URL of the primary hotel image captured at selection time")
    private String hotelImage;

    /**
     * Optional primary room image URL captured at selection time.
     */
    @JsonProperty(required = false, value = "roomImage")
    @JsonPropertyDescription("URL of the primary room image captured at selection time")
    private String roomImage;

    /**
     * Optional gallery of room images captured at selection time.
     */
    @JsonProperty(required = false, value = "roomImages")
    @JsonPropertyDescription("List of room image URLs captured at selection time")
    private List<String> roomImages;
}