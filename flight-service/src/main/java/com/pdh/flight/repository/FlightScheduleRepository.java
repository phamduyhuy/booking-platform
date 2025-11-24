package com.pdh.flight.repository;

import com.pdh.flight.model.FlightSchedule;
import com.pdh.flight.model.enums.ScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for FlightSchedule entity
 */
@Repository
public interface FlightScheduleRepository extends JpaRepository<FlightSchedule, UUID>, JpaSpecificationExecutor<FlightSchedule> {

    /**
     * Find flight schedules by flight ID
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId = :flightId AND fs.isDeleted = false ORDER BY fs.departureTime")
    List<FlightSchedule> findByFlightId(@Param("flightId") Long flightId);

    /**
     * Find flight schedules by flight ID and departure date
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId = :flightId AND DATE(fs.departureTime) = :date AND fs.isDeleted = false ORDER BY fs.departureTime")
    List<FlightSchedule> findByFlightIdAndDate(@Param("flightId") Long flightId, @Param("date") LocalDate date);

    /**
     * Find active flight schedules by flight ID
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId = :flightId AND fs.status IN ('ACTIVE', 'SCHEDULED') AND fs.isDeleted = false ORDER BY fs.departureTime")
    List<FlightSchedule> findActiveByFlightId(@Param("flightId") Long flightId);

    /**
     * Find flight schedules by multiple flight IDs
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flightId IN :flightIds AND fs.isDeleted = false ORDER BY fs.flightId, fs.departureTime")
    List<FlightSchedule> findByFlightIdIn(@Param("flightIds") List<Long> flightIds);

    /**
     * Find flight schedules by flight IDs and departure time range
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId IN :flightIds AND fs.departureTime BETWEEN :startTime AND :endTime AND fs.isDeleted = false ORDER BY fs.flightId, fs.departureTime")
    List<FlightSchedule> findByFlightIdInAndDepartureTimeBetween(
            @Param("flightIds") List<Long> flightIds,
            @Param("startTime") ZonedDateTime startTime,
            @Param("endTime") ZonedDateTime endTime);

    /**
     * Find flight schedules by departure time range
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.departureTime BETWEEN :startTime AND :endTime AND fs.isDeleted = false ORDER BY fs.departureTime")
    List<FlightSchedule> findByDepartureTimeBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Find flight schedules by arrival time range
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.arrivalTime BETWEEN :startTime AND :endTime AND fs.isDeleted = false ORDER BY fs.arrivalTime")
    List<FlightSchedule> findByArrivalTimeBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Find flight schedules by status
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.status = :status AND fs.isDeleted = false ORDER BY fs.departureTime")
    List<FlightSchedule> findByStatus(@Param("status") ScheduleStatus status);

    /**
     * Find flight schedules by flight ID and departure time range
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId = :flightId AND fs.departureTime BETWEEN :startTime AND :endTime AND fs.isDeleted = false ORDER BY fs.departureTime")
    List<FlightSchedule> findByFlightIdAndDepartureTimeBetween(
            @Param("flightId") Long flightId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // Methods for BackofficeFlightScheduleService

    /**
     * Find flight schedules by flight ID, status, date and not deleted
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId = :flightId AND fs.status = :status AND DATE(fs.departureTime) = :date AND fs.isDeleted = false ORDER BY fs.departureTime")
    Page<FlightSchedule> findByFlightIdAndStatusAndDateAndIsDeletedFalse(
            @Param("flightId") Long flightId,
            @Param("status") ScheduleStatus status,
            @Param("date") LocalDate date,
            Pageable pageable);

    /**
     * Find flight schedules by flight ID, date and not deleted
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId = :flightId AND DATE(fs.departureTime) = :date AND fs.isDeleted = false ORDER BY fs.departureTime")
    Page<FlightSchedule> findByFlightIdAndDateAndIsDeletedFalse(
            @Param("flightId") Long flightId,
            @Param("date") LocalDate date,
            Pageable pageable);

    /**
     * Find flight schedules by flight ID, status and not deleted
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId = :flightId AND fs.status = :status AND fs.isDeleted = false ORDER BY fs.departureTime")
    Page<FlightSchedule> findByFlightIdAndStatusAndIsDeletedFalse(
            @Param("flightId") Long flightId,
            @Param("status") ScheduleStatus status,
            Pageable pageable);

    /**
     * Find flight schedules by flight ID and not deleted
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId = :flightId AND fs.isDeleted = false ORDER BY fs.departureTime")
    Page<FlightSchedule> findByFlightIdAndIsDeletedFalse(@Param("flightId") Long flightId, Pageable pageable);

    /**
     * Find flight schedules by status and not deleted
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.status = :status AND fs.isDeleted = false ORDER BY fs.departureTime")
    Page<FlightSchedule> findByStatusAndIsDeletedFalse(@Param("status") ScheduleStatus status, Pageable pageable);

    /**
     * Find flight schedules by date and not deleted
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE DATE(fs.departureTime) = :date AND fs.isDeleted = false ORDER BY fs.departureTime")
    Page<FlightSchedule> findByDateAndIsDeletedFalse(@Param("date") LocalDate date, Pageable pageable);

    /**
     * Find flight schedules that are not deleted
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.isDeleted = false ORDER BY fs.departureTime")
    Page<FlightSchedule> findByIsDeletedFalse(Pageable pageable);

    /**
     * Count flight schedules by flight ID
     */
    @Query("SELECT COUNT(fs) FROM FlightSchedule fs WHERE fs.flight.flightId = :flightId AND fs.isDeleted = false")
    Long countByFlightId(@Param("flightId") Long flightId);

