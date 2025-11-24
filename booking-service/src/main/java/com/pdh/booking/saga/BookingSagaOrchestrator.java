package com.pdh.booking.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdh.booking.model.Booking;
import com.pdh.booking.model.BookingSagaInstance;
import com.pdh.booking.model.SagaStateLog;
import com.pdh.booking.model.dto.request.ComboBookingDetailsDto;
import com.pdh.booking.model.dto.request.FlightBookingDetailsDto;
import com.pdh.booking.model.dto.request.GuestDetailsDto;
import com.pdh.booking.model.dto.request.HotelBookingDetailsDto;
import com.pdh.booking.model.dto.request.PassengerDetailsDto;
import com.pdh.booking.model.enums.BookingStatus;
import com.pdh.booking.model.enums.BookingType;
import com.pdh.booking.repository.BookingRepository;
import com.pdh.booking.repository.BookingSagaRepository;
import com.pdh.booking.repository.SagaStateLogRepository;
import com.pdh.booking.service.BookingOutboxEventService;
import com.pdh.booking.service.BookingService;
import com.pdh.booking.service.ProductDetailsService;
import com.pdh.common.saga.SagaCommand;
import com.pdh.common.saga.SagaState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingSagaOrchestrator {

    private static final String BOOKING_SAGA_COMMAND_TOPIC = "booking-saga-commands";
    private static final String PAYMENT_SAGA_COMMAND_TOPIC = "payment-saga-commands";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final BookingSagaRepository sagaRepository;
    private final BookingRepository bookingRepository;
    private final SagaStateLogRepository sagaStateLogRepository;
    private final ProductDetailsService productDetailsService;
    private final BookingOutboxEventService bookingOutboxEventService;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;
    @Qualifier("sagaCommandKafkaTemplate")
    private final KafkaTemplate<String, String> sagaCommandKafkaTemplate;

    /**
     * Start saga orchestration for a booking.
     */
    @Transactional
    public void startSaga(UUID bookingId) {
        Booking booking = bookingRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        BookingSagaInstance saga = sagaRepository.findByBookingId(bookingId)
                .orElseGet(() -> sagaRepository.save(BookingSagaInstance.builder()
                        .sagaId(booking.getSagaId())
                        .bookingId(bookingId)
                        .currentState(SagaState.BOOKING_INITIATED)
                        .build()));

        updateBookingState(booking, SagaState.BOOKING_INITIATED, BookingStatus.PENDING);
        logStateTransition(saga, null, SagaState.BOOKING_INITIATED, "SagaStarted", null, null);

        if (booking.getBookingType() == BookingType.FLIGHT || booking.getBookingType() == BookingType.COMBO) {
            transitionState(saga, SagaState.FLIGHT_RESERVATION_PENDING, "FlightReservationRequested", null, null);
            publishFlightReservationCommand(booking, saga);
            return;
        }

        if (booking.getBookingType() == BookingType.HOTEL) {
            transitionState(saga, SagaState.HOTEL_RESERVATION_PENDING, "HotelReservationRequested", null, null);
            publishHotelReservationCommand(booking, saga);
            return;
        }

        transitionState(saga, SagaState.PAYMENT_PENDING, "PaymentRequested", null, null);
    }

    // ==== Kafka Listeners ====

    @Transactional
    @KafkaListener(topics = { "booking.Booking.events",
            "booking.Flight.events",
            "booking.Hotel.events",
            "booking.Payment.events" }, groupId = "booking-saga-outbox-listener", containerFactory = "bookingOutboxListenerContainerFactory")
    public void handleOutboxEvents(@Payload JsonNode message,
            @Header(value = "eventType", required = false) String eventTypeHeader) {

        if (message == null || message.isNull())
            return;

        // 1) Chuẩn hoá root (gỡ double-encoded nếu có)
        JsonNode root = normalize(message);

        // 2) Lấy payload node (nếu có), cũng normalize
        JsonNode payload = root.path("payload");
        if (payload.isMissingNode() || payload.isNull()) {
            payload = root;
        } else if (payload.isTextual()) {
            payload = normalize(payload);
        }

        // 3) Determine eventType, prioritizing the reliable Kafka header
        String eventType = eventTypeHeader;
        if (isBlank(eventType)) {
            log.debug("eventType header is blank, falling back to body parsing.");
            eventType = text(root, "eventType");
            if (isBlank(eventType))
                eventType = text(payload, "eventType");
            // Fallback cuối cùng: nếu producer lỡ đặt "type"
            if (isBlank(eventType))
                eventType = text(root, "type");
            if (isBlank(eventType))
                eventType = text(payload, "type");
        }
        
        // Trim whitespace and normalize
        if (eventType != null) {
            eventType = eventType.trim();
        }

        log.debug("EVENT TYPE HEADER: {}", eventTypeHeader);
        log.debug("DETERMINED EVENT TYPE: {}", eventType);
        log.debug("EVENT TYPE LENGTH: {}, TRIMMED: '{}'", eventType != null ? eventType.length() : 0, eventType != null ? eventType.trim() : "null");

        if (isBlank(eventType)) {
            log.warn("Saga outbox event ignored due to missing eventType. message={}", root.toString());
            return;
        }

        // 4) Payload phẳng để downstream xử lý
        String payloadJson = payload.toString();
        
        log.info("About to process saga event: type='{}', payloadLength={}", eventType, payloadJson.length());

        switch (eventType) {
            case "FlightReserved", "FlightReservationFailed", "FlightReservationCancelled" -> {
                log.info("Processing flight saga event: {}", eventType);
                processSagaCallback(payloadJson, eventType, this::handleFlightEvent);
            }
            case "HotelReserved", "HotelReservationFailed", "HotelReservationCancelled" -> {
                log.info("Processing hotel saga event: {}", eventType);
                processSagaCallback(payloadJson, eventType, this::handleHotelEvent);
            }
            case "PaymentProcessed", "PaymentFailed", "PaymentRefunded", "PaymentCancelled" -> {
                log.info("Processing payment saga event: {} with payload: {}", eventType, payloadJson);
                processSagaCallback(payloadJson, eventType, this::handlePaymentEvent);
            }
            default ->
                log.debug("Ignoring outbox event type {} for saga orchestration", eventType);
        }
    }

    private JsonNode normalize(JsonNode node) {
        try {
            // Nếu node là chuỗi JSON -> parse thành object
            if (node != null && node.isTextual()) {
                return objectMapper.readTree(node.asText());
            }
            return node;
        } catch (Exception e) {
            log.warn("Failed to normalize JSON node: {}", node, e);
            return node;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null)
            return null;
        JsonNode v = node.get(field);
        return (v != null && v.isTextual()) ? v.asText() : null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private interface SagaEventHandler {
        void handle(BookingSagaInstance saga, Booking booking, String eventType, JsonNode payload) throws Exception;
    }

    private void processSagaCallback(String payloadJson, String eventType, SagaEventHandler handler) {
        log.info("processSagaCallback called: eventType='{}', payloadLength={}", eventType, payloadJson != null ? payloadJson.length() : 0);
        
        if (StringUtils.isBlank(payloadJson)) {
            log.warn("processSagaCallback: payloadJson is blank for eventType={}", eventType);
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(payloadJson);
            JsonNode bookingIdNode = payload.get("bookingId");
            if (bookingIdNode == null || bookingIdNode.isNull()) {
                log.warn("Saga callback ignored due to missing bookingId: type={} payload={}", eventType, payloadJson);
                return;
            }

            UUID bookingId = UUID.fromString(bookingIdNode.asText());
            log.info("Looking for saga with bookingId={} for eventType={}", bookingId, eventType);
            
            Optional<BookingSagaInstance> sagaOpt = sagaRepository.findByBookingId(bookingId);
            Optional<Booking> bookingOpt = bookingRepository.findByBookingId(bookingId);

            if (sagaOpt.isEmpty() || bookingOpt.isEmpty()) {
                log.warn("Saga callback ignored because saga or booking not found: bookingId={}, type={}, sagaExists={}, bookingExists={}", 
                        bookingId, eventType, sagaOpt.isPresent(), bookingOpt.isPresent());
                return;
            }

            BookingSagaInstance saga = sagaOpt.get();
            if (saga.isCompleted()) {
                log.debug("Ignoring saga callback {} because saga {} already completed", eventType, saga.getSagaId());
                return;
            }

            log.info("Invoking handler for eventType={}, saga={}, bookingId={}", eventType, saga.getSagaId(), bookingId);
            handler.handle(saga, bookingOpt.get(), eventType, payload);
            log.info("Handler completed successfully for eventType={}, saga={}", eventType, saga.getSagaId());
        } catch (Exception e) {
            log.error("Error processing saga callback eventType={} payload={} ", eventType, payloadJson, e);
        }
    }

    private String resolveEventType(JsonNode message, String eventTypeHeader) {
        if (StringUtils.isNotBlank(eventTypeHeader)) {
            return eventTypeHeader;
        }
        return readFirstTextValue(message, "type", "eventType", "event_type");
    }

    private String readFirstTextValue(JsonNode node, String... fieldNames) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode valueNode = node.get(fieldName);
            if (valueNode != null && !valueNode.isNull()) {
                String value = valueNode.asText();
                if (StringUtils.isNotBlank(value) && !"null".equalsIgnoreCase(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private String extractEventType(JsonNode node) {
        return readFirstTextValue(node, "eventType", "event_type", "type");
    }

    private String determineEventType(String headerValue, JsonNode root, JsonNode payload) {
        if (StringUtils.isNotBlank(headerValue)) {
            return headerValue;
        }

        String eventType = extractEventType(root);
        if (StringUtils.isNotBlank(eventType)) {
            return eventType;
        }

        if (payload != null) {
            eventType = extractEventType(payload);
            if (StringUtils.isNotBlank(eventType)) {
                return eventType;
            }
        }

        JsonNode probe = payload != null ? payload : root;
        if (probe != null) {
            if (hasNonNull(probe, "eventType", "event_type", "type")) {
                eventType = readFirstTextValue(probe, "eventType", "event_type", "type");
                if (StringUtils.isNotBlank(eventType)) {
                    return eventType;
                }
            }

            if (hasNonNull(probe, "flightData", "flightDetails")) {
                return "FlightReserved";
            }
            if (hasNonNull(probe, "hotelData", "hotelDetails")) {
                return "HotelReserved";
            }
            if (hasNonNull(probe, "payment", "paymentId", "transactionId")) {
                return "PaymentProcessed";
            }
        }

        return null;
    }

    private String extractPayloadJson(JsonNode payloadNode) {
        if (payloadNode == null || payloadNode.isNull()) {
            return null;
        }
        if (payloadNode.isTextual()) {
            return payloadNode.asText();
        }
        try {
            return objectMapper.writeValueAsString(payloadNode);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize outbox payload node", e);
            return null;
        }
    }

    private boolean hasNonNull(JsonNode node, String... fieldNames) {
        if (node == null || node.isNull()) {
            return false;
        }
        for (String fieldName : fieldNames) {
            JsonNode child = node.get(fieldName);
            if (child != null && !child.isNull()) {
                return true;
            }
        }
        return false;
    }

    private void handleFlightEvent(BookingSagaInstance saga, Booking booking, String eventType, JsonNode payload) {
        switch (eventType) {
            case "FlightReserved" -> onFlightReserved(saga, booking, payload);
            case "FlightReservationFailed" -> onFlightReservationFailed(saga, booking, payload);
            case "FlightReservationCancelled" -> onFlightReservationCancelled(saga, booking, payload);
            default -> log.debug("Ignoring flight event {} for saga {}", eventType, saga.getSagaId());
        }
    }

    private void handleHotelEvent(BookingSagaInstance saga, Booking booking, String eventType, JsonNode payload) {
        switch (eventType) {
            case "HotelReserved" -> onHotelReserved(saga, booking, payload);
            case "HotelReservationFailed" -> onHotelReservationFailed(saga, booking, payload);
            case "HotelReservationCancelled" -> onHotelReservationCancelled(saga, booking, payload);
            default -> log.debug("Ignoring hotel event {} for saga {}", eventType, saga.getSagaId());
        }
    }

    private void handlePaymentEvent(BookingSagaInstance saga, Booking booking, String eventType, JsonNode payload) {
        switch (eventType) {
            case "PaymentProcessed" -> onPaymentProcessed(saga, booking, payload);
            case "PaymentFailed" -> onPaymentFailed(saga, booking, payload);
            case "PaymentRefunded" -> onPaymentRefunded(saga, booking, payload);
            case "PaymentCancelled" -> onPaymentCancelled(saga, booking, payload);
            default -> log.debug("Ignoring payment event {} for saga {}", eventType, saga.getSagaId());
        }
    }

    // ==== Flight handlers ====

    @Transactional
    void onFlightReserved(BookingSagaInstance saga, Booking booking, JsonNode payload) {
        log.info("Flight reserved for booking {} saga {}", booking.getBookingId(), saga.getSagaId());
        transitionState(saga, SagaState.FLIGHT_RESERVED, "FlightReserved", payload, null);

        if (booking.getBookingType() == BookingType.COMBO) {
            transitionState(saga, SagaState.HOTEL_RESERVATION_PENDING, "HotelReservationRequested", null, null);
            publishHotelReservationCommand(booking, saga);
        } else {
            transitionState(saga, SagaState.PAYMENT_PENDING, "PaymentRequested", null, null);
        }
    }

    @Transactional
    void onFlightReservationFailed(BookingSagaInstance saga, Booking booking, JsonNode payload) {
        log.warn("Flight reservation failed for booking {} saga {}", booking.getBookingId(), saga.getSagaId());
        cancelSaga(saga, booking, BookingStatus.VALIDATION_FAILED, "FlightReservationFailed", payload,
                "Flight reservation failed");
    }

    @Transactional
    void onFlightReservationCancelled(BookingSagaInstance saga, Booking booking, JsonNode payload) {
        log.info("Flight reservation cancellation acknowledged for booking {} saga {}", booking.getBookingId(),
                saga.getSagaId());
    }

    // ==== Hotel handlers ====

    @Transactional
    void onHotelReserved(BookingSagaInstance saga, Booking booking, JsonNode payload) {
        log.info("Hotel reserved for booking {} saga {}", booking.getBookingId(), saga.getSagaId());
        transitionState(saga, SagaState.HOTEL_RESERVED, "HotelReserved", payload, null);
        transitionState(saga, SagaState.PAYMENT_PENDING, "PaymentRequested", null, null);
    }

    @Transactional
    void onHotelReservationFailed(BookingSagaInstance saga, Booking booking, JsonNode payload) {
        log.warn("Hotel reservation failed for booking {} saga {}", booking.getBookingId(), saga.getSagaId());
        String failureReason = resolveHotelFailureReason(payload);
        if (hasFlight(booking)) {
            requestFlightCancellation(saga, booking, payload, failureReason);
        }
        cancelSaga(saga, booking, BookingStatus.VALIDATION_FAILED, "HotelReservationFailed", payload,
                failureReason);
    }

    @Transactional
    void onHotelReservationCancelled(BookingSagaInstance saga, Booking booking, JsonNode payload) {
        log.info("Hotel reservation cancellation acknowledged for booking {} saga {}", booking.getBookingId(),
                saga.getSagaId());
    }

    // ==== Payment handlers ====

    @Transactional
    void onPaymentProcessed(BookingSagaInstance saga, Booking booking, JsonNode payload) {
        log.info("Payment processed for booking {} saga {} - Current state: {}, Booking status: {}", 
                booking.getBookingId(), saga.getSagaId(), saga.getCurrentState(), booking.getStatus());
        
        transitionState(saga, SagaState.PAYMENT_COMPLETED, "PaymentProcessed", payload, null);
        
        Map<String, Object> extras = new HashMap<>();
        extras.put("emailTemplate", "booking-payment.ftl");
        Map<String, Object> paymentDetails = convertJsonNodeToMap(payload);
        if (!paymentDetails.isEmpty()) {
            extras.put("payment", paymentDetails);
        }
        publishNotificationEvent(booking, "BookingPaymentSucceeded", extras);
        
        completeSaga(saga, booking, payload);
        
        log.info("Payment processed completed - Saga state: {}, Booking status: {}", 
                saga.getCurrentState(), booking.getStatus());
    }

    @Transactional
    void onPaymentFailed(BookingSagaInstance saga, Booking booking, JsonNode payload) {
        log.warn("Payment failed for booking {} saga {}", booking.getBookingId(), saga.getSagaId());
        if (hasHotel(booking)) {
            requestHotelCancellation(saga, booking, payload, "Payment failed");
        }
        if (hasFlight(booking)) {
            requestFlightCancellation(saga, booking, payload, "Payment failed");
        }
        cancelSaga(saga, booking, BookingStatus.PAYMENT_FAILED, "PaymentFailed", payload, "Payment processing failed");
    }

    @Transactional
    void onPaymentRefunded(BookingSagaInstance saga, Booking booking, JsonNode payload) {
        log.info("Payment refunded for booking {} saga {}", booking.getBookingId(), saga.getSagaId());
        transitionState(saga, SagaState.COMPENSATION_PAYMENT_REFUND, "PaymentRefunded", payload, null);
        cancelSaga(saga, booking, BookingStatus.CANCELLED, "PaymentRefunded", payload, "Payment refunded");
    }

    @Transactional
    void onPaymentCancelled(BookingSagaInstance saga, Booking booking, JsonNode payload) {
        log.info("Payment cancellation acknowledged for booking {} saga {}", booking.getBookingId(), saga.getSagaId());
    }

    // ==== Command publishing ====

    private void publishFlightReservationCommand(Booking booking, BookingSagaInstance saga) {
        SagaCommand command = SagaCommand.builder()
                .sagaId(saga.getSagaId())
                .bookingId(booking.getBookingId())
                .customerId(booking.getUserId())
                .bookingType(booking.getBookingType().name())
                .totalAmount(toBigDecimal(booking.getTotalAmount()))
                .action("RESERVE_FLIGHT")
                .flightDetails(productDetailsService.getFlightDetails(booking))
                .build();

        sendCommand(command, BOOKING_SAGA_COMMAND_TOPIC);
    }

    private void publishHotelReservationCommand(Booking booking, BookingSagaInstance saga) {
        SagaCommand command = SagaCommand.builder()
                .sagaId(saga.getSagaId())
                .bookingId(booking.getBookingId())
                .customerId(booking.getUserId())
                .bookingType(booking.getBookingType().name())
                .totalAmount(toBigDecimal(booking.getTotalAmount()))
                .action("RESERVE_HOTEL")
                .hotelDetails(productDetailsService.getHotelDetails(booking))
                .build();

        sendCommand(command, BOOKING_SAGA_COMMAND_TOPIC);
    }

    private void sendCommand(SagaCommand command, String topic) {
        try {
            String payload = objectMapper.writeValueAsString(command);
            sagaCommandKafkaTemplate.send(topic, command.getSagaId(), payload);
            log.debug("Saga command sent: topic={} action={} sagaId={}", topic, command.getAction(),
                    command.getSagaId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize saga command", e);
        }
    }

    private void requestFlightCancellation(BookingSagaInstance saga,
            Booking booking,
            JsonNode payload,
            String reason) {
        transitionState(saga, SagaState.COMPENSATION_FLIGHT_CANCEL, "FlightCancellationRequested", payload, reason);
        publishFlightCancellationCommand(booking, saga, reason);
    }

    private void requestHotelCancellation(BookingSagaInstance saga,
            Booking booking,
            JsonNode payload,
            String reason) {
        transitionState(saga, SagaState.COMPENSATION_HOTEL_CANCEL, "HotelCancellationRequested", payload, reason);
        publishHotelCancellationCommand(booking, saga, reason);
    }

    private void publishFlightCancellationCommand(Booking booking,
            BookingSagaInstance saga,
            String reason) {
        SagaCommand command = SagaCommand.builder()
                .sagaId(saga.getSagaId())
                .bookingId(booking.getBookingId())
                .customerId(booking.getUserId())
                .bookingType(booking.getBookingType().name())
                .totalAmount(toBigDecimal(booking.getTotalAmount()))
                .action("CANCEL_FLIGHT_RESERVATION")
                .flightDetails(productDetailsService.getFlightDetails(booking))
                .build();

        command.addMetadata("isCompensation", "true");
        if (reason != null) {
            command.addMetadata("reason", reason);
        }
        sendCommand(command, BOOKING_SAGA_COMMAND_TOPIC);
    }

    private void publishHotelCancellationCommand(Booking booking,
            BookingSagaInstance saga,
            String reason) {
        SagaCommand command = SagaCommand.builder()
                .sagaId(saga.getSagaId())
                .bookingId(booking.getBookingId())
                .customerId(booking.getUserId())
                .bookingType(booking.getBookingType().name())
                .totalAmount(toBigDecimal(booking.getTotalAmount()))
                .action("CANCEL_HOTEL_RESERVATION")
                .hotelDetails(productDetailsService.getHotelDetails(booking))
                .build();

        command.addMetadata("isCompensation", "true");
        if (reason != null) {
            command.addMetadata("reason", reason);
        }
        sendCommand(command, BOOKING_SAGA_COMMAND_TOPIC);
    }

    private void publishNotificationEvent(Booking booking, String eventType, Map<String, Object> extras) {
        try {
            Map<String, Object> payload = buildNotificationPayload(booking);
            payload.put("eventType", eventType);
            if (extras != null && !extras.isEmpty()) {
                payload.putAll(extras);
            }
            bookingOutboxEventService.publishEvent(eventType, "Booking", booking.getBookingId().toString(), payload);
        } catch (Exception ex) {
            log.warn("Failed to publish notification event {} for booking {}", eventType, booking.getBookingId(), ex);
        }
    }

    private Map<String, Object> buildNotificationPayload(Booking booking) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bookingId", booking.getBookingId());
        payload.put("bookingReference", booking.getBookingReference());
        payload.put("bookingType", booking.getBookingType() != null ? booking.getBookingType().name() : null);
        payload.put("userId", booking.getUserId());
        payload.put("status", booking.getStatus() != null ? booking.getStatus().name() : null);
        payload.put("sagaState", booking.getSagaState() != null ? booking.getSagaState().name() : null);
        payload.put("totalAmount", booking.getTotalAmount());
        payload.put("currency", booking.getCurrency());
        payload.put("sagaId", booking.getSagaId());
        payload.put("confirmationNumber", booking.getConfirmationNumber());
        payload.put("createdAt", booking.getCreatedAt());
        payload.put("updatedAt", booking.getUpdatedAt());
        Map<String, Object> contact = extractPrimaryContact(booking);
        if (!contact.isEmpty()) {
            payload.put("contact", contact);
        }
        Map<String, Object> productDetails = extractProductDetails(booking);
        if (!productDetails.isEmpty()) {
            payload.put("productDetails", productDetails);
        }
        return payload;
    }

    private Map<String, Object> extractProductDetails(Booking booking) {
        Map<String, Object> details = new HashMap<>();
        if (hasFlight(booking)) {
            FlightBookingDetailsDto flightDetails = productDetailsService.getFlightDetails(booking);
            Map<String, Object> flight = convertObjectToMap(flightDetails);
            if (!flight.isEmpty()) {
                details.put("flight", flight);
            }
        }
        if (hasHotel(booking)) {
            HotelBookingDetailsDto hotelDetails = productDetailsService.getHotelDetails(booking);
            Map<String, Object> hotel = convertObjectToMap(hotelDetails);
            if (!hotel.isEmpty()) {
                details.put("hotel", hotel);
            }
        }
        if (booking.getBookingType() == BookingType.COMBO) {
            ComboBookingDetailsDto combo = (ComboBookingDetailsDto) productDetailsService.convertFromJson(booking.getBookingType(), booking.getProductDetailsJson());
            Map<String, Object> comboDetails = convertObjectToMap(combo);
            if (!comboDetails.isEmpty()) {
                details.put("combo", comboDetails);
            }
        }
        return details;
    }

    private Map<String, Object> convertObjectToMap(Object source) {
        if (source == null) {
            return new HashMap<>();
        }
        try {
            return objectMapper.convertValue(source, MAP_TYPE);
        } catch (IllegalArgumentException ex) {
            log.warn("Failed to convert object to map for notifications: {}", source, ex);
            return new HashMap<>();
        }
    }

    private Map<String, Object> extractPrimaryContact(Booking booking) {
        Map<String, Object> contact = new HashMap<>();

        if (hasFlight(booking)) {
            FlightBookingDetailsDto flightDetails = productDetailsService.getFlightDetails(booking);
            Map<String, Object> fromFlight = contactFromFlight(flightDetails);
            if (!fromFlight.isEmpty()) {
                contact.putAll(fromFlight);
            }
        }

        if (contact.isEmpty() && hasHotel(booking)) {
            HotelBookingDetailsDto hotelDetails = productDetailsService.getHotelDetails(booking);
            Map<String, Object> fromHotel = contactFromHotel(hotelDetails);
            if (!fromHotel.isEmpty()) {
                contact.putAll(fromHotel);
            }
        }

        return contact;
    }

    private Map<String, Object> contactFromFlight(FlightBookingDetailsDto flightDetails) {
        if (flightDetails == null || flightDetails.getPassengers() == null) {
            return new HashMap<>();
        }
        // Prefer passengers with explicit email
        Map<String, Object> fallback = new HashMap<>();
        for (PassengerDetailsDto passenger : flightDetails.getPassengers()) {
            Map<String, Object> data = contactFromPassenger(passenger);
            if (data.isEmpty()) {
                continue;
            }
            if (StringUtils.isNotBlank((String) data.get("email"))) {
                return data;
            }
            if (fallback.isEmpty()) {
                fallback.putAll(data);
            }
        }
        return fallback;
    }

    private Map<String, Object> contactFromHotel(HotelBookingDetailsDto hotelDetails) {
        if (hotelDetails == null || hotelDetails.getGuests() == null) {
            return new HashMap<>();
        }
        Map<String, Object> fallback = new HashMap<>();
        for (GuestDetailsDto guest : hotelDetails.getGuests()) {
            Map<String, Object> data = contactFromGuest(guest);
            if (data.isEmpty()) {
                continue;
            }
            if ("PRIMARY".equalsIgnoreCase(guest.getGuestType()) && StringUtils.isNotBlank((String) data.get("email"))) {
                return data;
            }
            if (StringUtils.isNotBlank((String) data.get("email"))) {
                return data;
            }
            if (fallback.isEmpty()) {
                fallback.putAll(data);
            }
        }
        return fallback;
    }

    private Map<String, Object> contactFromPassenger(PassengerDetailsDto passenger) {
        if (passenger == null) {
            return new HashMap<>();
        }
        Map<String, Object> contact = new HashMap<>();
        contact.put("type", "PASSENGER");
        contact.put("title", passenger.getTitle());
        contact.put("firstName", passenger.getFirstName());
        contact.put("lastName", passenger.getLastName());
        contact.put("fullName", buildFullName(passenger.getFirstName(), passenger.getLastName()));
        contact.put("email", passenger.getEmail());
        contact.put("phoneNumber", passenger.getPhoneNumber());
        contact.put("nationality", passenger.getNationality());
        return sanitizeContact(contact);
    }

    private Map<String, Object> contactFromGuest(GuestDetailsDto guest) {
        if (guest == null) {
            return new HashMap<>();
        }
        Map<String, Object> contact = new HashMap<>();
        contact.put("type", "GUEST");
        contact.put("title", guest.getTitle());
        contact.put("firstName", guest.getFirstName());
        contact.put("lastName", guest.getLastName());
        contact.put("fullName", buildFullName(guest.getFirstName(), guest.getLastName()));
        contact.put("email", guest.getEmail());
        contact.put("phoneNumber", guest.getPhoneNumber());
        contact.put("nationality", guest.getNationality());
        return sanitizeContact(contact);
    }

    private Map<String, Object> sanitizeContact(Map<String, Object> contact) {
        Map<String, Object> cleaned = new HashMap<>();
        contact.forEach((key, value) -> {
            if (value instanceof String str) {
                if (StringUtils.isNotBlank(str)) {
                    cleaned.put(key, str.trim());
                }
            } else if (value != null) {
                cleaned.put(key, value);
            }
        });
        return cleaned;
    }

    private String buildFullName(String firstName, String lastName) {
        if (StringUtils.isBlank(firstName) && StringUtils.isBlank(lastName)) {
            return null;
        }
        if (StringUtils.isBlank(firstName)) {
            return lastName;
        }
        if (StringUtils.isBlank(lastName)) {
            return firstName;
        }
        return firstName.trim() + " " + lastName.trim();
    }

    private Map<String, Object> convertJsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.convertValue(node, MAP_TYPE);
        } catch (IllegalArgumentException ex) {
            log.warn("Failed to convert saga payload to map: {}", node, ex);
            return new HashMap<>();
        }
    }

    private boolean hasFlight(Booking booking) {
        return booking.getBookingType() == BookingType.FLIGHT || booking.getBookingType() == BookingType.COMBO;
    }

    private boolean hasHotel(Booking booking) {
        return booking.getBookingType() == BookingType.HOTEL || booking.getBookingType() == BookingType.COMBO;
    }

    // ==== State helpers ====

    @Transactional
    public void markPaymentInitiated(UUID bookingId) {
        Booking booking = bookingRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        BookingSagaInstance saga = sagaRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalStateException("Saga instance not found for booking " + bookingId));

        SagaState currentState = saga.getCurrentState();
        if (currentState == SagaState.FLIGHT_RESERVED || currentState == SagaState.HOTEL_RESERVED) {
            transitionState(saga, SagaState.PAYMENT_PENDING, "PaymentRequested", null, null);
        }
        else if (currentState != SagaState.PAYMENT_PENDING) {
            log.warn("Ignoring manual payment initiation for booking {} in state {}", bookingId, currentState);
            throw new IllegalStateException("Booking is not ready for payment: state=" + currentState);
        }
    }

    private void transitionState(BookingSagaInstance saga,
            SagaState newState,
            String eventType,
            JsonNode payload,
            String errorMessage) {
        SagaState previous = saga.getCurrentState();
        if (previous == newState) {
            return;
        }
        saga.setCurrentState(newState);
        saga.setLastUpdatedAt(ZonedDateTime.now());
        sagaRepository.save(saga);
        bookingRepository.findByBookingId(saga.getBookingId())
                .ifPresent(b -> updateBookingState(b, newState, null));
        logStateTransition(saga, previous, newState, eventType, payload, errorMessage);
    }

    private void logStateTransition(BookingSagaInstance saga,
            SagaState from,
            SagaState to,
            String eventType,
            JsonNode payload,
            String errorMessage) {
        try {
            SagaStateLog logEntry = new SagaStateLog();
            logEntry.setSagaId(saga.getSagaId());
            logEntry.setBookingId(saga.getBookingId().toString());
            logEntry.setFromState(from);
            logEntry.setToState(to);
            logEntry.setEventType(eventType != null ? eventType : "STATE_TRANSITION");
            if (payload != null) {
                logEntry.setEventPayload(objectMapper.writeValueAsString(payload));
            }
            logEntry.setErrorMessage(errorMessage);
            sagaStateLogRepository.save(logEntry);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize saga event payload for saga {}", saga.getSagaId(), e);
        }
    }

    private void cancelSaga(BookingSagaInstance saga,
            Booking booking,
            BookingStatus finalStatus,
            String eventType,
            JsonNode payload,
            String reason) {
        saga.setIsCompensating(true);
        saga.setCompensationReason(reason);
        transitionState(saga, SagaState.BOOKING_CANCELLED, eventType, payload, reason);
        saga.setCompletedAt(ZonedDateTime.now());
        sagaRepository.save(saga);

        Booking latestBooking = bookingRepository.findByBookingId(booking.getBookingId())
                .orElse(booking);
        latestBooking.setSagaState(SagaState.BOOKING_CANCELLED);
        latestBooking.setStatus(finalStatus);
        latestBooking.setCancellationReason(reason);
        latestBooking.setCompensationReason(reason);
        latestBooking.setCancelledAt(ZonedDateTime.now());
        bookingService.releaseReservationLock(latestBooking);
        bookingRepository.save(latestBooking);
    }

    private String resolveHotelFailureReason(JsonNode payload) {
        final String defaultReason = "Hotel reservation failed";
        if (payload == null || payload.isNull()) {
            return defaultReason;
        }

        String message = null;
        JsonNode errorMessageNode = payload.get("errorMessage");
        if (errorMessageNode != null && !errorMessageNode.isNull()) {
            message = errorMessageNode.asText();
        }

        if (StringUtils.isBlank(message)) {
            JsonNode messageNode = payload.get("message");
            if (messageNode != null && !messageNode.isNull()) {
                message = messageNode.asText();
            }
        }

        if (StringUtils.isBlank(message)) {
            JsonNode detailsNode = payload.get("details");
            if (detailsNode != null && !detailsNode.isNull()) {
                JsonNode innerMessage = detailsNode.get("message");
                if (innerMessage != null && !innerMessage.isNull()) {
                    message = innerMessage.asText();
                }
            }
        }

        if (StringUtils.isBlank(message)) {
            return defaultReason;
        }

        String normalized = message.trim();
        String lower = normalized.toLowerCase();
        if (lower.contains("inventory not available") || lower.contains("not available for reservation")) {
            return "Selected hotel room is no longer available. Please choose another option.";
        }

        return normalized;
    }

    private void completeSaga(BookingSagaInstance saga, Booking booking, JsonNode payload) {
        transitionState(saga, SagaState.BOOKING_COMPLETED, "BookingCompleted", payload, null);
        saga.setCompletedAt(ZonedDateTime.now());
        sagaRepository.save(saga);

        booking.setStatus(BookingStatus.CONFIRMED);
        if (booking.getConfirmationNumber() == null || booking.getConfirmationNumber().isBlank()) {
            booking.setConfirmationNumber(generateConfirmationNumber());
        }
        booking.setSagaState(SagaState.BOOKING_COMPLETED);
        bookingRepository.save(booking);
        Map<String, Object> extras = new HashMap<>();
        extras.put("emailTemplate", "booking-confirmation.ftl");
        publishNotificationEvent(booking, "BookingConfirmed", extras);
        
        // Release reservation lock after saving booking state to ensure consistency
        try {
            bookingService.releaseReservationLock(booking);
        } catch (Exception e) {
            log.error("Failed to release reservation lock for booking {}: {}", booking.getBookingId(), e.getMessage(), e);
            // Don't fail the entire operation if lock release fails - just log the error
        }
    }

    private void updateBookingState(Booking booking, SagaState sagaState, BookingStatus status) {
        booking.setSagaState(sagaState);
        if (status != null) {
            booking.setStatus(status);
        }
        bookingRepository.save(booking);
    }

    private String generateConfirmationNumber() {
        return "CNF-" + System.currentTimeMillis();
    }

    private BigDecimal toBigDecimal(Object amount) {
        if (amount instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (amount instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
