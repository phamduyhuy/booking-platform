package com.pdh.booking.service;

import com.pdh.booking.model.Booking;
import com.pdh.booking.model.enums.BookingStatus;
import com.pdh.booking.model.enums.BookingType;
import com.pdh.booking.repository.BookingRepository;
import com.pdh.common.utils.AuthenticationUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdh.common.saga.SagaState;
import com.pdh.booking.model.dto.response.BookingHistoryItemDto;
import com.pdh.common.lock.DistributedLock;
import com.pdh.common.lock.DistributedLockManager;
import com.pdh.common.lock.LockResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Booking service using CQRS pattern
 * Handles core booking lifecycle without saga orchestration
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final AnalyticsEventService analyticsEventService;
    private final ObjectMapper objectMapper;
    private final DistributedLockManager distributedLockManager;

    @Value("${booking.reservation.lock-minutes:15}")
    private long reservationLockMinutes;

    /**
     * Create booking entity and emit analytics signal
     */
    @Transactional
    public Booking createBooking(Booking booking) {
        log.info("Creating booking with reference: {} for user: {}", booking.getBookingReference(), booking.getUserId());


        if (booking.getUserId() == null) {
            try {
                String userIdFromToken = AuthenticationUtils.extractUserId();
                booking.setUserId(UUID.fromString(userIdFromToken));
                log.debug("Set userId from JWT token: {}", userIdFromToken);
            } catch (Exception e) {
                log.warn("Could not extract userId from authentication context: {}. Using null.", e.getMessage());
            }
        } else {
            log.debug("Using pre-set userId: {}", booking.getUserId());
        }
        
        booking.setStatus(BookingStatus.PENDING);

        DistributedLock reservationLock = null;
        try {
            reservationLock = assignReservationLock(booking);

            // Save booking first
            Booking savedBooking = bookingRepository.save(booking);

            // Publish analytics event for booking initiated
            analyticsEventService.publishBookingAnalyticsEvent(savedBooking, "booking.initiated");

            log.info("Booking {} created", savedBooking.getBookingReference());

            return savedBooking;
        } catch (Exception ex) {
            if (reservationLock != null && distributedLockManager != null) {
                try {
                    distributedLockManager.releaseLock(reservationLock.getLockId(), booking.getSagaId());
                } catch (Exception releaseEx) {
                    log.warn("Failed to release reservation lock {} after creation failure: {}", reservationLock.getLockId(), releaseEx.getMessage());
                }
            }
            throw ex;
        }
    }

    // Utility methods
    public Optional<Booking> findByBookingId(UUID bookingId) {
        return bookingRepository.findByBookingId(bookingId);
    }

    public Optional<Booking> findBySagaId(String sagaId) {
        return bookingRepository.findBySagaId(sagaId);
    }

    @Transactional
    public Optional<Booking> updateBookingStatus(UUID bookingId, BookingStatus newStatus) {
        return bookingRepository.findByBookingId(bookingId)
            .map(booking -> {
                booking.setStatus(newStatus);
                if (shouldReleaseLock(newStatus)) {
                    releaseLockForBooking(booking);
                }
                if (newStatus == BookingStatus.CANCELLED) {
                    booking.setCancelledAt(ZonedDateTime.now());
                }
                return bookingRepository.save(booking);
            });
    }

    @Transactional(readOnly = true)
    public Page<BookingHistoryItemDto> getBookingHistory(UUID userId, Pageable pageable) {
        Page<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return bookings.map(this::mapToHistoryItem);
    }

    private String generateConfirmationNumber() {
        return "CNF" + System.currentTimeMillis();
    }

    @Transactional
    public Booking confirmBooking(UUID bookingId) {
        return bookingRepository.findByBookingId(bookingId)
            .map(booking -> {
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.setSagaState(SagaState.BOOKING_COMPLETED);
                if (booking.getConfirmationNumber() == null || booking.getConfirmationNumber().isBlank()) {
                    booking.setConfirmationNumber(generateConfirmationNumber());
                }
                releaseLockForBooking(booking);
                return bookingRepository.save(booking);
            })
            .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
    }

    private BookingHistoryItemDto mapToHistoryItem(Booking booking) {
        BookingHistoryItemDto.BookingHistoryItemDtoBuilder builder = BookingHistoryItemDto.builder()
            .bookingId(booking.getBookingId().toString())
            .bookingReference(booking.getBookingReference())
            .bookingType(booking.getBookingType())
            .status(booking.getStatus())
            .sagaState(booking.getSagaState() != null ? booking.getSagaState().name() : null)
            .sagaId(booking.getSagaId())
            .totalAmount(booking.getTotalAmount())
            .currency(booking.getCurrency())
            .createdAt(booking.getCreatedAt() != null ? booking.getCreatedAt().toString() : null)
            .updatedAt(booking.getUpdatedAt() != null ? booking.getUpdatedAt().toString() : null)
            .confirmationNumber(booking.getConfirmationNumber())
            .productDetailsJson(booking.getProductDetailsJson());

        if (booking.getReservationLockedAt() != null) {
            builder.reservationLockedAt(booking.getReservationLockedAt().toString());
        }
        if (booking.getReservationExpiresAt() != null) {
            builder.reservationExpiresAt(booking.getReservationExpiresAt().toString());
        }

        JsonNode productNode = null;
        if (booking.getProductDetailsJson() != null && !booking.getProductDetailsJson().isBlank()) {
            try {
                productNode = objectMapper.readTree(booking.getProductDetailsJson());
            } catch (Exception e) {
                log.warn("Failed to parse product details for booking {}", booking.getBookingId(), e);
            }
        }

        builder.productSummary(buildProductSummary(booking, productNode));
        populateCoordinateMetadata(booking, builder, productNode);

        return builder.build();
    }

    private String buildProductSummary(Booking booking) {
        if (booking.getProductDetailsJson() == null || booking.getProductDetailsJson().isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(booking.getProductDetailsJson());
            return buildProductSummary(booking, root);
        } catch (Exception e) {
            log.warn("Failed to build product summary for booking {}", booking.getBookingId(), e);
            return null;
        }
    }

    private String buildProductSummary(Booking booking, JsonNode root) {
        if (root == null) {
            return null;
        }

        try {
            return switch (booking.getBookingType()) {
                case FLIGHT -> summarizeFlight(root);
                case HOTEL -> summarizeHotel(root);
                case COMBO -> summarizeCombo(root);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Failed to build product summary for booking {}", booking.getBookingId(), e);
            return null;
        }
    }

    private void populateCoordinateMetadata(Booking booking, BookingHistoryItemDto.BookingHistoryItemDtoBuilder builder, JsonNode root) {
        if (root == null) {
            return;
        }

        try {
            switch (booking.getBookingType()) {
                case FLIGHT -> applyFlightCoordinates(builder, root);
                case HOTEL -> applyHotelCoordinates(builder, root);
                case COMBO -> {
                    JsonNode flightNode = root.path("flightDetails");
                    JsonNode hotelNode = root.path("hotelDetails");
                    if (!flightNode.isMissingNode()) {
                        applyFlightCoordinates(builder, flightNode);
                    }
                    if (!hotelNode.isMissingNode()) {
                        applyHotelCoordinates(builder, hotelNode);
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            log.warn("Failed to populate coordinate metadata for booking {}", booking.getBookingId(), e);
        }
    }

    private String summarizeFlight(JsonNode node) {
        String flightNumber = node.path("flightNumber").asText(null);
        String airline = node.path("airline").asText(null);
        String origin = node.path("originAirport").asText(null);
        String destination = node.path("destinationAirport").asText(null);

        if (flightNumber == null && airline == null && origin == null && destination == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        if (flightNumber != null) {
            builder.append(flightNumber);
        }
        if (airline != null) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(airline);
        }
        if (origin != null || destination != null) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(origin != null ? origin : "?")
                .append(" → ")
                .append(destination != null ? destination : "?");
        }
        return builder.toString();
    }

    private String summarizeHotel(JsonNode node) {
        String hotelName = node.path("hotelName").asText(null);
        String city = node.path("city").asText(null);
        if (hotelName == null && city == null) {
            return null;
        }
        if (hotelName != null && city != null) {
            return hotelName + " · " + city;
        }
        return hotelName != null ? hotelName : city;
    }

    private String summarizeCombo(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        JsonNode flight = node.path("flightDetails");
        JsonNode hotel = node.path("hotelDetails");
        if (!flight.isMissingNode()) {
            String flightSummary = summarizeFlight(flight);
            if (flightSummary != null) {
                builder.append(flightSummary);
            }
        }
        if (!hotel.isMissingNode()) {
            String hotelSummary = summarizeHotel(hotel);
            if (hotelSummary != null) {
                if (builder.length() > 0) {
                    builder.append(" • ");
                }
                builder.append(hotelSummary);
            }
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private void applyFlightCoordinates(BookingHistoryItemDto.BookingHistoryItemDtoBuilder builder, JsonNode node) {
        builder.originLatitude(getDouble(node, "originLatitude"));
        builder.originLongitude(getDouble(node, "originLongitude"));
        builder.destinationLatitude(getDouble(node, "destinationLatitude"));
        builder.destinationLongitude(getDouble(node, "destinationLongitude"));
    }

    private void applyHotelCoordinates(BookingHistoryItemDto.BookingHistoryItemDtoBuilder builder, JsonNode node) {
        builder.hotelLatitude(getDouble(node, "hotelLatitude"));
        builder.hotelLongitude(getDouble(node, "hotelLongitude"));
    }

    private Double getDouble(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isNumber()) {
            return valueNode.asDouble();
        }
        if (valueNode.isTextual()) {
            try {
                return Double.parseDouble(valueNode.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private DistributedLock assignReservationLock(Booking booking) {
        if (distributedLockManager == null || reservationLockMinutes <= 0) {
            return null;
        }

        if (booking.getSagaId() == null || booking.getSagaId().isBlank()) {
            booking.setSagaId(UUID.randomUUID().toString());
        }

        Duration timeout = Duration.ofMinutes(Math.max(1, reservationLockMinutes));
        String resourceKey = "booking:" + booking.getBookingId();

        DistributedLock lock = distributedLockManager.acquireLock(
                resourceKey,
                LockResourceType.BOOKING,
                booking.getSagaId(),
                timeout)
            .orElseThrow(() -> new IllegalStateException("Unable to reserve booking items. Please try again."));

        ZonedDateTime lockedAt = ZonedDateTime.now();
        booking.setReservationLockId(lock.getLockId());
        booking.setReservationLockedAt(lockedAt);
        booking.setReservationExpiresAt(lockedAt.plus(timeout));

        return lock;
    }

    private void releaseLockForBooking(Booking booking) {
        if (booking == null) {
            return;
        }
        try {
            if (distributedLockManager != null && booking.getReservationLockId() != null && booking.getSagaId() != null) {
                distributedLockManager.releaseLock(booking.getReservationLockId(), booking.getSagaId());
            }
        } catch (Exception e) {
            log.warn("Failed to release reservation lock for booking {}: {}", booking.getBookingId(), e.getMessage());
        } finally {
            clearReservationLockMetadata(booking);
        }
    }

    private void clearReservationLockMetadata(Booking booking) {
        booking.setReservationLockId(null);
        booking.setReservationLockedAt(null);
        booking.setReservationExpiresAt(null);
    }

    private boolean shouldReleaseLock(BookingStatus status) {
        return status == BookingStatus.CANCELLED
                || status == BookingStatus.FAILED
                || status == BookingStatus.PAYMENT_FAILED
                || status == BookingStatus.CONFIRMED;
    }

    @Transactional
    public void expireReservation(Booking booking, String reason) {
        if (booking == null) {
            return;
        }
        releaseLockForBooking(booking);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setSagaState(SagaState.BOOKING_CANCELLED);
        booking.setCancelledAt(ZonedDateTime.now());
        booking.setCancellationReason(reason);
        bookingRepository.save(booking);
    }

    public void releaseReservationLock(Booking booking) {
        if (booking == null) {
            return;
        }
        releaseLockForBooking(booking);
    }
}