    /**
     * Count flight schedules by flight ID and status
     */
    @Query("SELECT COUNT(fs) FROM FlightSchedule fs WHERE fs.flight.flightId = :flightId AND fs.status = :status AND fs.isDeleted = false")
    Long countByFlightIdAndStatus(@Param("flightId") Long flightId, @Param("status") ScheduleStatus status);

    /**
     * Count flight schedules that are not deleted
     */
    @Query("SELECT COUNT(fs) FROM FlightSchedule fs WHERE fs.isDeleted = false")
    Long countByIsDeletedFalse();

    /**
     * Count flight schedules by status and not deleted
     */
    @Query("SELECT COUNT(fs) FROM FlightSchedule fs WHERE fs.status = :status AND fs.isDeleted = false")
    Long countByStatusAndIsDeletedFalse(@Param("status") ScheduleStatus status);

    /**
     * Find overlapping schedules for aircraft validation
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.aircraftId = :aircraftId AND ((fs.departureTime < :arrivalTime AND fs.arrivalTime > :departureTime)) AND fs.isDeleted = false AND (:excludeScheduleId IS NULL OR fs.scheduleId != :excludeScheduleId)")
    List<FlightSchedule> findOverlappingSchedules(
            @Param("aircraftId") Long aircraftId,
            @Param("departureTime") ZonedDateTime departureTime,
            @Param("arrivalTime") ZonedDateTime arrivalTime,
            @Param("excludeScheduleId") UUID excludeScheduleId);

    // =========================
    // STATUS MANAGEMENT METHODS
    // =========================

    /**
     * Update schedules that have completed (arrival time has passed)
     * Used by scheduled task to automatically update completed flights
     */
    @Modifying
    @Query("""
            UPDATE FlightSchedule fs
            SET fs.status = 'COMPLETED', fs.updatedAt = :currentTime, fs.updatedBy = 'system-scheduler'
            WHERE fs.arrivalTime < :currentTime
            AND fs.status IN ('SCHEDULED', 'ACTIVE', 'DELAYED')
            AND fs.isDeleted = false
            """)
    int updateCompletedSchedules(@Param("currentTime") ZonedDateTime currentTime);

    /**
     * Update schedules to ACTIVE when departure is within specified window
     * Used by scheduled task to mark flights as active when departure is near
     */
    @Modifying
    @Query("""
            UPDATE FlightSchedule fs
            SET fs.status = 'ACTIVE', fs.updatedAt = :currentTime, fs.updatedBy = 'system-scheduler'
            WHERE fs.departureTime BETWEEN :currentTime AND :futureTime
            AND fs.status = 'SCHEDULED'
            AND fs.isDeleted = false
            """)
    int updateActiveSchedules(@Param("currentTime") ZonedDateTime currentTime,
            @Param("futureTime") ZonedDateTime futureTime);

