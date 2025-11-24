package com.pdh.flight.controller;

import com.pdh.common.dto.ApiResponse;
import com.pdh.flight.dto.request.FlightScheduleCreateDto;
import com.pdh.flight.dto.request.FlightScheduleUpdateDto;
import com.pdh.flight.dto.response.FlightScheduleDto;
import com.pdh.flight.service.BackofficeFlightScheduleService;
import com.pdh.flight.service.FlightScheduleService;
import com.pdh.flight.service.FlightDataGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Controller for managing flight schedules in backoffice
 */
@RestController
@RequestMapping("/backoffice/schedules")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Backoffice Flight Schedules", description = "Flight schedule management for backoffice")
public class BackofficeFlightScheduleController {

    private final BackofficeFlightScheduleService backofficeFlightScheduleService;
    private final FlightScheduleService flightScheduleService;
    private final FlightDataGeneratorService flightDataGeneratorService;

    /**
     * Get all flight schedules with pagination and filtering
     */
    @Operation(summary = "Get all flight schedules", description = "Retrieve all flight schedules with pagination and flexible search")
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllFlightSchedules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer departureAirportId,
            @RequestParam(required = false) Integer arrivalAirportId) {

        log.info("Fetching flight schedules: page={}, size={}, search={}, date={}, departureAirportId={}, arrivalAirportId={}", 
                page, size, search, date, departureAirportId, arrivalAirportId);

        try {
            Map<String, Object> response = backofficeFlightScheduleService.getAllFlightSchedules(
                    page, size, search, date, departureAirportId, arrivalAirportId);
            log.info("Found {} flight schedules", ((List<?>) response.getOrDefault("content", List.of())).size());
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Error fetching flight schedules", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch flight schedules", e.getMessage()));
        }
    }

    /**
     * Get flight schedule by ID
     */
    @Operation(summary = "Get flight schedule by ID", description = "Retrieve a specific flight schedule by its ID")
    @GetMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<FlightScheduleDto>> getFlightSchedule(
            @Parameter(description = "Schedule ID", required = true) @PathVariable UUID scheduleId) {

        log.info("Fetching flight schedule details: ID={}", scheduleId);

        try {
            FlightScheduleDto response = backofficeFlightScheduleService.getFlightSchedule(scheduleId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (jakarta.persistence.EntityNotFoundException e) {
            log.error("Flight schedule not found: ID={}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Flight schedule not found", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching flight schedule details: ID={}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch flight schedule details", e.getMessage()));
        }
    }

    /**
     * Create a new flight schedule
     */
    @Operation(summary = "Create flight schedule", description = "Create a new flight schedule")
    @PostMapping
    public ResponseEntity<ApiResponse<FlightScheduleDto>> createFlightSchedule(
            @Valid @RequestBody FlightScheduleCreateDto scheduleCreateDto) {

        log.info("Creating new flight schedule for flight: {}", scheduleCreateDto.getFlightId());

        try {
            FlightScheduleDto response = backofficeFlightScheduleService.createFlightSchedule(scheduleCreateDto);
            log.info("Flight schedule created successfully: {}", response.getScheduleId());
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("Invalid flight schedule data", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid flight schedule data", e.getMessage()));
        } catch (jakarta.persistence.EntityNotFoundException e) {
            log.error("Referenced entity not found", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Referenced entity not found", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating flight schedule", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create flight schedule", e.getMessage()));
        }
    }

    /**
     * Update an existing flight schedule
     */
    @Operation(summary = "Update flight schedule", description = "Update an existing flight schedule")
    @PutMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<FlightScheduleDto>> updateFlightSchedule(
            @Parameter(description = "Schedule ID", required = true) @PathVariable UUID scheduleId,
            @Valid @RequestBody FlightScheduleUpdateDto scheduleUpdateDto) {

        log.info("Updating flight schedule: ID={}", scheduleId);

        try {
            FlightScheduleDto response = backofficeFlightScheduleService.updateFlightSchedule(scheduleId,
                    scheduleUpdateDto);
            log.info("Flight schedule updated successfully: ID={}", scheduleId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (jakarta.persistence.EntityNotFoundException e) {
            log.error("Flight schedule not found for update: ID={}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Flight schedule not found", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid flight schedule data for update", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid flight schedule data", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating flight schedule: ID={}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update flight schedule", e.getMessage()));
        }
    }

    /**
     * Delete a flight schedule
     */
    @Operation(summary = "Delete flight schedule", description = "Delete a flight schedule (soft delete)")
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteFlightSchedule(
            @Parameter(description = "Schedule ID", required = true) @PathVariable UUID scheduleId) {

        log.info("Deleting flight schedule: ID={}", scheduleId);

        try {
            backofficeFlightScheduleService.deleteFlightSchedule(scheduleId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Flight schedule deleted successfully");
            log.info("Flight schedule deleted successfully: ID={}", scheduleId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (jakarta.persistence.EntityNotFoundException e) {
            log.error("Flight schedule not found for deletion: ID={}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Flight schedule not found", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("Cannot delete flight schedule: ID={}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Cannot delete flight schedule", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting flight schedule: ID={}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete flight schedule", e.getMessage()));
        }
    }

    /**
     * Get flight schedule statistics
     */
    @Operation(summary = "Get flight schedule statistics", description = "Get statistics about flight schedules")
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFlightScheduleStatistics() {
        log.info("Fetching flight schedule statistics");

        try {
            Map<String, Object> response = backofficeFlightScheduleService.getFlightScheduleStatistics();
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Error fetching flight schedule statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch flight schedule statistics", e.getMessage()));
        }
    }

    // Legacy endpoints for backward compatibility (nested under flights)

    /**
     * Get all schedules for a specific flight (legacy endpoint)
     */
    @Operation(summary = "Get schedules for flight", description = "Get all schedules for a specific flight")
    @GetMapping("/flight/{flightId}")
    public ResponseEntity<ApiResponse<List<FlightScheduleDto>>> getSchedulesForFlight(
            @Parameter(description = "Flight ID", required = true) @PathVariable Long flightId) {

        log.info("Fetching schedules for flight ID: {}", flightId);

        try {
            List<FlightScheduleDto> schedules = flightScheduleService.getSchedulesByFlightId(flightId);
            return ResponseEntity.ok(ApiResponse.success(schedules));
        } catch (Exception e) {
            log.error("Error fetching schedules for flight: {}", flightId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch schedules for flight", e.getMessage()));
        }
    }

    // Flight Data Generation Endpoints

    /**
     * Generate flight schedules and fares for a specific date
     */
    @Operation(summary = "Generate daily flight data", description = "Generate flight schedules and fares for a specific date")
    @PostMapping("/generate-daily")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateDailyFlightData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        log.info("Generating daily flight data for date: {}", targetDate);

        try {
            Integer schedulesCreated = flightDataGeneratorService.generateDailyFlightData(targetDate);

            Map<String, Object> response = new HashMap<>();
            response.put("target_date",
                    targetDate != null ? targetDate.toString() : LocalDate.now().plusDays(1).toString());
            response.put("schedules_created", schedulesCreated);
            response.put("message", "Flight data generated successfully");

            log.info("Generated {} schedules for date: {}", schedulesCreated, targetDate);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Error generating daily flight data for date: {}", targetDate, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to generate flight data", e.getMessage()));
        }
    }

    /**
     * Generate flight data for a range of dates
     */
    @Operation(summary = "Generate flight data range", description = "Generate flight data for a range of dates")
    @PostMapping("/generate-range")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateFlightDataRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Generating flight data range from {} to {}", startDate, endDate);

        try {
            Map<String, Object> result = flightDataGeneratorService.generateFlightDataRange(startDate, endDate);

            Map<String, Object> response = new HashMap<>();
            response.put("start_date", startDate.toString());
            response.put("end_date", endDate.toString());
            response.put("data", result);
            response.put("message", "Flight data range generated successfully");

            log.info("Generated flight data for date range: {} to {}", startDate, endDate);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Error generating flight data range from {} to {}", startDate, endDate, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to generate flight data range", e.getMessage()));
        }
    }

    /**
     * Generate data for the next N days
     */
    @Operation(summary = "Generate data for next days", description = "Generate flight data for the next N days")
    @PostMapping("/generate-next-days")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateDataForNextDays(
            @RequestParam(defaultValue = "7") Integer numberOfDays) {

        log.info("Generating flight data for next {} days", numberOfDays);

        try {
            Map<String, Object> result = flightDataGeneratorService.generateDataForNextDays(numberOfDays);

            Map<String, Object> response = new HashMap<>();
            response.put("number_of_days", numberOfDays);
            response.put("data", result);
            response.put("message", "Flight data generated for next " + numberOfDays + " days");

            log.info("Generated flight data for next {} days", numberOfDays);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Error generating flight data for next {} days", numberOfDays, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to generate flight data", e.getMessage()));
        }
    }

    /**
     * Clean up old flight data
     */
    @Operation(summary = "Clean up old flight data", description = "Clean up old flight data")
    @DeleteMapping("/cleanup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cleanupOldFlightData(
            @RequestParam(defaultValue = "30") Integer daysToKeep) {

        log.info("Cleaning up old flight data, keeping {} days", daysToKeep);

        try {
            Integer deletedSchedules = flightDataGeneratorService.cleanupOldFlightData(daysToKeep);

            Map<String, Object> response = new HashMap<>();
            response.put("days_kept", daysToKeep);
            response.put("deleted_schedules", deletedSchedules);
            response.put("message", "Old flight data cleaned up successfully");

            log.info("Cleaned up {} old flight schedules", deletedSchedules);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Error cleaning up old flight data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to cleanup flight data", e.getMessage()));
        }
    }
}
