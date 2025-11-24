package com.pdh.flight.service;

import com.pdh.flight.client.MediaServiceClient;
import com.pdh.flight.dto.request.AirlineRequestDto;
import com.pdh.flight.dto.response.AirlineDto;
import com.pdh.flight.mapper.AirlineMapper;
import com.pdh.flight.model.Airline;
import com.pdh.flight.repository.AirlineRepository;
import com.pdh.flight.repository.FlightRepository;
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
 * Service for managing airlines in backoffice
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackofficeAirlineService {

    private final AirlineRepository airlineRepository;
    private final FlightRepository flightRepository;
    private final AirlineMapper airlineMapper;

    /**
     * Get all airlines with pagination and filtering for backoffice
     */
    public Map<String, Object> getAllAirlines(int page, int size, String search, String country) {
        log.info("Fetching airlines for backoffice with search: {}, country: {}, page: {}, size: {}",
                search, country, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Airline> airlinePage = airlineRepository.findAll(pageable);

        List<AirlineDto> airlineDtos = airlinePage.getContent().stream()
                .map(airlineMapper::toDto)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", airlineDtos);
        response.put("totalElements", airlinePage.getTotalElements());
        response.put("totalPages", airlinePage.getTotalPages());
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("hasNext", airlinePage.hasNext());
        response.put("hasPrevious", airlinePage.hasPrevious());

        return response;
    }

    /**
     * Get airline by ID for backoffice
     */
    public AirlineDto getAirline(Long id) {
        log.info("Fetching airline details for backoffice: ID={}", id);

        Airline airline = airlineRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Airline not found with ID: " + id));

        return airlineMapper.toDto(airline);
    }

    /**
     * Create a new airline
     */
    @Transactional
    public AirlineDto createAirline(AirlineRequestDto airlineRequestDto) {
        log.info("Creating new airline: {}", airlineRequestDto.getName());

        Airline airline = airlineMapper.toEntity(airlineRequestDto);
        airline = airlineRepository.save(airline);

        // Set featured media URL if provided
        if (airlineRequestDto.getFeaturedMediaUrl() != null && !airlineRequestDto.getFeaturedMediaUrl().isEmpty()) {
            airline.setFeaturedMediaUrl(airlineRequestDto.getFeaturedMediaUrl());
            airline = airlineRepository.save(airline); // Save the updated airline with featured media URL
            log.info("Set featured media URL for airline ID: {}", airline.getAirlineId());
        }

        log.info("Airline created with ID: {}", airline.getAirlineId());
        return airlineMapper.toDto(airline);
    }

    /**
     * Update an existing airline
     */
    @Transactional
    public AirlineDto updateAirline(Long id, AirlineRequestDto airlineRequestDto) {
        log.info("Updating airline: ID={}", id);

        Airline existingAirline = airlineRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Airline not found with ID: " + id));

        // Update fields directly
        existingAirline.setName(airlineRequestDto.getName());
        existingAirline.setIataCode(airlineRequestDto.getCode());

        Airline updatedAirline = airlineRepository.save(existingAirline);

        // Update featured media URL if provided
        if (airlineRequestDto.getFeaturedMediaUrl() != null) {
            updatedAirline.setFeaturedMediaUrl(airlineRequestDto.getFeaturedMediaUrl());
            updatedAirline = airlineRepository.save(updatedAirline); // Save the updated airline with featured media URL
            log.info("Updated featured media URL for airline ID: {}", updatedAirline.getAirlineId());
        }

        log.info("Airline updated with ID: {}", id);
        return airlineMapper.toDto(updatedAirline);
    }

    /**
     * Delete an airline (soft delete)
     */
    @Transactional
    public void deleteAirline(Long id) {
        log.info("Deleting airline: ID={}", id);

        Airline airline = airlineRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Airline not found with ID: " + id));

        airline.setIsActive(false);
        airlineRepository.save(airline);

        log.info("Airline deleted with ID: {}", id);
    }

    /**
     * Search airlines for storefront autocomplete
     */
    @Transactional(readOnly = true)
    public List<AirlineDto> searchAirlinesForStorefront(String query) {
        log.info("Searching airlines for storefront: query={}", query);

        if (!StringUtils.hasText(query) || query.trim().length() < 2) {
            return new ArrayList<>();
        }

        List<Airline> airlines = airlineRepository.findAll();

        return airlines.stream()
                .filter(airline -> airline.getName().toLowerCase().contains(query.toLowerCase()) ||
                        airline.getIataCode().toLowerCase().contains(query.toLowerCase()))
                .limit(10)
                .map(airlineMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get popular airlines for storefront
     */
    @Transactional(readOnly = true)
    public List<AirlineDto> getPopularAirlines() {
        log.info("Fetching popular airlines for storefront");

        // For now, return all active airlines
        // In production, this would be based on booking statistics
        List<Airline> airlines = airlineRepository.findAll();

        // Limit to top 10 popular airlines
        return airlineMapper.toDtoList(
                airlines.stream()
                        .filter(Airline::getIsActive)
                        .limit(10)
                        .collect(Collectors.toList()));
    }

    /**
     * Search airlines for autocomplete functionality
     */
    public List<Map<String, Object>> searchAirlines(String query) {
        log.info("Searching airlines for autocomplete: query={}", query);

        if (!StringUtils.hasText(query) || query.trim().length() < 2) {
            return new ArrayList<>();
        }

        List<Airline> airlines = airlineRepository.findAll();

        return airlines.stream()
                .filter(airline -> airline.getName().toLowerCase().contains(query.toLowerCase()) ||
                        airline.getIataCode().toLowerCase().contains(query.toLowerCase()))
                .limit(10)
                .map(airline -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", airline.getAirlineId());
                    result.put("name", airline.getName());
                    result.put("code", airline.getIataCode());
                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get airline statistics
     */
    public Map<String, Object> getAirlineStatistics() {
        log.info("Fetching airline statistics for backoffice");

        long totalAirlines = airlineRepository.count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAirlines", totalAirlines);
        stats.put("airlinesWithFlights", 0L); // Simplified
        stats.put("countriesCount", 0L); // Simplified

        return stats;
    }
}