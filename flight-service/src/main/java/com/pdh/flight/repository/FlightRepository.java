package com.pdh.flight.repository;

import com.pdh.flight.model.Flight;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long>, JpaSpecificationExecutor<Flight> {

    /**
     * Find flights by route (origin and destination airports)
     */
    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport da
            JOIN FETCH f.arrivalAirport aa
            JOIN FlightSchedule fs ON fs.flightId = f.flightId
            WHERE da.iataCode = :origin
            AND aa.iataCode = :destination
            AND DATE(fs.departureTime) = :departureDate
            ORDER BY f.flightNumber
            """)
    Page<Flight> findFlightsByRoute(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureDate") LocalDate departureDate,
            Pageable pageable);

    /**
     * Find flights by route with filters for airlines and airports
     */
    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport da
            JOIN FETCH f.arrivalAirport aa
            JOIN FlightSchedule fs ON fs.flightId = f.flightId
            WHERE (:origin IS NULL OR da.iataCode = :origin)
            AND (:destination IS NULL OR aa.iataCode = :destination)
            AND (:departureDate IS NULL OR DATE(fs.departureTime) = :departureDate)
            AND (:airlineId IS NULL OR f.airline.airlineId = :airlineId)
            ORDER BY f.flightNumber
            """)
    Page<Flight> findFlightsByRouteWithFilters(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureDate") LocalDate departureDate,
            @Param("airlineId") Long airlineId,
            Pageable pageable);

    /**
     * Find flights by route sorted by departure time
     */
    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport da
            JOIN FETCH f.arrivalAirport aa
            JOIN FlightSchedule fs ON fs.flightId = f.flightId
            WHERE da.iataCode = :origin
            AND aa.iataCode = :destination
            AND DATE(fs.departureTime) = :departureDate
            ORDER BY fs.departureTime
            """)
    Page<Flight> findFlightsByRouteOrderByDepartureTime(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureDate") LocalDate departureDate,
            Pageable pageable);

    /**
     * Find flights by flight number
     */
    @Query("""
            SELECT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport
            JOIN FETCH f.arrivalAirport
            WHERE f.flightNumber = :flightNumber
            """)
    Optional<Flight> findByFlightNumberWithDetails(@Param("flightNumber") String flightNumber);

    /**
     * Find all flights with details (for admin/backoffice)
     */
    @Query("""
            SELECT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport
            JOIN FETCH f.arrivalAirport
            WHERE f.isDeleted = false
            ORDER BY f.flightNumber
            """)
    Page<Flight> findAllWithDetails(Pageable pageable);

    /**
     * Count flights by status
     */
    @Query("SELECT COUNT(f) FROM Flight f WHERE f.status = :status AND f.isDeleted = false")
    long countByStatus(@Param("status") String status);

    /**
     * Find flights by airline
     */
    @Query("""
            SELECT f FROM Flight f
            JOIN FETCH f.airline a
            JOIN FETCH f.departureAirport
            JOIN FETCH f.arrivalAirport
            WHERE a.airlineId = :airlineId AND f.isDeleted = false
            """)
    Page<Flight> findByAirlineId(@Param("airlineId") Long airlineId, Pageable pageable);

    /**
     * Find flights by departure airport
     */
    @Query("""
            SELECT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport da
            JOIN FETCH f.arrivalAirport
            WHERE da.airportId = :airportId AND f.isDeleted = false
            """)
    Page<Flight> findByDepartureAirportId(@Param("airportId") Long airportId, Pageable pageable);

    /**
     * Find flights by arrival airport
     */
    @Query("""
            SELECT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport
            JOIN FETCH f.arrivalAirport aa
            WHERE aa.airportId = :airportId AND f.isDeleted = false
            """)
    Page<Flight> findByArrivalAirportId(@Param("airportId") Long airportId, Pageable pageable);

    /**
     * Count flights by airline ID
     */
    @Query("SELECT COUNT(f) FROM Flight f WHERE f.airline.airlineId = :airlineId AND f.isDeleted = false")
    Long countByAirlineId(@Param("airlineId") Long airlineId);

    /**
     * Count flights by airline ID and status
     */
    @Query("SELECT COUNT(f) FROM Flight f WHERE f.airline.airlineId = :airlineId AND f.status = :status AND f.isDeleted = false")
    Long countByAirlineIdAndStatus(@Param("airlineId") Long airlineId, @Param("status") String status);

    /**
     * Count flights by departure airport ID
     */
    @Query("SELECT COUNT(f) FROM Flight f WHERE f.departureAirport.airportId = :airportId AND f.isDeleted = false")
    Long countByDepartureAirportId(@Param("airportId") Long airportId);

    /**
     * Count flights by arrival airport ID
     */
    @Query("SELECT COUNT(f) FROM Flight f WHERE f.arrivalAirport.airportId = :airportId AND f.isDeleted = false")
    Long countByArrivalAirportId(@Param("airportId") Long airportId);

    /**
     * Count flights by departure airport ID and status
     */
    @Query("SELECT COUNT(f) FROM Flight f WHERE f.departureAirport.airportId = :airportId AND f.status = :status AND f.isDeleted = false")
    Long countByDepartureAirportIdAndStatus(@Param("airportId") Long airportId, @Param("status") String status);

    /**
     * Count flights by arrival airport ID and status
     */
    @Query("SELECT COUNT(f) FROM Flight f WHERE f.arrivalAirport.airportId = :airportId AND f.status = :status AND f.isDeleted = false")
    Long countByArrivalAirportIdAndStatus(@Param("airportId") Long airportId, @Param("status") String status);

    /**
     * Find flights by flexible route search (supports city names and IATA codes)
     */
    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport da
            JOIN FETCH f.arrivalAirport aa
            JOIN FlightSchedule fs ON fs.flightId = f.flightId
            WHERE (:origin IS NULL OR da.iataCode = :origin OR LOWER(da.city) LIKE LOWER(CONCAT('%', :origin, '%')))
            AND (:destination IS NULL OR aa.iataCode = :destination OR LOWER(aa.city) LIKE LOWER(CONCAT('%', :destination, '%')))
            AND (:departureDate IS NULL OR DATE(fs.departureTime) = :departureDate)
            AND (:airlineId IS NULL OR f.airline.airlineId = :airlineId)
            AND f.isDeleted = false
            ORDER BY f.flightNumber
            """)
    Page<Flight> findFlightsByFlexibleRoute(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureDate") LocalDate departureDate,
            @Param("airlineId") Long airlineId,
            Pageable pageable);

    /**
     * Find flights by flexible route search sorted by departure time
     */
    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport da
            JOIN FETCH f.arrivalAirport aa
            JOIN FlightSchedule fs ON fs.flightId = f.flightId
            WHERE (:origin IS NULL OR da.iataCode = :origin OR LOWER(da.city) LIKE LOWER(CONCAT('%', :origin, '%')))
            AND (:destination IS NULL OR aa.iataCode = :destination OR LOWER(aa.city) LIKE LOWER(CONCAT('%', :destination, '%')))
            AND (:departureDate IS NULL OR DATE(fs.departureTime) = :departureDate)
            AND (:airlineId IS NULL OR f.airline.airlineId = :airlineId)
            AND f.isDeleted = false
            ORDER BY fs.departureTime
            """)
    Page<Flight> findFlightsByFlexibleRouteOrderByDepartureTime(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureDate") LocalDate departureDate,
            @Param("airlineId") Long airlineId,
            Pageable pageable);

    /**
     * Find flights by flexible route search sorted by price (requires pricing
     * service integration)
     */
    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport da
            JOIN FETCH f.arrivalAirport aa
            JOIN FlightSchedule fs ON fs.flightId = f.flightId
            WHERE (:origin IS NULL OR da.iataCode = :origin OR LOWER(da.city) LIKE LOWER(CONCAT('%', :origin, '%')))
            AND (:destination IS NULL OR aa.iataCode = :destination OR LOWER(aa.city) LIKE LOWER(CONCAT('%', :destination, '%')))
            AND (:departureDate IS NULL OR DATE(fs.departureTime) = :departureDate)
            AND (:airlineId IS NULL OR f.airline.airlineId = :airlineId)
            AND f.isDeleted = false
            ORDER BY f.flightNumber
            """)
    Page<Flight> findFlightsByFlexibleRouteOrderByPrice(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureDate") LocalDate departureDate,
            @Param("airlineId") Long airlineId,
            Pageable pageable);

    /**
     * Find flights by flexible route search sorted by duration
     */
    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport da
            JOIN FETCH f.arrivalAirport aa
            JOIN FlightSchedule fs ON fs.flightId = f.flightId
            WHERE (:origin IS NULL OR da.iataCode = :origin OR LOWER(da.city) LIKE LOWER(CONCAT('%', :origin, '%')))
            AND (:destination IS NULL OR aa.iataCode = :destination OR LOWER(aa.city) LIKE LOWER(CONCAT('%', :destination, '%')))
            AND (:departureDate IS NULL OR DATE(fs.departureTime) = :departureDate)
            AND (:airlineId IS NULL OR f.airline.airlineId = :airlineId)
            AND f.isDeleted = false
            ORDER BY f.baseDurationMinutes
            """)
    Page<Flight> findFlightsByFlexibleRouteOrderByDuration(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureDate") LocalDate departureDate,
            @Param("airlineId") Long airlineId,
            Pageable pageable);

    /**
     * Find flights by origin only (for destination suggestions)
     */
    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport da
            JOIN FETCH f.arrivalAirport aa
            JOIN FlightSchedule fs ON fs.flightId = f.flightId
            WHERE (:origin IS NULL OR da.iataCode = :origin OR LOWER(da.city) LIKE LOWER(CONCAT('%', :origin, '%')))
            AND (:departureDate IS NULL OR DATE(fs.departureTime) = :departureDate)
            AND f.isDeleted = false
            ORDER BY aa.city, aa.iataCode
            """)
    Page<Flight> findFlightsByOrigin(
            @Param("origin") String origin,
            @Param("departureDate") LocalDate departureDate,
            Pageable pageable);

    /**
     * Find flights by destination only (for origin suggestions)
     */
    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport da
            JOIN FETCH f.arrivalAirport aa
            JOIN FlightSchedule fs ON fs.flightId = f.flightId
            WHERE (:destination IS NULL OR aa.iataCode = :destination OR LOWER(aa.city) LIKE LOWER(CONCAT('%', :destination, '%')))
            AND (:departureDate IS NULL OR DATE(fs.departureTime) = :departureDate)
            AND f.isDeleted = false
            ORDER BY da.city, da.iataCode
            """)
    Page<Flight> findFlightsByDestination(
            @Param("destination") String destination,
            @Param("departureDate") LocalDate departureDate,
            Pageable pageable);

    /**
     * Get all flight IDs for RAG initialization
     * 
     * @param pageable pagination information
     * @return Page of flight IDs
     */
    @Query("SELECT f.flightId FROM Flight f WHERE f.isDeleted = false ORDER BY f.flightId")
    Page<Long> findAllFlightIds(Pageable pageable);

    // Methods for filtering schedules by airports

    /**
     * Find flights by departure and arrival airports (for schedule filtering)
     */
    @Query("""
            SELECT f FROM Flight f
            WHERE f.departureAirport.airportId = :departureAirportId
            AND f.arrivalAirport.airportId = :arrivalAirportId
            AND f.isDeleted = false
            """)
    List<Flight> findByDepartureAirportIdAndArrivalAirportIdAndIsDeletedFalse(
            @Param("departureAirportId") Long departureAirportId,
            @Param("arrivalAirportId") Long arrivalAirportId);

    /**
     * Find flights by departure airport only (for schedule filtering)
     */
    @Query("""
            SELECT f FROM Flight f
            WHERE f.departureAirport.airportId = :airportId
            AND f.isDeleted = false
            """)
    List<Flight> findByDepartureAirportIdAndIsDeletedFalse(@Param("airportId") Long airportId);

    /**
     * Find flights by arrival airport only (for schedule filtering)
     */
    @Query("""
            SELECT f FROM Flight f
            WHERE f.arrivalAirport.airportId = :airportId
            AND f.isDeleted = false
            """)
    List<Flight> findByArrivalAirportIdAndIsDeletedFalse(@Param("airportId") Long airportId);

    /**
     * Find flights by flight number containing search term (for autocomplete/search)
     */
    @Query("""
            SELECT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.departureAirport
            JOIN FETCH f.arrivalAirport
            WHERE LOWER(f.flightNumber) LIKE LOWER(CONCAT('%', :flightNumber, '%'))
            AND f.isDeleted = false
            ORDER BY f.flightNumber
            """)
    List<Flight> findByFlightNumberContainingIgnoreCaseAndIsDeletedFalse(@Param("flightNumber") String flightNumber);
}
