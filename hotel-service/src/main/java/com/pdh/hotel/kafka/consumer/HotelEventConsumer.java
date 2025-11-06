package com.pdh.hotel.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdh.common.saga.SagaCommand;
import com.pdh.hotel.dto.HotelBookingDetailsDto;
import com.pdh.hotel.service.HotelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Handles hotel saga commands emitted by booking-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HotelEventConsumer {

    private final HotelService hotelService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "booking-saga-commands",
        groupId = "hotel-service-saga-group",
        containerFactory = "sagaCommandListenerContainerFactory"
    )
    public void handleSagaCommand(@Payload String message, Acknowledgment acknowledgment) {
        try {
            SagaCommand command = objectMapper.readValue(message, SagaCommand.class);
            if (command == null || command.getAction() == null) {
                acknowledgment.acknowledge();
                return;
            }

            switch (command.getAction()) {
                case "RESERVE_HOTEL" -> reserveHotel(command);
                case "CANCEL_HOTEL_RESERVATION" -> cancelHotel(command);
                default -> {
                    acknowledgment.acknowledge();
                    return;
                }
            }

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing hotel saga command: {}", message, ex);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    private void reserveHotel(SagaCommand command) {
        UUID bookingId = command.getBookingId();
        String sagaId = command.getSagaId();
        HotelBookingDetailsDto details = convertHotelDetails(command);

        if (details != null) {
            hotelService.reserveHotel(bookingId, sagaId, details);
        } 
    }

    private void cancelHotel(SagaCommand command) {
        UUID bookingId = command.getBookingId();
        HotelBookingDetailsDto details = convertHotelDetails(command);
        if (details != null) {
            hotelService.cancelHotelReservation(bookingId, command.getSagaId(), details);
        } else {
            hotelService.cancelHotelReservation(bookingId);
        }
    }

    private HotelBookingDetailsDto convertHotelDetails(SagaCommand command) {
        if (command == null || command.getHotelDetails() == null) {
            return null;
        }
        return objectMapper.convertValue(command.getHotelDetails(), HotelBookingDetailsDto.class);
    }
}
