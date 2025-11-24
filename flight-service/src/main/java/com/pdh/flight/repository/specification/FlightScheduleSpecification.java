package com.pdh.flight.repository.specification;

import com.pdh.flight.model.Airport;
import com.pdh.flight.model.Flight;
import com.pdh.flight.model.FlightSchedule;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specifications for FlightSchedule entity
 * Supports flexible search by flight number, airports, and status
 */
public class FlightScheduleSpecification {

    /**
     * Flexible search across flight number and airports (city, country, IATA code)
     * Searches in flight number, departure airport, and arrival airport
     */
    public static Specification<FlightSchedule> searchByTerm(String search) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(search)) {
                return criteriaBuilder.conjunction();
            }

            Join<FlightSchedule, Flight> flight = root.join("flight", JoinType.INNER);
            Join<Flight, Airport> departureAirport = flight.join("departureAirport", JoinType.INNER);
            Join<Flight, Airport> arrivalAirport = flight.join("arrivalAirport", JoinType.INNER);
            
            String searchPattern = "%" + search.toLowerCase() + "%";
            
            // Search in flight number
            Predicate flightNumberPredicate = criteriaBuilder.like(
                criteriaBuilder.lower(flight.get("flightNumber")), 
                searchPattern
            );
            
            // Search in departure airport
            Predicate departurePredicate = criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(departureAirport.get("city")), searchPattern),
                criteriaBuilder.like(criteriaBuilder.lower(departureAirport.get("country")), searchPattern),
                criteriaBuilder.like(criteriaBuilder.lower(departureAirport.get("iataCode")), searchPattern),
                criteriaBuilder.like(criteriaBuilder.lower(departureAirport.get("name")), searchPattern)
            );
            
            // Search in arrival airport
            Predicate arrivalPredicate = criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(arrivalAirport.get("city")), searchPattern),
                criteriaBuilder.like(criteriaBuilder.lower(arrivalAirport.get("country")), searchPattern),
                criteriaBuilder.like(criteriaBuilder.lower(arrivalAirport.get("iataCode")), searchPattern),
                criteriaBuilder.like(criteriaBuilder.lower(arrivalAirport.get("name")), searchPattern)
            );
            
            return criteriaBuilder.or(flightNumberPredicate, departurePredicate, arrivalPredicate);
        };
    }

    /**
     * Filter by departure date (converted to ZonedDateTime range for database comparison)
     */
    public static Specification<FlightSchedule> hasDepartureDate(LocalDate date) {
        return (root, query, criteriaBuilder) -> {
            if (date == null) {
                return criteriaBuilder.conjunction();
            }
            
            // Convert LocalDate to ZonedDateTime range (start and end of day in UTC)
            ZonedDateTime startOfDay = date.atStartOfDay(ZoneId.of("UTC"));
            ZonedDateTime endOfDay = date.atTime(LocalTime.MAX).atZone(ZoneId.of("UTC"));
            
            return criteriaBuilder.and(
                criteriaBuilder.greaterThanOrEqualTo(root.get("departureTime"), startOfDay),
                criteriaBuilder.lessThanOrEqualTo(root.get("departureTime"), endOfDay)
            );
        };
    }

    /**
     * Filter by status
     */
    public static Specification<FlightSchedule> hasStatus(String status) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(status)) {
                return criteriaBuilder.conjunction();
            }
            
            return criteriaBuilder.equal(criteriaBuilder.lower(root.get("status")), status.toLowerCase());
        };
    }

    /**
     * Filter active (non-deleted) schedules
     */
    public static Specification<FlightSchedule> isNotDeleted() {
        return (root, query, criteriaBuilder) -> 
            criteriaBuilder.equal(root.get("isDeleted"), false);
    }

    /**
     * Filter by flight ID
     */
    public static Specification<FlightSchedule> hasFlightId(String flightId) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(flightId)) {
                return criteriaBuilder.conjunction();
            }
            
            return criteriaBuilder.equal(root.get("flightId"), flightId);
        };
    }

    /**
     * Filter by departure airport ID
     */
    public static Specification<FlightSchedule> hasDepartureAirportId(Integer airportId) {
        return (root, query, criteriaBuilder) -> {
            if (airportId == null) {
                return criteriaBuilder.conjunction();
            }
            
            Join<FlightSchedule, Flight> flight = root.join("flight", JoinType.INNER);
            Join<Flight, Airport> departureAirport = flight.join("departureAirport", JoinType.INNER);
            
            return criteriaBuilder.equal(departureAirport.get("airportId"), airportId);
        };
    }

    /**
     * Filter by arrival airport ID
     */
    public static Specification<FlightSchedule> hasArrivalAirportId(Integer airportId) {
        return (root, query, criteriaBuilder) -> {
            if (airportId == null) {
                return criteriaBuilder.conjunction();
            }
            
            Join<FlightSchedule, Flight> flight = root.join("flight", JoinType.INNER);
            Join<Flight, Airport> arrivalAirport = flight.join("arrivalAirport", JoinType.INNER);
            
            return criteriaBuilder.equal(arrivalAirport.get("airportId"), airportId);
        };
    }

    /**
     * Combine multiple specifications with AND logic
     */
    public static Specification<FlightSchedule> combine(Specification<FlightSchedule>... specifications) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            for (Specification<FlightSchedule> spec : specifications) {
                if (spec != null) {
                    Predicate predicate = spec.toPredicate(root, query, criteriaBuilder);
                    if (predicate != null) {
                        predicates.add(predicate);
                    }
                }
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
