package com.pdh.flight.service;

import com.pdh.flight.dto.request.FlightScheduleCreateDto;
import com.pdh.flight.dto.request.FlightScheduleUpdateDto;
import com.pdh.flight.dto.response.FlightScheduleDto;
import com.pdh.flight.dto.response.FlightDto;
import com.pdh.flight.mapper.BackofficeFlightMapper;
import com.pdh.flight.model.FlightSchedule;
import com.pdh.flight.model.Flight;
import com.pdh.flight.model.Aircraft;
import com.pdh.flight.model.enums.ScheduleStatus;
import com.pdh.flight.repository.FlightScheduleRepository;
import com.pdh.flight.repository.FlightRepository;
import com.pdh.flight.repository.AircraftRepository;
import com.pdh.flight.repository.specification.FlightScheduleSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.util.*;

/**
 * Service for managing flight schedules in backoffice
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BackofficeFlightScheduleService {

    private final FlightScheduleRepository flightScheduleRepository;
    private final FlightRepository flightRepository;
    private final AircraftRepository aircraftRepository;
    private final BackofficeFlightMapper flightMapper;

    /**
     * Get all flight schedules with pagination and filtering using JPA Specification
     * 
     * @param page Page number
     * @param size Page size
     * @param search Search term for flight number and airports (city/country/IATA)
     * @param date Filter by departure date
     * @param departureAirportId Filter by specific departure airport
     * @param arrivalAirportId Filter by specific arrival airport
     * @return Paginated flight schedule results
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAllFlightSchedules(int page, int size, String search, LocalDate date, 
                                                      Integer departureAirportId, Integer arrivalAirportId) {
        log.info("Fetching flight schedules: page={}, size={}, search={}, date={}, departureAirportId={}, arrivalAirportId={}", 
                page, size, search, date, departureAirportId, arrivalAirportId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("departureTime").ascending());
        
        // Build specification using Specification pattern
        Specification<FlightSchedule> spec = Specification.where(FlightScheduleSpecification.isNotDeleted());
        
        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and(FlightScheduleSpecification.searchByTerm(search));
        }
        
        if (date != null) {
            spec = spec.and(FlightScheduleSpecification.hasDepartureDate(date));
        }
        
        if (departureAirportId != null) {
            spec = spec.and(FlightScheduleSpecification.hasDepartureAirportId(departureAirportId));
        }
        
        if (arrivalAirportId != null) {
            spec = spec.and(FlightScheduleSpecification.hasArrivalAirportId(arrivalAirportId));
        }
        
        Page<FlightSchedule> schedulePage = flightScheduleRepository.findAll(spec, pageable);
        
        return buildResponse(schedulePage);
    }

    /**
     * Helper method to build response from page
     */
    private Map<String, Object> buildResponse(Page<FlightSchedule> schedulePage) {
        List<FlightScheduleDto> content = schedulePage.getContent().stream()
                .map(this::toDtoWithDetails)
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", content);
        response.put("page", schedulePage.getNumber());
        response.put("size", schedulePage.getSize());
        response.put("totalElements", schedulePage.getTotalElements());
        response.put("totalPages", schedulePage.getTotalPages());
        response.put("first", schedulePage.isFirst());
        response.put("last", schedulePage.isLast());
        return response;
    }



    /**
     * Get single flight schedule with full details
     */
    @Transactional(readOnly = true)
    public FlightScheduleDto getFlightSchedule(UUID scheduleId) {
        log.info("Fetching flight schedule details: {}", scheduleId);

        FlightSchedule schedule = flightScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("Flight schedule not found with ID: " + scheduleId));

        if (schedule.isDeleted()) {
            throw new EntityNotFoundException("Flight schedule not found with ID: " + scheduleId);
        }

        return toDtoWithDetails(schedule);
    }

    /**
     * Create new flight schedule
     */
    public FlightScheduleDto createFlightSchedule(FlightScheduleCreateDto createDto) {
        log.info("Creating new flight schedule for flight: {}", createDto.getFlightId());

        // Validate that the flight exists
        flightRepository.findById(createDto.getFlightId())
                .orElseThrow(() -> new EntityNotFoundException("Flight not found with ID: " + createDto.getFlightId()));

        // Validate that the aircraft exists
        Aircraft aircraft = aircraftRepository.findById(createDto.getAircraftId())
                .orElseThrow(
                        () -> new EntityNotFoundException("Aircraft not found with ID: " + createDto.getAircraftId()));

        // Validate aircraft availability (no overlapping schedules)
        validateAircraftAvailability(createDto.getAircraftId(), createDto.getDepartureTime(),
                createDto.getArrivalTime(), null);

        FlightSchedule schedule = new FlightSchedule();
        schedule.setFlightId(createDto.getFlightId());
        schedule.setDepartureTime(createDto.getDepartureTime());
        schedule.setArrivalTime(createDto.getArrivalTime());
        schedule.setAircraftType(aircraft.getModel()); // Set from aircraft
        schedule.setAircraftId(createDto.getAircraftId()); // Set aircraft ID
        schedule.setStatus(createDto.getStatus());

        FlightSchedule savedSchedule = flightScheduleRepository.save(schedule);
        log.info("Flight schedule created successfully with ID: {}", savedSchedule.getScheduleId());

        return toDtoWithDetails(savedSchedule);
    }

    /**
     * Update existing flight schedule
     */
    public FlightScheduleDto updateFlightSchedule(UUID scheduleId, FlightScheduleUpdateDto updateDto) {
        log.info("Updating flight schedule: {}", scheduleId);

        FlightSchedule schedule = flightScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("Flight schedule not found with ID: " + scheduleId));

        if (schedule.isDeleted()) {
            throw new EntityNotFoundException("Flight schedule not found with ID: " + scheduleId);
        }

        // Update fields if provided
        if (updateDto.getDepartureTime() != null) {
            schedule.setDepartureTime(updateDto.getDepartureTime());
        }

        if (updateDto.getArrivalTime() != null) {
            schedule.setArrivalTime(updateDto.getArrivalTime());
        }

        if (updateDto.getAircraftId() != null) {
            Aircraft aircraft = aircraftRepository.findById(updateDto.getAircraftId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Aircraft not found with ID: " + updateDto.getAircraftId()));

            // Validate aircraft availability for the new time slot
            ZonedDateTime departureTime = updateDto.getDepartureTime() != null ? updateDto.getDepartureTime()
                    : schedule.getDepartureTime();
            ZonedDateTime arrivalTime = updateDto.getArrivalTime() != null ? updateDto.getArrivalTime()
                    : schedule.getArrivalTime();

            validateAircraftAvailability(updateDto.getAircraftId(), departureTime, arrivalTime, scheduleId);
            schedule.setAircraftType(aircraft.getModel());
            schedule.setAircraftId(updateDto.getAircraftId());
        }

        if (updateDto.getStatus() != null) {
            schedule.setStatus(updateDto.getStatus());
        }

        FlightSchedule updatedSchedule = flightScheduleRepository.save(schedule);
        log.info("Flight schedule updated successfully: {}", scheduleId);

        return toDtoWithDetails(updatedSchedule);
    }

    /**
     * Delete flight schedule (soft delete)
     */
    public void deleteFlightSchedule(UUID scheduleId) {
        log.info("Deleting flight schedule: {}", scheduleId);

        FlightSchedule schedule = flightScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("Flight schedule not found with ID: " + scheduleId));

        if (schedule.isDeleted()) {
            throw new EntityNotFoundException("Flight schedule not found with ID: " + scheduleId);
        }

        // Check if schedule can be deleted (e.g., not if it has bookings)
        if ("ACTIVE".equals(schedule.getStatus()) || "COMPLETED".equals(schedule.getStatus())) {
            throw new IllegalStateException("Cannot delete active or completed flight schedule");
        }

        schedule.setDeleted(true);
        schedule.setDeletedAt(ZonedDateTime.now());

        flightScheduleRepository.save(schedule);
        log.info("Flight schedule deleted successfully: {}", scheduleId);
    }

    /**
     * Get flight schedule statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFlightScheduleStatistics() {
        log.info("Fetching flight schedule statistics");

        long totalSchedules = flightScheduleRepository.countByIsDeletedFalse();
        long scheduledCount = flightScheduleRepository.countByStatusAndIsDeletedFalse(ScheduleStatus.SCHEDULED);
        long activeCount = flightScheduleRepository.countByStatusAndIsDeletedFalse(ScheduleStatus.ACTIVE);
        long delayedCount = flightScheduleRepository.countByStatusAndIsDeletedFalse(ScheduleStatus.DELAYED);
        long cancelledCount = flightScheduleRepository.countByStatusAndIsDeletedFalse(ScheduleStatus.CANCELLED);
        long completedCount = flightScheduleRepository.countByStatusAndIsDeletedFalse(ScheduleStatus.COMPLETED);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSchedules", totalSchedules);
        stats.put("scheduledCount", scheduledCount);
        stats.put("activeCount", activeCount);
        stats.put("delayedCount", delayedCount);
        stats.put("cancelledCount", cancelledCount);
        stats.put("completedCount", completedCount);

        return stats;
    }

    /**
     * Validate aircraft availability for the given time slot
     */
    private void validateAircraftAvailability(Long aircraftId, ZonedDateTime departureTime, ZonedDateTime arrivalTime,
            UUID excludeScheduleId) {
        // Check for overlapping schedules for the same aircraft
        List<FlightSchedule> overlapping = flightScheduleRepository.findOverlappingSchedules(
                aircraftId, departureTime, arrivalTime, excludeScheduleId);

        if (!overlapping.isEmpty()) {
            throw new IllegalArgumentException("Aircraft is not available during the requested time slot. " +
                    "Conflicting schedule ID: " + overlapping.get(0).getScheduleId());
        }
    }

    /**
     * Convert FlightSchedule entity to DTO with complete details
     */
    private FlightScheduleDto toDtoWithDetails(FlightSchedule schedule) {
        FlightScheduleDto.FlightScheduleDtoBuilder builder = FlightScheduleDto.builder()
                .scheduleId(schedule.getScheduleId())
                .flightId(schedule.getFlightId())
                .departureTime(schedule.getDepartureTime())
                .arrivalTime(schedule.getArrivalTime())
                .aircraftType(schedule.getAircraftType())
                .aircraftId(schedule.getAircraftId())
                .status(schedule.getStatus())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .createdBy(schedule.getCreatedBy())
                .updatedBy(schedule.getUpdatedBy());

        // Calculate duration
        if (schedule.getDepartureTime() != null && schedule.getArrivalTime() != null) {
            Duration duration = Duration.between(schedule.getDepartureTime(), schedule.getArrivalTime());
            builder.durationMinutes(duration.toMinutes());
        }

        // Load related entities if needed (use lightweight DTO to prevent circular
        // references)
        if (schedule.getFlight() != null) {
            FlightDto flightDto = flightMapper.toLightweightDto(schedule.getFlight());
            builder.flight(flightDto);
        }

        return builder.build();
    }
}
