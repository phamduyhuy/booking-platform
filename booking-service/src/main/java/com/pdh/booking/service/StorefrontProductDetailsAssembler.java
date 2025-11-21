package com.pdh.booking.service;

import com.pdh.booking.client.FlightServiceClient;
import com.pdh.booking.client.HotelServiceClient;
import com.pdh.booking.client.dto.FlightFareDetailsClientResponse;
import com.pdh.booking.client.dto.HotelDetailsClientResponse;
import com.pdh.booking.model.dto.request.ComboBookingDetailsDto;
import com.pdh.booking.model.dto.request.FlightBookingDetailsDto;
import com.pdh.booking.model.dto.request.HotelBookingDetailsDto;
import com.pdh.booking.model.dto.request.StorefrontCreateBookingRequestDto;
import com.pdh.booking.model.dto.request.StorefrontFlightSelectionRequestDto;
import com.pdh.booking.model.dto.request.StorefrontHotelSelectionRequestDto;
import com.pdh.booking.model.enums.BookingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Enriches storefront booking payloads by fetching authoritative product data
 * from the corresponding downstream services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorefrontProductDetailsAssembler {

    private final FlightServiceClient flightServiceClient;
    private final HotelServiceClient hotelServiceClient;

    public Object buildProductDetails(StorefrontCreateBookingRequestDto request) {
        BookingType bookingType = request.getBookingType();
        if (bookingType == null) {
            throw new IllegalArgumentException("Booking type is required");
        }

        return switch (bookingType) {
            case FLIGHT -> buildFlightDetails(request.getFlightSelection());
            case HOTEL -> buildHotelDetails(request.getHotelSelection());
            case COMBO -> buildComboDetails(request);
            default -> throw new IllegalArgumentException("Unsupported booking type: " + bookingType);
        };
    }

    private ComboBookingDetailsDto buildComboDetails(StorefrontCreateBookingRequestDto request) {
        FlightBookingDetailsDto flight = buildFlightDetails(request.getFlightSelection());
        HotelBookingDetailsDto hotel = buildHotelDetails(request.getHotelSelection());

        return ComboBookingDetailsDto.builder()
            .flightDetails(flight)
            .hotelDetails(hotel)
            .comboDiscount(request.getComboDiscount())
            .build();
    }

    private FlightBookingDetailsDto buildFlightDetails(StorefrontFlightSelectionRequestDto selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Flight selection is required for flight booking");
        }

        FlightFareDetailsClientResponse fareDetails = flightServiceClient.getFareDetails(
            selection.getFlightId(),
            selection.getSeatClass(),
            selection.getScheduleId(),
            selection.getFareId()
        );

        if (fareDetails == null) {
            throw new IllegalStateException("Unable to resolve flight details for selection " + selection.getFlightId());
        }

        String departure = resolveDateTime(fareDetails.getDepartureTime(), selection.getDepartureDateTime());
        String arrival = resolveDateTime(fareDetails.getArrivalTime(), selection.getArrivalDateTime());
        String seatClass = firstNonBlank(selection.getSeatClass(), fareDetails.getSeatClass());

        double pricePerPassenger = resolvePricePerPassenger(selection.getPricePerPassenger(), fareDetails.getPrice());
        double totalFlightPrice = resolveTotalFlightPrice(selection.getTotalFlightPrice(), pricePerPassenger, selection.getPassengerCount());

        return FlightBookingDetailsDto.builder()
            .flightId(resolveFlightId(selection.getFlightId(), fareDetails.getFlightId()))
            .flightNumber(fareDetails.getFlightNumber())
            .airline(fareDetails.getAirline())
            .originAirport(fareDetails.getOriginAirport())
            .destinationAirport(fareDetails.getDestinationAirport())
            .originLatitude(fareDetails.getOriginLatitude())
            .originLongitude(fareDetails.getOriginLongitude())
            .destinationLatitude(fareDetails.getDestinationLatitude())
            .destinationLongitude(fareDetails.getDestinationLongitude())
            .departureDateTime(departure)
            .arrivalDateTime(arrival)
            .seatClass(seatClass)
            .scheduleId(selection.getScheduleId())
            .fareId(selection.getFareId())
            .passengerCount(selection.getPassengerCount())
            .passengers(selection.getPassengers())
            .selectedSeats(selection.getSelectedSeats())
            .pricePerPassenger(pricePerPassenger)
            .totalFlightPrice(totalFlightPrice)
            .airlineLogo(firstNonBlank(selection.getAirlineLogo(), fareDetails.getAirlineLogo()))
            .originAirportName(firstNonBlank(selection.getOriginAirportName(), fareDetails.getOriginAirport()))
            .destinationAirportName(firstNonBlank(selection.getDestinationAirportName(), fareDetails.getDestinationAirport()))
            .originAirportImage(firstNonBlank(selection.getOriginAirportImage()))
            .destinationAirportImage(firstNonBlank(selection.getDestinationAirportImage()))
            .additionalServices(selection.getAdditionalServices())
            .specialRequests(selection.getSpecialRequests())
            .build();
    }

    private HotelBookingDetailsDto buildHotelDetails(StorefrontHotelSelectionRequestDto selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Hotel selection is required for hotel booking");
        }

        LocalDate checkIn = selection.getCheckInDate();
        LocalDate checkOut = selection.getCheckOutDate();

        HotelDetailsClientResponse hotelDetails = hotelServiceClient.getHotelDetails(
            selection.getHotelId(),
            checkIn,
            checkOut
        );

        if (hotelDetails == null) {
            throw new IllegalStateException("Unable to resolve hotel details for selection " + selection.getHotelId());
        }

        HotelDetailsClientResponse.RoomType roomType = resolveRoomType(hotelDetails.getRoomTypes(), selection.getRoomTypeId());

        double pricePerNight = Optional.ofNullable(selection.getPricePerNight())
            .orElseGet(() -> Optional.ofNullable(roomType)
                .map(HotelDetailsClientResponse.RoomType::getBasePrice)
                .orElse(0.0));

        double totalRoomPrice = Optional.ofNullable(selection.getTotalRoomPrice())
            .orElseGet(() -> pricePerNight * selection.getNumberOfRooms() * resolveNights(selection));

        return HotelBookingDetailsDto.builder()
            .hotelId(resolveHotelId(selection.getHotelId(), hotelDetails.getHotelId()))
            .hotelName(firstNonBlank(hotelDetails.getName(), "Unknown Hotel"))
            .hotelAddress(firstNonBlank(hotelDetails.getAddress(), ""))
            .city(firstNonBlank(hotelDetails.getCity(), ""))
            .country(firstNonBlank(hotelDetails.getCountry(), ""))
            .hotelLatitude(hotelDetails.getLatitude())
            .hotelLongitude(hotelDetails.getLongitude())
            .starRating(hotelDetails.getStarRating())
            .roomTypeId(selection.getRoomTypeId())
            .roomId(firstNonBlank(selection.getRoomId(), roomType != null ? valueToString(roomType.getId()) : null))
            .roomAvailabilityId(selection.getRoomAvailabilityId())
            .roomType(roomType != null ? firstNonBlank(roomType.getName(), "Room") : "Room")
            .roomName(roomType != null ? firstNonBlank(roomType.getName(), "Room") : "Room")
            .checkInDate(checkIn)
            .checkOutDate(checkOut)
            .numberOfNights(resolveNights(selection))
            .numberOfRooms(selection.getNumberOfRooms())
            .numberOfGuests(selection.getNumberOfGuests())
            .guests(selection.getGuests())
            .pricePerNight(pricePerNight)
            .totalRoomPrice(totalRoomPrice)
            .bedType(selection.getBedType())
            .amenities(resolveAmenities(selection.getAmenities(), roomType, hotelDetails))
            .additionalServices(selection.getAdditionalServices())
            .specialRequests(selection.getSpecialRequests())
            .cancellationPolicy(selection.getCancellationPolicy())
            .hotelImage(firstNonBlank(selection.getHotelImage(), hotelDetails.getPrimaryImage(), firstImage(hotelDetails.getImages())))
            .roomImage(firstNonBlank(selection.getRoomImage(), roomType != null ? roomType.getImage() : null))
            .roomImages(resolveRoomImages(selection.getRoomImages(), roomType, hotelDetails))
            .build();
    }

    private List<String> resolveAmenities(List<String> requested,
                                          HotelDetailsClientResponse.RoomType roomType,
                                          HotelDetailsClientResponse hotelDetails) {
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        if (roomType != null && roomType.getFeatures() != null && !roomType.getFeatures().isEmpty()) {
            return roomType.getFeatures();
        }
        if (hotelDetails.getAmenities() != null && !hotelDetails.getAmenities().isEmpty()) {
            return hotelDetails.getAmenities();
        }
        return List.of();
    }

    private HotelDetailsClientResponse.RoomType resolveRoomType(List<HotelDetailsClientResponse.RoomType> roomTypes,
                                                                String desiredRoomTypeId) {
        if (roomTypes == null || roomTypes.isEmpty() || StringUtils.isBlank(desiredRoomTypeId)) {
            return null;
        }

        for (HotelDetailsClientResponse.RoomType roomType : roomTypes) {
            if (roomType == null) {
                continue;
            }
            String candidateId = valueToString(roomType.getId());
            if (StringUtils.isNotBlank(candidateId) && candidateId.equals(desiredRoomTypeId.trim())) {
                return roomType;
            }
        }

        log.warn("Room type {} not found in hotel response; using first available entry", desiredRoomTypeId);
        return roomTypes.get(0);
    }

    private int resolveNights(StorefrontHotelSelectionRequestDto selection) {
        if (selection.getNumberOfNights() != null && selection.getNumberOfNights() > 0) {
            return selection.getNumberOfNights();
        }
        LocalDate checkIn = selection.getCheckInDate();
        LocalDate checkOut = selection.getCheckOutDate();
        if (checkIn != null && checkOut != null && checkOut.isAfter(checkIn)) {
            return Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
        }
        return 1;
    }

    private String resolveFlightId(String providedFlightId, Long responseFlightId) {
        if (StringUtils.isNotBlank(providedFlightId)) {
            return providedFlightId.trim();
        }
        if (responseFlightId != null) {
            return Long.toString(responseFlightId);
        }
        throw new IllegalArgumentException("Flight ID is required");
    }

    private String resolveHotelId(String providedHotelId, String responseHotelId) {
        if (StringUtils.isNotBlank(providedHotelId)) {
            return providedHotelId.trim();
        }
        if (StringUtils.isNotBlank(responseHotelId)) {
            return responseHotelId.trim();
        }
        throw new IllegalArgumentException("Hotel ID is required");
    }

    private String resolveDateTime(String serviceValue, String fallback) {
        // Prefer service value if available
        if (StringUtils.isNotBlank(serviceValue)) {
            return serviceValue;
        }
        // Otherwise use storefront-provided value (already in ISO 8601 format)
        return fallback;
    }

    private double resolvePricePerPassenger(Double provided, BigDecimal servicePrice) {
        if (provided != null && provided > 0) {
            return provided;
        }
        if (servicePrice != null) {
            return servicePrice.doubleValue();
        }
        return 0d;
    }

    private double resolveTotalFlightPrice(Double provided, double pricePerPassenger, Integer passengerCount) {
        if (provided != null && provided >= 0) {
            return provided;
        }
        int count = passengerCount != null ? passengerCount : 1;
        return pricePerPassenger * Math.max(count, 1);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String valueToString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private List<String> resolveRoomImages(List<String> requested,
                                           HotelDetailsClientResponse.RoomType roomType,
                                           HotelDetailsClientResponse hotelDetails) {
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        if (roomType != null && StringUtils.isNotBlank(roomType.getImage())) {
            return List.of(roomType.getImage());
        }
        if (hotelDetails.getImages() != null && !hotelDetails.getImages().isEmpty()) {
            return hotelDetails.getImages();
        }
        if (StringUtils.isNotBlank(hotelDetails.getPrimaryImage())) {
            return List.of(hotelDetails.getPrimaryImage());
        }
        return List.of();
    }

    private String firstImage(List<String> images) {
        if (images == null) {
            return null;
        }
        return images.stream()
            .filter(StringUtils::isNotBlank)
            .findFirst()
            .map(String::trim)
            .orElse(null);
    }
}
