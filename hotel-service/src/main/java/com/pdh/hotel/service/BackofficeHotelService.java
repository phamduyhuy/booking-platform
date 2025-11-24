package com.pdh.hotel.service;

import com.pdh.common.dto.response.MediaResponse;
import com.pdh.hotel.dto.request.HotelRequestDto;
import com.pdh.hotel.model.Amenity;
import com.pdh.hotel.model.Hotel;
import com.pdh.hotel.model.HotelAmenity;
import com.pdh.hotel.model.RoomAvailability;
import com.pdh.hotel.model.RoomType;
import com.pdh.hotel.repository.AmenityRepository;
import com.pdh.hotel.repository.HotelAmenityRepository;
import com.pdh.hotel.repository.HotelRepository;
import com.pdh.hotel.repository.RoomAvailabilityRepository;
import com.pdh.hotel.repository.RoomTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BackofficeHotelService {

    private final HotelRepository hotelRepository;
    private final AmenityRepository amenityRepository;
    private final HotelAmenityRepository hotelAmenityRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailabilityRepository roomAvailabilityRepository;
    private final ImageService imageService;

    @Transactional(readOnly = true)
    public Map<String, Object> getAllHotels(int page, int size, String search, String city, String status) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("name"));
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Hotel> hotelPage;
        if (search != null && !search.isEmpty()) {
            if (city != null && !city.isEmpty()) {
                hotelPage = hotelRepository.findHotelsByDestinationAndRating(search, BigDecimal.ZERO, BigDecimal.TEN,
                        pageable);
            } else {
                hotelPage = hotelRepository.findHotelsByDestination(search, pageable);
            }
        } else if (city != null && !city.isEmpty()) {
            hotelPage = hotelRepository.findHotelsByCity(city, pageable);
        } else {
            hotelPage = hotelRepository.findAllWithDetails(pageable);
        }

        List<Map<String, Object>> hotels = hotelPage.getContent().stream()
                .map(this::convertHotelToResponse)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", hotels);
        response.put("totalElements", hotelPage.getTotalElements());
        response.put("totalPages", hotelPage.getTotalPages());
        response.put("size", hotelPage.getSize());
        response.put("number", hotelPage.getNumber());
        response.put("first", hotelPage.isFirst());
        response.put("last", hotelPage.isLast());
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getHotel(Long id) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Hotel not found with ID: " + id));

        return convertHotelToResponse(hotel);
    }

    public Map<String, Object> createHotel(HotelRequestDto hotelRequestDto) {
        Hotel hotel = new Hotel();
        hotel.setName(hotelRequestDto.getName());
        hotel.setAddress(hotelRequestDto.getAddress());
        hotel.setCity(hotelRequestDto.getCity());
        hotel.setCountry(hotelRequestDto.getCountry());
        hotel.setDescription(hotelRequestDto.getDescription());
        hotel.setStarRating(hotelRequestDto.getStarRating());
        hotel.setLatitude(hotelRequestDto.getLatitude());
        hotel.setLongitude(hotelRequestDto.getLongitude());
        hotel.setIsActive(true);

        Hotel savedHotel = hotelRepository.save(hotel);

        if (hotelRequestDto.getMedia() != null && !hotelRequestDto.getMedia().isEmpty()) {
            try {
                imageService.updateHotelImagesWithMediaResponse(savedHotel.getHotelId(), hotelRequestDto.getMedia());
                log.info("Associated {} media items with hotel ID: {}", hotelRequestDto.getMedia().size(),
                        savedHotel.getHotelId());
            } catch (Exception e) {
                log.error("Error associating media with hotel {}: {}", savedHotel.getHotelId(), e.getMessage());
            }
        }

        Map<String, Object> response = convertHotelToResponse(savedHotel);
        response.put("message", "Hotel created successfully");
        return response;
    }

    public Map<String, Object> updateHotel(Long id, HotelRequestDto hotelRequestDto) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Hotel not found with ID: " + id));

        hotel.setName(hotelRequestDto.getName());
        hotel.setAddress(hotelRequestDto.getAddress());
        hotel.setCity(hotelRequestDto.getCity());
        hotel.setCountry(hotelRequestDto.getCountry());
        hotel.setDescription(hotelRequestDto.getDescription());
        hotel.setStarRating(hotelRequestDto.getStarRating());
        hotel.setLatitude(hotelRequestDto.getLatitude());
        hotel.setLongitude(hotelRequestDto.getLongitude());

        Hotel updatedHotel = hotelRepository.save(hotel);

        if (hotelRequestDto.getMedia() != null) {
            try {
                imageService.updateHotelImagesWithMediaResponse(id, hotelRequestDto.getMedia());
                log.info("Updated {} media items for hotel ID: {}", hotelRequestDto.getMedia().size(), id);
            } catch (Exception e) {
                log.error("Error updating media for hotel {}: {}", id, e.getMessage());
            }
        }

        Map<String, Object> response = convertHotelToResponse(updatedHotel);
        response.put("message", "Hotel updated successfully");
        return response;
    }

    public void deleteHotel(Long id) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Hotel not found with ID: " + id));
        hotel.setIsActive(false);
        hotelRepository.save(hotel);
    }

    public Map<String, Object> updateHotelAmenities(Long hotelId, List<Long> amenityIds) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new EntityNotFoundException("Hotel not found with ID: " + hotelId));

        if (amenityIds == null) {
            amenityIds = Collections.emptyList();
        }

        List<Amenity> amenities = amenityIds.isEmpty()
                ? Collections.emptyList()
                : amenityRepository.findActiveAmenitiesByIds(amenityIds);

        Set<Long> validIds = amenities.stream()
                .map(Amenity::getAmenityId)
                .collect(Collectors.toSet());

        hotelAmenityRepository.deleteByHotelId(hotelId);
        if (!validIds.isEmpty()) {
            List<HotelAmenity> mappings = validIds.stream()
                    .map(aid -> new HotelAmenity(hotelId, aid, hotel, null))
                    .collect(Collectors.toList());
            hotelAmenityRepository.saveAll(mappings);
        }

        Map<String, Object> response = convertHotelToResponse(hotel);
        response.put("message", "Hotel amenities updated successfully");
        return response;
    }

    private Map<String, Object> convertHotelToResponse(Hotel hotel) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", hotel.getHotelId());
        response.put("name", hotel.getName() != null ? hotel.getName() : "");
        response.put("address", hotel.getAddress() != null ? hotel.getAddress() : "");
        response.put("city", hotel.getCity() != null ? hotel.getCity() : "");
        response.put("country", hotel.getCountry() != null ? hotel.getCountry() : "");
        response.put("starRating", hotel.getStarRating() != null ? hotel.getStarRating().doubleValue() : 0.0);
        response.put("description", hotel.getDescription() != null ? hotel.getDescription() : "");
        response.put("isActive", hotel.getIsActive() != null ? hotel.getIsActive() : Boolean.TRUE);
        response.put("status", hotel.getIsActive() != null && hotel.getIsActive() ? "ACTIVE" : "INACTIVE");
        response.put("latitude", hotel.getLatitude());
        response.put("longitude", hotel.getLongitude());

        List<RoomType> roomTypes = roomTypeRepository.findByHotelId(hotel.getHotelId());
        response.put("availableRooms", calculateAvailableRooms(roomTypes));
        response.put("minPrice", determineMinPrice(roomTypes));
        response.put("maxPrice", determineMaxPrice(roomTypes));
        response.put("roomTypeCount", roomTypes.size());

        List<Map<String, Object>> roomTypeSummaries = roomTypes.stream()
                .map(this::convertRoomTypeSummary)
                .collect(Collectors.toList());
        response.put("roomTypes", roomTypeSummaries);

        List<Amenity> amenities = hotelAmenityRepository.findAmenitiesByHotelId(hotel.getHotelId());
        if (amenities != null && !amenities.isEmpty()) {
            List<Map<String, Object>> amenityList = amenities.stream()
                    .map(this::convertAmenityToResponse)
                    .collect(Collectors.toList());
            response.put("amenities", amenityList);
        } else {
            response.put("amenities", Collections.emptyList());
        }

        List<MediaResponse> mediaResponses = imageService.getHotelMedia(hotel.getHotelId());
        response.put("media", mediaResponses);

        List<String> imageUrls = mediaResponses.stream()
                .sorted((a, b) -> {
                    if (Boolean.TRUE.equals(a.getIsPrimary()) && !Boolean.TRUE.equals(b.getIsPrimary())) {
                        return -1;
                    }
                    if (!Boolean.TRUE.equals(a.getIsPrimary()) && Boolean.TRUE.equals(b.getIsPrimary())) {
                        return 1;
                    }
                    return Integer.compare(
                            a.getDisplayOrder() != null ? a.getDisplayOrder() : 0,
                            b.getDisplayOrder() != null ? b.getDisplayOrder() : 0);
                })
                .map(media -> media.getSecureUrl() != null ? media.getSecureUrl() : media.getUrl())
                .filter(Objects::nonNull)
                .filter(url -> !url.isEmpty())
                .collect(Collectors.toList());

        response.put("images", imageUrls);
        response.put("primaryImage", imageUrls.isEmpty() ? null : imageUrls.get(0));

        return response;
    }

    private int calculateAvailableRooms(List<RoomType> roomTypes) {
        if (roomTypes == null || roomTypes.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        return roomTypes.stream()
                .map(RoomType::getRoomTypeId)
                .filter(Objects::nonNull)
                .map(roomTypeId -> roomAvailabilityRepository.findByRoomTypeIdAndDate(roomTypeId, today))
                .map(optional -> optional.orElse(null))
                .mapToInt(this::remainingInventory)
                .sum();
    }

    private double determineMinPrice(List<RoomType> roomTypes) {
        return roomTypes.stream()
                .map(RoomType::getBasePrice)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .min()
                .orElse(0.0);
    }

    private Double determineMaxPrice(List<RoomType> roomTypes) {
        return roomTypes.stream()
                .map(RoomType::getBasePrice)
                .filter(Objects::nonNull)
                .map(BigDecimal::doubleValue)
                .max(Double::compare)
                .orElse(null);
    }

    private int remainingInventory(RoomAvailability availability) {
        if (availability == null) {
            return 0;
        }
        int totalInventory = Optional.ofNullable(availability.getTotalInventory()).orElse(0);
        int totalReserved = Optional.ofNullable(availability.getTotalReserved()).orElse(0);
        return Math.max(totalInventory - totalReserved, 0);
    }

    private Map<String, Object> convertRoomTypeSummary(RoomType roomType) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", roomType.getRoomTypeId());
        summary.put("name", roomType.getName());
        summary.put("description", roomType.getDescription());
        summary.put("capacityAdults", roomType.getCapacityAdults());
        summary.put("basePrice", roomType.getBasePrice() != null ? roomType.getBasePrice().doubleValue() : null);
        return summary;
    }

    private Map<String, Object> convertAmenityToResponse(Amenity amenity) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", amenity.getAmenityId());
        response.put("name", amenity.getName());
        response.put("iconUrl", amenity.getIconUrl());
        response.put("isActive", amenity.getIsActive() != null ? amenity.getIsActive() : Boolean.TRUE);
        response.put("displayOrder", amenity.getDisplayOrder());
        return response;
    }

    /**
     * Get all hotel IDs for RAG initialization
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAllHotelIds(int page, int size) {
        log.info("Fetching hotel IDs for RAG initialization: page={}, size={}", page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "hotelId"));

        Page<Long> hotelIdPage = hotelRepository.findAllHotelIds(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", hotelIdPage.getContent());
        response.put("totalElements", hotelIdPage.getTotalElements());
        response.put("totalPages", hotelIdPage.getTotalPages());
        response.put("size", hotelIdPage.getSize());
        response.put("number", hotelIdPage.getNumber());
        response.put("first", hotelIdPage.isFirst());
        response.put("last", hotelIdPage.isLast());
        response.put("empty", hotelIdPage.isEmpty());

        log.info("Found {} hotel IDs for RAG initialization", hotelIdPage.getContent().size());
        return response;
    }

    /**
     * Get hotel statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getHotelStatistics() {
        long totalHotels = hotelRepository.count();
        long activeHotels = hotelRepository.countByIsActive(true);
        long inactiveHotels = hotelRepository.countByIsActive(false);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalHotels", totalHotels);
        stats.put("activeHotels", activeHotels);
        stats.put("inactiveHotels", inactiveHotels);
        stats.put("totalRoomTypes", roomTypeRepository.count());
        stats.put("totalAmenities", amenityRepository.count());

        return stats;
    }
}
