package com.pdh.flight.service;

import com.pdh.common.outbox.service.OutboxEventService;
import com.pdh.flight.dto.FlightBookingDetailsDto;
import com.pdh.flight.dto.FlightBookingDetailsDto.ReturnFlightDetailsDto;
import com.pdh.flight.dto.FlightReservationData;
import com.pdh.flight.model.FlightFare;
import com.pdh.flight.model.FlightSchedule;
import com.pdh.flight.model.enums.FareClass;
import com.pdh.flight.repository.FlightFareRepository;
import com.pdh.flight.repository.FlightScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlightService {

    private final OutboxEventService eventPublisher;
    private final FlightFareRepository flightFareRepository;
    private final FlightScheduleRepository flightScheduleRepository;

    @Transactional
    public void reserveFlight(UUID bookingId) {
        // Legacy method for backward compatibility
        log.info("Reserving flight for booking: {} (legacy method)", bookingId);

        // Publish basic success event
        eventPublisher.publishEvent("FlightReserved", "Booking", bookingId.toString(),
                Map.of("eventType", "FlightReserved", "bookingId", bookingId));
    }

    @Transactional
    public void reserveFlight(UUID bookingId, String sagaId, FlightBookingDetailsDto flightDetails) {
        log.info("Reserving flight for booking: {} with detailed product information", bookingId);

        int passengerCount = Optional.ofNullable(flightDetails.getPassengerCount()).orElse(0);
        boolean outboundReserved = false;
        boolean returnReserved = false;
        int outboundRemaining = -1;
        int returnRemaining = -1;

        try {
            if (passengerCount > 0) {
                outboundRemaining = adjustFlightInventory(
                        flightDetails.getFlightId(),
                        flightDetails.getSeatClass(),
                        parseIso8601ToLocalDateTime(flightDetails.getDepartureDateTime()),
                        passengerCount,
                        true
                );
                outboundReserved = true;

                ReturnFlightDetailsDto returnFlight = flightDetails.getReturnFlight();
                if (returnFlight != null && returnFlight.getFlightId() != null) {
                    String returnSeatClass = firstNonNull(returnFlight.getSeatClass(), flightDetails.getSeatClass());
                    returnRemaining = adjustFlightInventory(
                            returnFlight.getFlightId(),
                            returnSeatClass,
                            parseIso8601ToLocalDateTime(returnFlight.getDepartureDateTime()),
                            passengerCount,
                            true
                    );
                    returnReserved = true;
                }
            }

            FlightReservationData flightData = FlightReservationData.builder()
                .flightId(flightDetails.getFlightId())
                .reservationId("FLT-" + bookingId.toString().substring(0, 8))
                .departureDate(extractDateFromIso8601(flightDetails.getDepartureDateTime()))
                .returnDate(flightDetails.getReturnFlight() != null ?
                    extractDateFromIso8601(flightDetails.getReturnFlight().getDepartureDateTime()) : null)
                .passengers(flightDetails.getPassengerCount())
                .seatClass(flightDetails.getSeatClass())
                .amount(BigDecimal.valueOf(flightDetails.getTotalFlightPrice()))
                .build();

            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("eventType", "FlightReserved");
            eventPayload.put("bookingId", bookingId);
            eventPayload.put("sagaId", sagaId);
            eventPayload.put("flightData", flightData);
            eventPayload.put("flightDetails", flightDetails);
            if (outboundReserved) {
                Map<String, Object> inventorySnapshot = new HashMap<>();
                inventorySnapshot.put("outboundRemaining", outboundRemaining);
                if (returnReserved) {
                    inventorySnapshot.put("returnRemaining", returnRemaining);
                }
                eventPayload.put("inventorySnapshot", inventorySnapshot);
            }

            eventPublisher.publishEvent("FlightReserved", "Booking", bookingId.toString(), eventPayload);

            log.info("Flight reserved successfully for booking: {} with flight: {}", bookingId, flightDetails.getFlightId());

        } catch (Exception e) {
            log.error("Failed to reserve flight for booking: {}", bookingId, e);

            if (returnReserved) {
                safeReleaseInventory(
                        flightDetails.getReturnFlight().getFlightId(),
                        firstNonNull(flightDetails.getReturnFlight().getSeatClass(), flightDetails.getSeatClass()),
                        parseIso8601ToLocalDateTime(flightDetails.getReturnFlight().getDepartureDateTime()),
                        passengerCount
                );
            }
            if (outboundReserved) {
                safeReleaseInventory(
                        flightDetails.getFlightId(),
                        flightDetails.getSeatClass(),
                        parseIso8601ToLocalDateTime(flightDetails.getDepartureDateTime()),
                        passengerCount
                );
            }

            Map<String, Object> failurePayload = new HashMap<>();
            failurePayload.put("eventType", "FlightReservationFailed");
            failurePayload.put("bookingId", bookingId);
            failurePayload.put("sagaId", sagaId);
            failurePayload.put("errorMessage", e.getMessage());
            failurePayload.put("flightId", flightDetails.getFlightId());
            failurePayload.put("seatClass", flightDetails.getSeatClass());
            failurePayload.put("passengers", passengerCount);

            eventPublisher.publishEvent("FlightReservationFailed", "Booking", bookingId.toString(), failurePayload);

            throw e;
        }
    }

    @Transactional
    public void cancelFlightReservation(UUID bookingId) {
        // Legacy method for backward compatibility
        log.info("Canceling flight reservation for booking: {} (legacy method)", bookingId);

        eventPublisher.publishEvent("FlightReservationCancelled", "Booking", bookingId.toString(),
                Map.of("eventType", "FlightReservationCancelled", "bookingId", bookingId));
    }

    @Transactional
    public void cancelFlightReservation(UUID bookingId, String sagaId, FlightBookingDetailsDto flightDetails) {
        log.info("Canceling flight reservation for booking: {} with detailed product information", bookingId);

        int passengerCount = Optional.ofNullable(flightDetails.getPassengerCount()).orElse(0);
        if (passengerCount > 0) {
            safeReleaseInventory(
                    flightDetails.getFlightId(),
                    flightDetails.getSeatClass(),
                    parseIso8601ToLocalDateTime(flightDetails.getDepartureDateTime()),
                    passengerCount
            );

            ReturnFlightDetailsDto returnFlight = flightDetails.getReturnFlight();
            if (returnFlight != null && returnFlight.getFlightId() != null) {
                safeReleaseInventory(
                        returnFlight.getFlightId(),
                        firstNonNull(returnFlight.getSeatClass(), flightDetails.getSeatClass()),
                        parseIso8601ToLocalDateTime(returnFlight.getDepartureDateTime()),
                        passengerCount
                );
            }
        }

        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("eventType", "FlightReservationCancelled");
        eventPayload.put("bookingId", bookingId);
        eventPayload.put("sagaId", sagaId);
        eventPayload.put("flightId", flightDetails.getFlightId());
        eventPayload.put("passengers", passengerCount);
        eventPayload.put("seatClass", flightDetails.getSeatClass());

        eventPublisher.publishEvent("FlightReservationCancelled", "Booking", bookingId.toString(), eventPayload);

        log.info("Flight reservation cancelled successfully for booking: {}", bookingId);
    }

    private int adjustFlightInventory(String flightId,
                                      String seatClass,
                                      LocalDateTime departureDateTime,
                                      int passengerCount,
                                      boolean reserve) {
        if (passengerCount <= 0) {
            return -1;
        }

        FareClass fareClass = resolveFareClass(seatClass);
        FlightFare fare = resolveFlightFare(flightId, departureDateTime, fareClass);
        int currentSeats = Optional.ofNullable(fare.getAvailableSeats()).orElse(0);

        if (reserve) {
            if (currentSeats < passengerCount) {
                throw new IllegalStateException(String.format(
                        "Not enough seats for flight %s (%s). Requested %d, available %d",
                        flightId, fareClass, passengerCount, currentSeats));
            }
            fare.setAvailableSeats(currentSeats - passengerCount);
        } else {
            fare.setAvailableSeats(currentSeats + Math.max(passengerCount, 0));
        }

        flightFareRepository.save(fare);
        return fare.getAvailableSeats();
    }

    private void safeReleaseInventory(String flightId,
                                      String seatClass,
                                      LocalDateTime departureDateTime,
                                      int passengerCount) {
        try {
            adjustFlightInventory(flightId, seatClass, departureDateTime, passengerCount, false);
        } catch (Exception releaseError) {
            log.warn("Failed to release flight inventory for flight {}: {}", flightId, releaseError.getMessage(), releaseError);
        }
    }

    private FlightFare resolveFlightFare(String flightId,
                                         LocalDateTime departureDateTime,
                                         FareClass fareClass) {
        UUID scheduleId = tryParseUuid(flightId);
        if (scheduleId != null) {
            return flightFareRepository.findByScheduleIdAndFareClassForUpdate(scheduleId, fareClass)
                    .orElseThrow(() -> new IllegalStateException(String.format(
                            "Fare not found for schedule %s and class %s", scheduleId, fareClass)));
        }

        Long flightNumericId = tryParseLong(flightId);
        if (flightNumericId == null) {
            throw new IllegalStateException("Unsupported flight identifier: " + flightId);
        }
        if (departureDateTime == null) {
            throw new IllegalStateException("Departure date/time is required to resolve flight inventory");
        }

        List<FlightSchedule> schedules = flightScheduleRepository.findByFlightIdAndDate(
                flightNumericId,
                departureDateTime.toLocalDate()
        );

        FlightSchedule bestMatch = selectBestMatchingSchedule(schedules, departureDateTime);
        if (bestMatch == null) {
            throw new IllegalStateException(String.format(
                    "No flight schedule found for flight %s around %s", flightId, departureDateTime));
        }

        return flightFareRepository.findByScheduleIdAndFareClassForUpdate(bestMatch.getScheduleId(), fareClass)
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "Fare not found for schedule %s and class %s", bestMatch.getScheduleId(), fareClass)));
    }

    private FlightSchedule selectBestMatchingSchedule(List<FlightSchedule> schedules, LocalDateTime desiredDeparture) {
        if (schedules == null || schedules.isEmpty()) {
            return null;
        }
        if (desiredDeparture == null) {
            return schedules.get(0);
        }

        return schedules.stream()
                .min(Comparator.comparing(schedule -> {
                    if (schedule.getDepartureTime() == null) {
                        return Long.MAX_VALUE;
                    }
                    ZoneId zoneId = schedule.getDepartureTime().getZone();
                    Duration difference = Duration.between(
                            desiredDeparture.atZone(zoneId),
                            schedule.getDepartureTime()
                    );
                    return Math.abs(difference.toMinutes());
                }))
                .orElse(null);
    }

    private FareClass resolveFareClass(String seatClass) {
        if (seatClass == null || seatClass.isBlank()) {
            return FareClass.ECONOMY;
        }
        String normalized = seatClass.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return FareClass.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown seat class '{}', defaulting to ECONOMY", seatClass);
            return FareClass.ECONOMY;
        }
    }

    private UUID tryParseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Long tryParseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private <T> T firstNonNull(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }

    /**
     * Parse ISO 8601 datetime string to LocalDateTime
     * Supports formats: "2025-11-16T20:45:00+07:00", "2025-11-16T20:45:00Z"
     */
    private LocalDateTime parseIso8601ToLocalDateTime(String iso8601String) {
        if (iso8601String == null || iso8601String.isBlank()) {
            throw new IllegalArgumentException("DateTime string cannot be null or blank");
        }
        try {
            // Parse as OffsetDateTime (handles timezone) then convert to LocalDateTime
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(iso8601String, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return offsetDateTime.toLocalDateTime();
        } catch (Exception ex) {
            log.error("Failed to parse ISO 8601 datetime: {}", iso8601String, ex);
            throw new IllegalArgumentException("Invalid ISO 8601 datetime format: " + iso8601String);
        }
    }

    /**
     * Extract date part from ISO 8601 datetime string
     * Returns format: "YYYY-MM-DD"
     */
    private String extractDateFromIso8601(String iso8601String) {
        if (iso8601String == null || iso8601String.isBlank()) {
            return null;
        }
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(iso8601String, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return offsetDateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ex) {
            log.error("Failed to extract date from ISO 8601 datetime: {}", iso8601String, ex);
            return null;
        }
    }
}