    /**
     * Generic method to update schedule status based on conditions
     * Used for manual status updates or specific business logic
     */
    @Modifying
    @Query("""
            UPDATE FlightSchedule fs
            SET fs.status = :toStatus, fs.updatedAt = :currentTime, fs.updatedBy = 'system-manual'
            WHERE fs.status = :fromStatus
            AND fs.arrivalTime < :timeCondition
            AND fs.isDeleted = false
            """)
    int updateScheduleStatus(@Param("fromStatus") ScheduleStatus fromStatus,
            @Param("toStatus") ScheduleStatus toStatus,
            @Param("timeCondition") ZonedDateTime timeCondition,
            @Param("currentTime") ZonedDateTime currentTime);

    /**
     * Soft delete old completed schedules to cleanup database
     * Used by scheduled cleanup task
     */
    @Modifying
    @Query("""
            UPDATE FlightSchedule fs
            SET fs.isDeleted = true, fs.deletedAt = :currentTime, fs.deletedBy = 'system-cleanup'
            WHERE fs.status = 'COMPLETED'
            AND fs.arrivalTime < :cutoffDate
            AND fs.isDeleted = false
            """)
    int softDeleteOldCompletedSchedules(@Param("cutoffDate") ZonedDateTime cutoffDate,
            @Param("currentTime") ZonedDateTime currentTime);

    /**
     * Find future schedules for fare creation (only SCHEDULED and ACTIVE)
     * Replaces the need for string-based status filtering
     */
    @Query("""
            SELECT fs FROM FlightSchedule fs
            LEFT JOIN FETCH fs.flight f
            LEFT JOIN FETCH f.airline
            LEFT JOIN FETCH f.departureAirport
            LEFT JOIN FETCH f.arrivalAirport
            LEFT JOIN FETCH fs.aircraft
            WHERE fs.departureTime > :currentTime
            AND fs.status IN ('SCHEDULED', 'ACTIVE')
            AND fs.isDeleted = false
            ORDER BY fs.departureTime ASC
            """)
    List<FlightSchedule> findFutureSchedulesForFareCreation(@Param("currentTime") ZonedDateTime currentTime);

    // Methods for filtering by multiple flight IDs (for airport filtering)

    /**
     * Find schedules by multiple flight IDs, status, and date
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId IN :flightIds AND fs.status = :status AND DATE(fs.departureTime) = :date AND fs.isDeleted = false ORDER BY fs.departureTime")
    Page<FlightSchedule> findByFlightIdInAndStatusAndDateAndIsDeletedFalse(
            @Param("flightIds") List<Long> flightIds,
            @Param("status") ScheduleStatus status,
            @Param("date") LocalDate date,
            Pageable pageable);

    /**
     * Find schedules by multiple flight IDs and date
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId IN :flightIds AND DATE(fs.departureTime) = :date AND fs.isDeleted = false ORDER BY fs.departureTime")
    Page<FlightSchedule> findByFlightIdInAndDateAndIsDeletedFalse(
            @Param("flightIds") List<Long> flightIds,
            @Param("date") LocalDate date,
            Pageable pageable);

    /**
     * Find schedules by multiple flight IDs and status
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId IN :flightIds AND fs.status = :status AND fs.isDeleted = false ORDER BY fs.departureTime")
    Page<FlightSchedule> findByFlightIdInAndStatusAndIsDeletedFalse(
            @Param("flightIds") List<Long> flightIds,
            @Param("status") ScheduleStatus status,
            Pageable pageable);

    /**
     * Find schedules by multiple flight IDs
     */
    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.flightId IN :flightIds AND fs.isDeleted = false ORDER BY fs.departureTime")
    Page<FlightSchedule> findByFlightIdInAndIsDeletedFalse(
            @Param("flightIds") List<Long> flightIds,
            Pageable pageable);
}