package com.pdh.flight.service;

import com.pdh.flight.client.MediaServiceClient;
import com.pdh.flight.dto.request.AircraftRequestDto;
import com.pdh.flight.dto.response.AircraftDto;
import com.pdh.flight.mapper.AircraftMapper;
import com.pdh.flight.model.Aircraft;
import com.pdh.flight.repository.AircraftRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing aircraft in backoffice
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackofficeAircraftService {

    private final AircraftRepository aircraftRepository;
    private final AircraftMapper aircraftMapper;

    /**
     * Get all aircraft with pagination and filtering for backoffice
     */
    public Map<String, Object> getAllAircraft(int page, int size, String search) {
        log.info("Fetching aircraft for backoffice with search: {}, page: {}, size: {}",
                search, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Aircraft> aircraftPage;

        if (StringUtils.hasText(search)) {
            aircraftPage = aircraftRepository.findActiveByModelContainingIgnoreCase(search, pageable);
        } else {
            aircraftPage = aircraftRepository.findAllActive(pageable);
        }

        List<AircraftDto> aircraftDtos = aircraftMapper.toDtoListWithMedia(aircraftPage.getContent());

        Map<String, Object> response = new HashMap<>();
        response.put("content", aircraftDtos);
        response.put("totalElements", aircraftPage.getTotalElements());
        response.put("totalPages", aircraftPage.getTotalPages());
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("hasNext", aircraftPage.hasNext());
        response.put("hasPrevious", aircraftPage.hasPrevious());

        return response;
    }

    /**
     * Get aircraft by ID for backoffice
     */
    public AircraftDto getAircraft(Long id) {
        log.info("Fetching aircraft details for backoffice: ID={}", id);

        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aircraft not found with ID: " + id));

        return aircraftMapper.toDtoWithMedia(aircraft);
    }

    /**
     * Create a new aircraft
     */
    @Transactional
    public AircraftDto createAircraft(AircraftRequestDto aircraftRequestDto) {
        log.info("Creating new aircraft: {}", aircraftRequestDto.getModel());

        // Validate registration number uniqueness
        if (StringUtils.hasText(aircraftRequestDto.getRegistrationNumber()) &&
                aircraftRepository.existsByRegistrationNumberIgnoreCase(aircraftRequestDto.getRegistrationNumber())) {
            throw new IllegalArgumentException(
                    "Registration number already exists: " + aircraftRequestDto.getRegistrationNumber());
        }

        Aircraft aircraft = aircraftMapper.toEntity(aircraftRequestDto);
        aircraft = aircraftRepository.save(aircraft);

        // Set featured media URL if provided
        if (aircraftRequestDto.getFeaturedMediaUrl() != null && !aircraftRequestDto.getFeaturedMediaUrl().isEmpty()) {
            aircraft.setFeaturedMediaUrl(aircraftRequestDto.getFeaturedMediaUrl());
            aircraft = aircraftRepository.save(aircraft); // Save the updated aircraft with featured media URL
            log.info("Set featured media URL for aircraft ID: {}", aircraft.getAircraftId());
        }

        log.info("Aircraft created with ID: {}", aircraft.getAircraftId());
        return aircraftMapper.toDtoWithMedia(aircraft);
    }

    /**
     * Update an existing aircraft
     */
    @Transactional
    public AircraftDto updateAircraft(Long id, AircraftRequestDto aircraftRequestDto) {
        log.info("Updating aircraft: ID={}", id);

        Aircraft existingAircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aircraft not found with ID: " + id));

        // Check if registration number is unique (exclude current aircraft)
        if (StringUtils.hasText(aircraftRequestDto.getRegistrationNumber())) {
            String upperRegNumber = aircraftRequestDto.getRegistrationNumber().toUpperCase();
            if (!upperRegNumber.equals(existingAircraft.getRegistrationNumber()) &&
                    aircraftRepository.existsByRegistrationNumberIgnoreCase(upperRegNumber)) {
                throw new IllegalArgumentException("Registration number already exists: " + upperRegNumber);
            }
        }

        aircraftMapper.updateEntityFromRequest(existingAircraft, aircraftRequestDto);
        Aircraft updatedAircraft = aircraftRepository.save(existingAircraft);

        // Update featured media URL if provided
        if (aircraftRequestDto.getFeaturedMediaUrl() != null) {
            updatedAircraft.setFeaturedMediaUrl(aircraftRequestDto.getFeaturedMediaUrl());
            updatedAircraft = aircraftRepository.save(updatedAircraft); // Save the updated aircraft with featured media
                                                                        // URL
            log.info("Updated featured media URL for aircraft ID: {}", updatedAircraft.getAircraftId());
        }

        log.info("Aircraft updated with ID: {}", id);
        return aircraftMapper.toDtoWithMedia(updatedAircraft);
    }

    /**
     * Delete an aircraft (soft delete)
     */
    @Transactional
    public void deleteAircraft(Long id) {
        log.info("Deleting aircraft: ID={}", id);

        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aircraft not found with ID: " + id));

        aircraft.setIsActive(false);
        aircraftRepository.save(aircraft);

        log.info("Aircraft deleted with ID: {}", id);
    }

    /**
     * Search aircraft for autocomplete functionality
     */
    public List<Map<String, Object>> searchAircraft(String query) {
        log.info("Searching aircraft for autocomplete: query={}", query);

        if (!StringUtils.hasText(query) || query.trim().length() < 2) {
            return new ArrayList<>();
        }

        List<Aircraft> aircrafts = aircraftRepository.findActiveByModelContainingIgnoreCase(query);

        return aircrafts.stream()
                .limit(10)
                .map(aircraft -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", aircraft.getAircraftId());
                    result.put("model", aircraft.getModel());
                    result.put("manufacturer", aircraft.getManufacturer());
                    result.put("registrationNumber", aircraft.getRegistrationNumber());
                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get aircraft statistics
     */
    public Map<String, Object> getAircraftStatistics() {
        log.info("Fetching aircraft statistics for backoffice");

        long totalAircraft = aircraftRepository.count();
        long activeAircraft = aircraftRepository.findAllActive().size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAircraft", totalAircraft);
        stats.put("activeAircraft", activeAircraft);

        return stats;
    }
}