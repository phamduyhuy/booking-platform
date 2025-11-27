package com.pdh.notification.kafka.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdh.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.Acknowledgment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.springframework.kafka.support.KafkaHeaders.RECEIVED_TOPIC;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "PaymentFailed",
            "BookingPaymentFailed",
            "BookingConfirmed",
            "BookingRefunded");

    @KafkaListener(topics = { "booking.Booking.events",
            "booking.Payment.events" }, groupId = "notification-saga-outbox-listener", containerFactory = "notificationEventListenerContainerFactory")
    public void consumeBookingSagaEvents(
            @Payload JsonNode message,
            @Header(value = "eventType", required = false) String eventTypeHeader,
            @Header(RECEIVED_TOPIC) String topic) {
        try {
            if (message == null || message.isNull()) {
                log.debug("Received null or empty message from topic {}", topic);
                return;
            }

            log.debug("Received notification message from topic {}: {}", topic, message.toString());
            JsonNode root = normalize(message);
            JsonNode payload = root != null ? root.path("payload") : null;
            if (payload == null || payload.isMissingNode() || payload.isNull()) {
                payload = root;
            } else if (payload.isTextual()) {
                payload = normalize(payload);
            }

            String eventType = resolveEventType(eventTypeHeader, root, payload);
            if (StringUtils.isBlank(eventType)) {
                log.debug("Skipping notification message without eventType from topic {}", topic);
                return;
            }

            if (!SUPPORTED_EVENT_TYPES.contains(eventType)) {
                log.debug("Skipping unsupported notification event {} from topic {}", eventType, topic);
                return;
            }

            Map<String, Object> payloadMap = convertPayload(payload);
            if (payloadMap == null || payloadMap.isEmpty()) {
                log.debug("Skipping notification for event {} due to empty payload", eventType);
                return;
            }

            log.info("Dispatching notification event {} from topic {}", eventType, topic);
            notificationService.handleBookingEvent(eventType, payloadMap);

        } catch (Exception e) {
            log.error("Error processing notification event from topic {}: {}", topic, e.getMessage(), e);

        }
    }

    private JsonNode normalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isTextual()) {
                String text = node.asText();
                if (StringUtils.isNotBlank(text)) {
                    return objectMapper.readTree(text);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to normalize JSON node: {}", node, ex);
        }
        return node;
    }

    private String resolveEventType(String header, JsonNode root, JsonNode payload) {
        if (StringUtils.isNotBlank(header)) {
            return header;
        }
        String eventType = extractText(root, "eventType", "event_type", "type");
        if (StringUtils.isBlank(eventType)) {
            eventType = extractText(payload, "eventType", "event_type", "type");
        }
        if (StringUtils.isBlank(eventType)) {
            JsonNode probe = payload != null && !payload.isNull() ? payload : root;
            if (probe != null && probe.has("flightData")) {
                return "FlightReserved";
            }
        }
        return eventType;
    }

    private String extractText(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            if (field != null) {
                JsonNode value = node.get(field);
                if (value != null && value.isTextual()) {
                    String trimmed = value.asText();
                    if (StringUtils.isNotBlank(trimmed)) {
                        return trimmed;
                    }
                }
            }
        }
        return null;
    }

    private Map<String, Object> convertPayload(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> result = objectMapper.convertValue(payload, MAP_TYPE);
            if (result == null) {
                return new HashMap<>();
            }
            return flattenPayload(result);
        } catch (IllegalArgumentException ex) {
            log.warn("Failed to convert payload for notification: {}", payload, ex);
            return new HashMap<>();
        }
    }

    private Map<String, Object> flattenPayload(Map<String, Object> source) {
        if (source == null) {
            return new HashMap<>();
        }

        Map<String, Object> result = new HashMap<>();
        source.forEach((key, value) -> {
            if ("payload".equalsIgnoreCase(key) || ("data".equalsIgnoreCase(key) && result.isEmpty())) {
                Map<String, Object> nested = convertToMap(value);
                if (nested != null && !nested.isEmpty()) {
                    result.putAll(flattenPayload(nested));
                }
            } else {
                result.put(key, normalizeValue(value));
            }
        });
        return result;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return flattenPayload(convertToMap(map));
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>(collection.size());
            for (Object element : collection) {
                normalized.add(normalizeValue(element));
            }
            return normalized;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> normalized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                normalized.add(normalizeValue(java.lang.reflect.Array.get(value, i)));
            }
            return normalized;
        }
        if (value instanceof String str && looksLikeJson(str)) {
            Map<String, Object> parsed = parseJsonString(str);
            if (parsed != null) {
                return flattenPayload(parsed);
            }
        }
        return value;
    }

    private Map<String, Object> convertToMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        if (value instanceof String str) {
            return parseJsonString(str);
        }
        try {
            return objectMapper.convertValue(value, MAP_TYPE);
        } catch (IllegalArgumentException ex) {
            log.debug("Unable to convert value to map: {}", value);
            return null;
        }
    }

    private Map<String, Object> parseJsonString(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            log.debug("String value is not JSON: {}", json);
            return null;
        }
    }

    private boolean looksLikeJson(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}
