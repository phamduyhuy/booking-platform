package com.pdh.flight.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for flight booking details (Flight Service local copy)
 * Contains all information about the selected flight and passengers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlightBookingDetailsDto {
    
    /**
     * Selected flight ID from flight service
     */
    private String flightId;
    
    /**
     * Flight number (e.g., VN123)
     */
    private String flightNumber;
    
    /**
     * Airline code (e.g., VN for Vietnam Airlines)
     */
    private String airline;

    /**
     * Saga schedule identifier (optional)
     */
    @JsonProperty("scheduleId")
    private String scheduleId;

    /**
     * Fare identifier selected for reservation (optional)
     */
    @JsonProperty("fareId")
    private String fareId;
    
    /**
     * Origin airport code (e.g., HAN)
     */
    private String originAirport;
    
    /**
     * Destination airport code (e.g., SGN)
     */
    private String destinationAirport;
    
    /**
     * Departure date and time (ISO 8601 format with timezone, e.g., "2025-11-16T20:45:00+07:00")
     */
    @JsonProperty("departureDateTime")
    private String departureDateTime;
    
    /**
     * Arrival date and time (ISO 8601 format with timezone, e.g., "2025-11-16T22:20:00+07:00")
     */
    @JsonProperty("arrivalDateTime")
    private String arrivalDateTime;
    
    /**
     * Seat class (ECONOMY, BUSINESS, FIRST)
     */
    private String seatClass;
    
    /**
     * Number of passengers
     */
    private Integer passengerCount;
    
    /**
     * Passenger details
     */
    private List<PassengerDetailsDto> passengers;
    
    /**
     * Selected seat numbers (optional)
     */
    private List<String> selectedSeats;
    
    /**
     * Flight price per passenger
     */
    private Double pricePerPassenger;
    
    /**
     * Total flight price (price * passengers)
     */
    private Double totalFlightPrice;
    
    /**
     * Return flight details (for round trip)
     */
    private ReturnFlightDetailsDto returnFlight;
    
    /**
     * Nested class for passenger details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PassengerDetailsDto {
        @JsonProperty("passengerType")
        private String passengerType;

        private String firstName;
        private String lastName;
        @JsonProperty("dateOfBirth")
        private LocalDate dateOfBirth;
        private String title;
        private String email;
        @JsonProperty("phoneNumber")
        private String phone;
        private String nationality;
        @JsonProperty("idNumber")
        private String passportNumber;
        @JsonProperty("passportExpiryDate")
        private LocalDate passportExpiryDate;
        @JsonProperty("passportIssuingCountry")
        private String passportIssuingCountry;
        @JsonProperty("specialAssistance")
        private String specialAssistance;
        @JsonProperty("mealPreference")
        private String mealPreference;
        @JsonProperty("seatPreference")
        private String seatPreference;
        private String specialRequests;
        @JsonProperty("isPrimaryPassenger")
        private Boolean isPrimaryPassenger;
    }
    
    /**
     * Nested class for return flight details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnFlightDetailsDto {
        private String flightId;
        private String flightNumber;
        @JsonProperty("departureDateTime")
        private String departureDateTime;
        @JsonProperty("arrivalDateTime")
        private String arrivalDateTime;
        private String seatClass;
        private List<String> selectedSeats;
        private Double pricePerPassenger;
        private Double totalPrice;
    }
}
